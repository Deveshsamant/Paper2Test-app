package app.paper2test

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import app.paper2test.screens.*

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingCode.value = codeFromIntent(intent)
        instituteFromIntent(intent)
        Institutes.readInstallReferrer(this)
        pendingUrl.value = intent?.getStringExtra("url")
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

            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1F6FEB), secondary = Color(0xFF2E9E4F))) {
                Surface {
                    when (val s = stack.last()) {
                        Screen.Login -> LoginScreen(nav)
                        Screen.Home -> key(homeRefresh.intValue) { HomeScreen(nav) }
                        Screen.Scan -> ScanScreen(nav)
                        Screen.Bundles -> BundlesScreen(nav)
                        is Screen.Exam -> WebScreen(nav, "/t/${s.code}", "Test ${s.code}", exam = true)
                        is Screen.Web -> WebScreen(nav, s.path, s.title)
                    }
                }
            }
        }
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
