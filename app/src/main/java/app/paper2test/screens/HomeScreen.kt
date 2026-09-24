package app.paper2test.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.paper2test.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = App.of(ctx)
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf<List<JSONObject>?>(null) }
    var tests by remember { mutableStateOf<List<JSONObject>?>(null) }
    var user by remember { mutableStateOf<JSONObject?>(null) }
    var recs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var alerts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var instTests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() = scope.launch {
        try {
            // Came through an institute's link before signing in: join it now, then read the profile.
            Institutes.joinPending(app)
            user = app.api.get("/me").getJSONObject("user")
            // New account: the website's welcome page (name, photo, exams) once; it closes itself when done or skipped.
            if (user?.optBoolean("onboarded", true) == false && !app.session.welcomeOffered) {
                app.session.welcomeOffered = true
                nav.go(Screen.Web("/#/welcome", "Welcome"))
                return@launch
            }
            attempts = app.api.get("/me/attempts").getJSONArray("attempts").objects()
            tests = app.api.get("/tests").getJSONArray("tests").objects()
            // Bundles for the exams the user picked (not owned yet); nothing shown if none match or on error.
            recs = runCatching { app.api.get("/store/recommended").getJSONArray("bundles").objects() }.getOrDefault(emptyList())
            // Exam calendar alerts for the exams the user follows ("form closes in 3 days").
            alerts = runCatching { app.api.get("/me/alerts").getJSONArray("alerts").objects() }.getOrDefault(emptyList())
            // Tests the institute shares with its students.
            if (user?.optJSONObject("institute") != null) instTests = runCatching { app.api.get("/me/institute/tests").getJSONArray("tests").objects() }.getOrDefault(emptyList())
        } catch (e: ApiException) { if (e.status == 401) nav.replace(Screen.Login) else error = e.code }
        catch (e: Exception) { error = e.message }
    }
    LaunchedEffect(Unit) { load() }
    // Notifications: Android 13+ asks the user once; then this phone's token goes to the server.
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { scope.launch { Push.register(ctx) } }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !app.session.notifAsked && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            app.session.notifAsked = true
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else Push.register(ctx)
    }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Scaffold(topBar = {
        TopAppBar(title = { user?.optJSONObject("institute")?.let { CoBrand(it) } ?: Text("Paper2Test") }, actions = {
            IconButton(onClick = { nav.go(Screen.Web("/#/settings", "Profile & settings")) }) { Icon(Icons.Default.AccountCircle, "Profile") }
            IconButton(onClick = { scope.launch { Push.unregister(ctx); app.session.clear(); nav.replace(Screen.Login) } }) { Icon(Icons.AutoMirrored.Filled.Logout, "Sign out") }
        })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                val name = user?.optString("name")?.takeIf { it.isNotBlank() } ?: app.session.userName ?: "there"
                val uname = user?.optString("username")?.takeIf { it.isNotBlank() }
                Text("Hi, $name", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(uname?.let { "@$it" } ?: "Set a username in your profile", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (alerts.isNotEmpty()) {
                item {
                    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Upcoming for your exams", fontWeight = FontWeight.SemiBold)
                        alerts.forEach { a ->
                            val urgent = a.optBoolean("urgent")
                            TextButton(onClick = { nav.go(Screen.Web("/#/exams/${a.optString("exam_code")}", a.optString("exam_name"))) }, contentPadding = PaddingValues(0.dp)) {
                                Text("${a.optString("title")}: ${a.optString("text")}", color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontWeight = if (urgent) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    } }
                }
            }
            item { TextButton(onClick = { nav.go(Screen.Web("/#/exams", "Exams & dates")) }) { Text("Exams & dates →") } }
            // Admin accounts: the full admin panel (bundles, prices, calendar, announcements, users, AI spend).
            if (user?.optString("role") == "admin") item {
                Button(onClick = { nav.go(Screen.Web("/admin/", "Admin")) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.AdminPanelSettings, null); Spacer(Modifier.width(8.dp)); Text("Admin panel") }
            }
            item {
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Join a test", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) }, Modifier.weight(1f), placeholder = { Text("Test code") }, singleLine = true)
                        Button(onClick = { nav.go(Screen.Exam(code)) }, enabled = code.length >= 4) { Text("Join") }
                    }
                    OutlinedButton(onClick = { scope.launch { scanTestCode(ctx)?.let { nav.go(Screen.Exam(it)) } } }, Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Scan QR code") }
                } }
            }
            item {
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Host a test", fontWeight = FontWeight.SemiBold)
                    Text("Scan a question paper with the camera; the test link is ready in minutes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { nav.go(Screen.Scan) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text("Scan / upload a paper") }
                    OutlinedButton(onClick = { nav.go(Screen.Web("/#/make", "Make a paper with AI")) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Make a paper with AI") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { nav.go(Screen.Web("/#/papers", "My papers")) }, Modifier.weight(1f)) { Text("My papers") }
                        OutlinedButton(onClick = { nav.go(Screen.Bundles) }, Modifier.weight(1f)) { Text("My bundles") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { nav.go(Screen.Web("/#/store", "Store")) }, Modifier.weight(1f)) { Icon(Icons.Default.Storefront, null); Spacer(Modifier.width(6.dp)); Text("Store") }
                        OutlinedButton(onClick = { nav.go(Screen.Web("/#/tests", "My tests")) }, Modifier.weight(1f)) { Text("My tests") }
                    }
                } }
            }
            user?.optJSONObject("institute")?.let { inst ->
                item {
                    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CoBrand(inst, 40.dp)
                        Text(if (inst.optBoolean("own")) "Tests you share with your students" else "From ${inst.optString("name")}", fontWeight = FontWeight.SemiBold)
                        if (instTests.isEmpty()) Text(if (inst.optBoolean("own")) "Nothing shared yet. On a test's results page, turn on \"Show to my institute's students\"." else "No tests shared yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        instTests.forEach { t ->
                            val mine = t.optString("mine")
                            ListItem(headlineContent = { Text(t.optString("title")) }, supportingContent = { Text("${t.optInt("question_count")} Qs · ${t.optInt("duration_sec") / 60} min${if (t.optString("status") == "ended") " · ended" else ""}") },
                                trailingContent = {
                                    when {
                                        inst.optBoolean("own") -> TextButton(onClick = { nav.go(Screen.Web("/#/test/${t.optString("id")}", "Results")) }) { Text("Results") }
                                        mine == "done" -> TextButton(onClick = { nav.go(Screen.Web("/#/results/${t.optString("code")}", "My answers")) }) { Text("My answers") }
                                        t.optString("status") == "ended" -> Text("closed", style = MaterialTheme.typography.bodySmall)
                                        else -> TextButton(onClick = { nav.go(Screen.Exam(t.optString("code"))) }) { Text(if (mine == "writing") "Continue" else "Start") }
                                    }
                                })
                        }
                        TextButton(onClick = { nav.go(Screen.Web("/#/institute/${inst.optString("slug")}", inst.optString("name"))) }, contentPadding = PaddingValues(0.dp)) { Text("Institute page →") }
                    } }
                }
            }
            if (recs.isNotEmpty()) {
                item { Text("Recommended for your exams", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium) }
                items(recs) { b ->
                    val paise = b.optInt("price_paise")
                    val price = if (paise == 0) "Free" else "₹" + (if (paise % 100 == 0) (paise / 100).toString() else String.format(java.util.Locale.US, "%.2f", paise / 100.0))
                    ListItem(headlineContent = { Text(b.optString("title")) },
                        supportingContent = { Text(listOfNotNull(b.optString("exam").takeIf { it.isNotBlank() && it != "null" }, "${b.optInt("item_count")} tests").joinToString(" · ")) },
                        trailingContent = { TextButton(onClick = { nav.go(Screen.Web("/#/store/${b.optString("slug")}", "Store")) }) { Text(price) } })
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { Text("Tests you've taken", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium) }
            val at = attempts
            if (at == null) item { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else if (at.isEmpty()) item { Text("Nothing yet. Enter a code or scan a QR above.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else items(at) { a ->
                val submitted = !a.isNull("submitted_at")
                val score = if (a.optBoolean("result_published") && !a.isNull("score")) "Score ${a.get("score")}" else if (!submitted) "In progress" else if (a.optBoolean("pending_key")) "Key pending" else when (a.optString("result_wait")) { "after_end" -> "After the test closes"; "host" -> "Not published yet"; else -> if (a.optBoolean("result_hidden")) "Marks hidden" else "Result not published" }
                ListItem(headlineContent = { Text(a.optString("title")) }, supportingContent = { Text("${a.optString("host")} · ${df.format(Date(a.optLong("started_at")))}") },
                    trailingContent = { Text(score, fontWeight = FontWeight.SemiBold) },
                )
                TextButton(onClick = { if (submitted) nav.go(Screen.Web("/#/results/${a.optString("code")}", "My answers")) else nav.go(Screen.Exam(a.optString("code"))) }) { Text(if (submitted) "My answers" else "Continue") }
            }
            val ts = tests
            if (!ts.isNullOrEmpty()) {
                item { Text("Tests you host", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium) }
                items(ts) { t ->
                    ListItem(headlineContent = { Text(t.optString("title")) }, supportingContent = { Text("Code ${t.optString("code")} · ${t.optInt("submitted_count")} submitted · ${t.optString("status")}") },
                        trailingContent = { TextButton(onClick = { nav.go(Screen.Web("/#/test/${t.optString("id")}", "Results")) }) { Text("Results") } })
                }
            }
        }
    }
}
