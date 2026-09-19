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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingCode.value = codeFromIntent(intent)
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
            BackHandler(enabled = stack.size > 1) { nav.back() }

            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1F6FEB), secondary = Color(0xFF2E9E4F))) {
                Surface {
                    when (val s = stack.last()) {
                        Screen.Login -> LoginScreen(nav)
                        Screen.Home -> HomeScreen(nav)
                        Screen.Scan -> ScanScreen(nav)
                        Screen.Bundles -> BundlesScreen(nav)
                        is Screen.Exam -> WebScreen(nav, "/t/${s.code}", "Test ${s.code}", exam = true)
                        is Screen.Web -> WebScreen(nav, s.path, s.title)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); codeFromIntent(intent)?.let { pendingCode.value = it } }

    private fun codeFromIntent(i: Intent?): String? = i?.data?.path?.let { Regex("^/t/([A-Za-z0-9]{4,10})").find(it)?.groupValues?.get(1)?.uppercase() }
}

interface Nav { fun go(s: Screen); fun replace(s: Screen); fun back(): Boolean }
