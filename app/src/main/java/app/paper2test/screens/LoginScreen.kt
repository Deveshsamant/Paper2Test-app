package app.paper2test.screens

import androidx.compose.foundation.Image
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import app.paper2test.ui.*
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
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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
                val cm = CredentialManager.create(ctx)
                // Bottom sheet first (one tap for returning users); if Play Services has no pre-authorised account
                // it throws NoCredentialException, so fall back to the full Sign in with Google account picker.
                val result = try {
                    val quick = GetGoogleIdOption.Builder().setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID).setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
                    cm.getCredential(ctx, GetCredentialRequest.Builder().addCredentialOption(quick).build())
                } catch (e: NoCredentialException) {
                    val full = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
                    cm.getCredential(ctx, GetCredentialRequest.Builder().addCredentialOption(full).build())
                }
                val idToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                val r = app.api.post("/host/login", JSONObject().put("id_token", idToken))
                app.session.token = r.getString("token")
                val me = app.api.get("/me").getJSONObject("user")
                app.session.userName = me.optString("name", null); app.session.username = me.optString("username", null)
                nav.replace(Screen.Home)
            } catch (e: GetCredentialException) {
                error = when {
                    e is NoCredentialException -> "No Google account available on this device. Add one in Settings > Accounts, or join with a test code below."
                    e.message?.contains("10") == true || e.message?.contains("Developer console", true) == true -> "Google sign-in is not configured for this build (Android OAuth client / SHA-1)."
                    e.message?.contains("cancel", true) == true -> "Sign-in cancelled."
                    else -> "Sign-in unavailable: ${e.message}"
                }
            } catch (e: ApiException) { error = "Server refused the sign-in (${e.code})." }
            catch (e: Exception) { error = e.message ?: "Sign-in failed" }
            finally { busy = false }
        }
    }

    // Email sign-in appears once the server can send emails (/api/config email_login).
    var emailOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { emailOn = runCatching { app.api.get("/config").optBoolean("email_login") }.getOrDefault(false) }

    // The owner's sign-in design (same as the website on phones): the full picture (logo, headline, features, 3D page)
    // as the background, the welcome card in its light part. Always light. The picture's top is dark, so light
    // status-bar icons here; restored on leaving.
    val activity = ctx as? android.app.Activity
    val appDark = P2T.isDark
    DisposableEffect(Unit) {
        val win = activity?.window
        val ctl = win?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        ctl?.isAppearanceLightStatusBars = false; ctl?.isAppearanceLightNavigationBars = true
        onDispose { ctl?.isAppearanceLightStatusBars = !appDark; ctl?.isAppearanceLightNavigationBars = !appDark }
    }
    P2TTheme(dark = false) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFB7D4FE))) {
        // The picture is 1:2. Fit the width, or the height on taller screens (a little is trimmed at the sides).
        val imgW = if (maxWidth > maxHeight / 2) maxWidth else maxHeight / 2
        val imgH = imgW * 2
        val side = maxWidth * 0.1f
        val bottomGap = maxHeight * 0.065f
        Box(Modifier.fillMaxSize()) {
            Image(painterResource(R.drawable.mobile_onboarding_bg), null, Modifier.align(Alignment.TopCenter).requiredWidth(imgW).height(imgH), contentScale = ContentScale.FillBounds)
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = side, end = side, bottom = bottomGap), horizontalAlignment = Alignment.CenterHorizontally) {
                P2TCard(padding = 18.dp) {
                    Text("Welcome back", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, letterSpacing = (-0.7).sp, color = Color(0xFF0F172A))
                    Text("Sign in to host tests, keep your scores in one place and use test bundles.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color(0xFF64748B), fontSize = 14.sp, lineHeight = 19.sp)
                    OutlinedButton(onClick = { signIn() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)), contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Image(painterResource(R.drawable.ic_google), null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                        Text(if (busy) "Signing in…" else "Continue with Google", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = Color(0xFF0F172A))
                    }
                    // Email + password (sign up with a code by email, forgot password): the website's sign-in page, which
                    // hands the session back to the app (P2TApp.signedIn).
                    if (emailOn) OutlinedButton(onClick = { nav.go(Screen.Web("/#/login", "Sign in with email")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Email, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Use email and password", fontWeight = FontWeight.SemiBold)
                    }
                    error?.let { Text(it, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text("By continuing you agree to the Terms and Privacy Policy.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 11.sp, lineHeight = 15.sp, color = Color(0xFF64748B))
                }
            }
        }
    }
    }
}

@Composable
fun CodeField(code: String, onChange: (String) -> Unit, onGo: () -> Unit, onDark: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(code, { onChange(it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8)) }, Modifier.weight(1f),
            placeholder = { Text("TEST CODE", letterSpacing = 4.sp, fontFamily = Jakarta, fontWeight = FontWeight.Bold, color = if (onDark) Color.White.copy(alpha = .6f) else Color(0xFFA5B4D4)) },
            textStyle = LocalTextStyle.current.copy(letterSpacing = 4.sp, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = if (onDark) Color.White else P2T.Ink),
            singleLine = true, shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { if (code.length >= 4) onGo() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = if (onDark) Color.White.copy(alpha = .16f) else P2T.Tint, unfocusedContainerColor = if (onDark) Color.White.copy(alpha = .16f) else P2T.Tint,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = if (onDark) Color.White else P2T.Brand))
        Button(onClick = onGo, enabled = code.length >= 4, modifier = Modifier.height(54.dp), shape = RoundedCornerShape(14.dp),
            colors = if (onDark) ButtonDefaults.buttonColors(containerColor = P2T.Card, contentColor = P2T.Brand, disabledContainerColor = Color.White.copy(alpha = .35f), disabledContentColor = Color.White) else ButtonDefaults.buttonColors()) {
            Text("Join"); Spacer(Modifier.width(4.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}
