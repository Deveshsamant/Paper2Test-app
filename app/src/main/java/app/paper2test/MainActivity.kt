package app.paper2test

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import app.paper2test.ui.P2TTheme
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.paper2test.screens.*
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Where the user is. The app is a native shell; heavy screens open the site in a WebView with the session handed over. */
sealed class Screen {
    data object Login : Screen()
    data object Home : Screen()
    data object Scan : Screen()
    data object Bundles : Screen()
    data class Exam(val code: String) : Screen()
    data class Web(val path: String, val title: String) : Screen()
}

class MainActivity : ComponentActivity() {
    private val pendingCode = mutableStateOf<String?>(null)
    private val pendingUrl = mutableStateOf<String?>(null) // tapped notification: page to open
    private val homeRefresh = mutableIntStateOf(0) // bumped when an institute link arrives while the app is open

    // App updates: what the server says (newest / minimum version) and Google Play's in-app update flow.
    private val update = mutableStateOf<UpdateInfo?>(null)
    private val updateDismissed = mutableStateOf(false)
    private val updateDownloaded = mutableStateOf(false) // a background (flexible) update is ready to install
    private lateinit var appUpdates: AppUpdateManager
    private val updateFlow = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    private val installListener = InstallStateUpdatedListener { if (it.installStatus() == InstallStatus.DOWNLOADED) updateDownloaded.value = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always light: dark status-bar icons even when the phone is in dark mode.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE))
        pendingCode.value = codeFromIntent(intent)
        instituteFromIntent(intent)
        Institutes.readInstallReferrer(this)
        pendingUrl.value = intent?.getStringExtra("url")
        appUpdates = AppUpdateManagerFactory.create(this)
        appUpdates.registerListener(installListener)
        lifecycleScope.launch { checkForUpdate() }
        setContent {
            val app = App.of(this)
            var stack by remember { mutableStateOf<List<Screen>>(listOf(if (app.session.signedIn) Screen.Home else Screen.Login)) }
            val nav = remember { object : Nav {
                override fun go(s: Screen) { stack = stack + s }
                override fun replace(s: Screen) { stack = listOf(s) }
                override fun back(): Boolean { if (stack.size <= 1) return false; stack = stack.dropLast(1); return true }
            } }
            // A shared link (or QR opened by the camera app) lands straight in the exam room.
            LaunchedEffect(pendingCode.value) { pendingCode.value?.let { nav.go(Screen.Exam(it)); pendingCode.value = null } }
            // A tapped notification opens its page (a test link goes to the exam room).
            LaunchedEffect(pendingUrl.value) {
                val u = pendingUrl.value ?: return@LaunchedEffect
                pendingUrl.value = null
                if (!app.session.signedIn || u == "/") return@LaunchedEffect
                val code = Regex("^/t/([A-Za-z0-9]{4,10})").find(u)?.groupValues?.get(1)
                nav.go(if (code != null) Screen.Exam(code.uppercase()) else Screen.Web(u, "Paper2Test"))
            }
            BackHandler(enabled = stack.size > 1) { nav.back() }

            P2TTheme {
                Surface {
                    Box(Modifier.fillMaxSize()) {
                        val current = stack.last()
                        when (val s = current) {
                            Screen.Login -> LoginScreen(nav)
                            Screen.Home -> key(homeRefresh.intValue) { HomeScreen(nav) }
                            Screen.Scan -> ScanScreen(nav)
                            Screen.Bundles -> BundlesScreen(nav)
                            is Screen.Exam -> WebScreen(nav, "/t/${s.code}", "Test ${s.code}", exam = true)
                            is Screen.Web -> WebScreen(nav, s.path, s.title)
                        }
                        // Never interrupt a test in progress; everywhere else show what the update check found.
                        val u = update.value
                        if (current !is Screen.Exam) {
                            if (u?.need == UpdateNeed.REQUIRED) UpdateRequired(u) { Updates.openPlay(this@MainActivity, u.playUrl) }
                            else Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp)) {
                                if (updateDownloaded.value) UpdateBanner("New version downloaded", "Restart", { appUpdates.completeUpdate() }, null)
                                else if (u?.need == UpdateNeed.OPTIONAL && !updateDismissed.value)
                                    UpdateBanner(u.message.ifBlank { "Version ${u.latestName} is available" }, "Update", { Updates.openPlay(this@MainActivity, u.playUrl) }, { updateDismissed.value = true })
                            }
                        }
                    }
                }
            }
        }
    }

    /** Ask the server which versions are current; when this one is behind and Google Play has the update, use Play's
     *  own in-app update screen (required = full screen, optional = downloads in the background). Installs that did not
     *  come from Google Play fall back to the banner / "Update required" page, which open the Play Store. */
    private suspend fun checkForUpdate() {
        val info = runCatching { Updates.check(App.of(this)) }.getOrNull() ?: return
        update.value = info
        if (info.need == UpdateNeed.NONE) return
        val play = runCatching { appUpdates.appUpdateInfo.await() }.getOrNull() ?: return
        if (play.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return
        val type = if (info.need == UpdateNeed.REQUIRED) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
        if (play.isUpdateTypeAllowed(type)) runCatching { appUpdates.startUpdateFlowForResult(play, updateFlow, AppUpdateOptions.newBuilder(type).build()) }
    }

    override fun onResume() {
        super.onResume()
        // Back from Play's update screen: continue an interrupted required update; notice a finished download.
        if (::appUpdates.isInitialized) appUpdates.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) updateDownloaded.value = true
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
                runCatching { appUpdates.startUpdateFlowForResult(info, updateFlow, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()) }
        }
    }

    override fun onDestroy() {
        if (::appUpdates.isInitialized) appUpdates.unregisterListener(installListener)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        codeFromIntent(intent)?.let { pendingCode.value = it }
        instituteFromIntent(intent)
        intent.getStringExtra("url")?.let { pendingUrl.value = it }
    }

    /** paper2test.app/i/<slug>: remember the institute; Home joins it once the person is signed in. */
    private fun instituteFromIntent(i: Intent?) {
        i?.data?.path?.let { Regex("^/i/([A-Za-z0-9-]{3,40})").find(it)?.groupValues?.get(1) }?.let { App.of(this).session.refInstitute = it.lowercase(); homeRefresh.intValue++ }
    }

    private fun codeFromIntent(i: Intent?): String? = i?.data?.path?.let { Regex("^/t/([A-Za-z0-9]{4,10})").find(it)?.groupValues?.get(1)?.uppercase() }
}

interface Nav { fun go(s: Screen); fun replace(s: Screen); fun back(): Boolean }
