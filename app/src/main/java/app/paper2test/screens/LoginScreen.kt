package app.paper2test.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import app.paper2test.*
import app.paper2test.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun LoginScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = App.of(ctx)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }

    fun signIn() {
        busy = true; error = null
        scope.launch {
            try {
                val option = GetGoogleIdOption.Builder().setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID).setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
                val result = CredentialManager.create(ctx).getCredential(ctx, GetCredentialRequest.Builder().addCredentialOption(option).build())
                val idToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                val r = app.api.post("/host/login", JSONObject().put("id_token", idToken))
                app.session.token = r.getString("token")
                val me = app.api.get("/me").getJSONObject("user")
                app.session.userName = me.optString("name", null); app.session.username = me.optString("username", null)
                nav.replace(Screen.Home)
            } catch (e: GetCredentialException) {
                error = if (e.message?.contains("10") == true || e.message?.contains("Developer console", true) == true)
                    "Google sign-in is not configured for this build yet (Android OAuth client / SHA-1)." else "Sign-in cancelled or unavailable: ${e.message}"
            } catch (e: ApiException) { error = "Server refused the sign-in (${e.code})." }
            catch (e: Exception) { error = e.message ?: "Sign-in failed" }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(140.dp))
        Text("Paper2Test", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Scan it. Share it. Score it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { signIn() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Signing in…" else "Continue with Google") }
        Text("Host tests, keep your scores, buy bundles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Just have a test code?", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) }, Modifier.weight(1f), label = { Text("Test code") }, singleLine = true)
            Button(onClick = { nav.go(Screen.Exam(code)) }, enabled = code.length >= 4) { Text("Join") }
        }
        TextButton(onClick = { scope.launch { scanTestCode(ctx)?.let { nav.go(Screen.Exam(it)) } } }) { Text("Scan QR code") }
    }
}
