package app.paper2test.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import app.paper2test.ui.*
import app.paper2test.R
import androidx.compose.ui.text.style.TextOverflow
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
    var spaces by remember { mutableStateOf(app.session.spacesJson?.let { runCatching { org.json.JSONArray(it).objects() }.getOrNull() } ?: emptyList()) }
    var menu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var allTaken by remember { mutableStateOf(false) }

    fun load() = scope.launch {
        try {
            // Came through an institute's link before signing in: join it now, then read the profile.
            Institutes.claimReferral(app)
            Institutes.joinPending(app)
            // Spaces (personal / my institute / institute I study at): a phone without a valid choice opens the default.
            runCatching { app.api.get("/me/spaces") }.getOrNull()?.let { r ->
                spaces = r.getJSONArray("spaces").objects()
                app.session.spacesJson = r.getJSONArray("spaces").toString()
                if (spaces.none { it.optString("id") == app.session.space }) app.session.space = r.optString("default", "personal")
            }
            user = app.api.get("/me").getJSONObject("user")
            // New account, or no username yet (a unique username is required): the website's welcome page (name,
            // username, photo, exams) once per app start; it closes itself when done.
            val noUsername = user?.optString("username").let { it.isNullOrBlank() || it == "null" }
            if ((user?.optBoolean("onboarded", true) == false || noUsername) && !app.session.welcomeOffered) {
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
            instTests = if (user?.optJSONObject("institute") != null) runCatching { app.api.get("/me/institute/tests").getJSONArray("tests").objects() }.getOrDefault(emptyList()) else emptyList()
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

    val name = user?.optString("name")?.takeIf { it.isNotBlank() && it != "null" } ?: app.session.userName ?: "there"
    val uname = user?.optString("username")?.takeIf { it.isNotBlank() && it != "null" }
    val inst = user?.optJSONObject("institute")
    val current = spaces.firstOrNull { it.optString("id") == app.session.space } ?: spaces.firstOrNull()
    val kind = current?.optString("kind") ?: "personal"
    /** Switch space: lists, branding and plan all belong to the space, so reload everything. */
    fun switchSpace(id: String) {
        menu = false
        if (id == app.session.space) return
        app.session.space = id
        tests = null; attempts = null; instTests = emptyList()
        load()
    }

    Scaffold(containerColor = P2T.Canvas, topBar = {
        TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White.copy(alpha = .92f)),
            title = {
                inst?.let { CoBrand(it) } ?: Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.logo), null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(8.dp)); Text("Paper2Test", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }
            },
            actions = {
                // Profile picture: switch space, profile & settings, sign out.
                Box {
                    IconButton(onClick = { menu = true }) {
                        RemoteImage(user?.optString("avatar_url")?.takeIf { it.isNotBlank() && it != "null" }, 34.dp, name, circle = true)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp), containerColor = Color.White) {
                        if (spaces.size > 1) {
                            Text("SWITCH SPACE", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = P2T.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            spaces.forEach { s ->
                                val sid = s.optString("id"); val on = sid == current?.optString("id")
                                DropdownMenuItem(
                                    text = { Column { Text(s.optString("name"), fontWeight = FontWeight.SemiBold); Text(s.optString("role"), color = P2T.Muted, style = MaterialTheme.typography.bodySmall) } },
                                    leadingIcon = { RemoteImage(s.optString("logo_url").takeIf { it.isNotBlank() && it != "null" }, 32.dp, s.optString("name")) },
                                    trailingIcon = { if (on) Icon(Icons.Default.Check, null, tint = P2T.Brand) },
                                    onClick = { switchSpace(sid) },
                                    modifier = if (on) Modifier.background(P2T.Tint) else Modifier,
                                )
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        }
                        DropdownMenuItem(text = { Text("Profile & settings") }, leadingIcon = { Icon(Icons.Default.Person, null) }, onClick = { menu = false; nav.go(Screen.Web("/#/settings", "Profile & settings")) })
                        DropdownMenuItem(text = { Text("Invite friends, get free tests") }, leadingIcon = { Icon(Icons.Default.Redeem, null) }, onClick = { menu = false; nav.go(Screen.Web("/#/invite", "Invite friends")) })
                        if (kind == "institute") DropdownMenuItem(text = { Text(if (current?.optString("role") == "Owner") "Batches & teachers" else "My batches") }, leadingIcon = { Icon(Icons.Default.Groups, null) }, onClick = { menu = false; nav.go(Screen.Web("/#/batches", "Batches")) })
                        DropdownMenuItem(text = { Text("Sign out") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) }, onClick = { menu = false; scope.launch { Push.unregister(ctx); app.session.clear(); nav.replace(Screen.Login) } })
                    }
                }
            })
    }, bottomBar = {
        // Tabs follow the space: running an institute, studying at one, or the personal Paper2Test.
        TabBar(nav, "home", kind, current?.optString("role") == "Owner")
    }) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= 720.dp // tablets: join + host side by side, lists in a centred column
            LazyColumn(Modifier.widthIn(max = 960.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Column(Modifier.padding(top = 4.dp)) {
                        Text("Hi, ${name.substringBefore(' ')}", style = MaterialTheme.typography.headlineMedium)
                        Text(listOfNotNull(uname?.let { "@$it" }, current?.takeIf { kind != "personal" }?.let { "${it.optString("name")} · ${it.optString("role")}" }).joinToString("  ·  "), color = P2T.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    // Student space: taking tests only (hosting is in the personal / institute space).
                    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                        JoinCard(Modifier.weight(1f), code, { code = it }, nav, scope, ctx)
                        if (kind != "student") HostCard(Modifier.weight(1f), nav)
                    } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        JoinCard(Modifier, code, { code = it }, nav, scope, ctx)
                        if (kind != "student") HostCard(Modifier, nav)
                    }
                }
                // Admin accounts: the full admin panel (bundles, prices, calendar, announcements, users, AI spend).
                if (user?.optString("role") == "admin") item {
                    OutlinedButton(onClick = { nav.go(Screen.Web("/admin/", "Admin")) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.AdminPanelSettings, null); Spacer(Modifier.width(8.dp)); Text("Admin panel") }
                }
                if (alerts.isNotEmpty() && kind == "personal") item {
                    P2TCard {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Event, null, tint = P2T.Brand); Spacer(Modifier.width(8.dp)); Text("Upcoming for your exams", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = { nav.tab(Screen.Web("/#/exams", "Exams & dates", "exams")) }) { Text("All") } }
                        alerts.forEach { a ->
                            val urgent = a.optBoolean("urgent")
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (urgent) Color(0xFFFEF2F2) else P2T.Tint).clickable { nav.go(Screen.Web("/#/exams/${a.optString("exam_code")}", a.optString("exam_name"))) }.padding(10.dp)) {
                                Text("${a.optString("title")}: ${a.optString("text")}", color = if (urgent) P2T.Bad else P2T.Ink, fontWeight = if (urgent) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                inst?.let { i ->
                    item {
                        P2TCard {
                            CoBrand(i, 40.dp)
                            val staff = kind == "institute"
                            Text(if (staff) "Tests for your students" else "Tests from ${i.optString("name")}", style = MaterialTheme.typography.titleMedium)
                            if (instTests.isEmpty()) Text(if (staff) "Nothing yet. Make a test and pick a batch under \"Who sees it\"." else "No tests for you yet.", style = MaterialTheme.typography.bodySmall, color = P2T.Muted)
                            instTests.forEach { t ->
                                val mine = t.optString("mine")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t.optString("title"), fontWeight = FontWeight.SemiBold)
                                        Text("${t.optInt("question_count")} Qs · ${t.optInt("duration_sec") / 60} min${if (t.optString("status") == "ended") " · ended" else ""}", style = MaterialTheme.typography.bodySmall, color = P2T.Muted)
                                    }
                                    when {
                                        kind == "institute" -> TextButton(onClick = { nav.tab(Screen.Web("/#/tests", "Tests", "tests")) }) { Text("Results") }
                                        mine == "done" -> TextButton(onClick = { nav.go(Screen.Web("/#/results/${t.optString("code")}", "My answers")) }) { Text("My answers") }
                                        t.optString("status") == "ended" -> Pill("closed", Color(0xFFF1F5F9), P2T.Ink2)
                                        else -> FilledTonalButton(onClick = { nav.go(Screen.Exam(t.optString("code"))) }) { Text(if (mine == "writing") "Continue" else "Start") }
                                    }
                                }
                            }
                            if (kind == "student") TextButton(onClick = { nav.tab(Screen.Web("/#/mybatches", "My batches", "mybatches")) }, contentPadding = PaddingValues(0.dp)) { Text("My batches & leaderboard →") }
                            else TextButton(onClick = { nav.go(Screen.Web("/#/institute/${i.optString("slug")}", i.optString("name"))) }, contentPadding = PaddingValues(0.dp)) { Text("Student link & QR →") }
                        }
                    }
                }
                if (recs.isNotEmpty() && kind == "personal") {
                    item { SectionTitle("Recommended for your exams", "Store") { nav.tab(Screen.Web("/#/store", "Store", "store")) } }
                    items(recs) { b ->
                        val paise = b.optInt("price_paise")
                        val price = if (paise == 0) "Free" else "₹" + (if (paise % 100 == 0) (paise / 100).toString() else String.format(java.util.Locale.US, "%.2f", paise / 100.0))
                        P2TCard(Modifier.clickable { nav.go(Screen.Web("/#/store/${b.optString("slug")}", "Store")) }) {
                            b.optString("exam").takeIf { it.isNotBlank() && it != "null" }?.let { Pill(it) }
                            Text(b.optString("title"), style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${b.optInt("item_count")} tests", color = P2T.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(price, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            }
                        }
                    }
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                item { SectionTitle("Tests you've taken", if ((attempts?.size ?: 0) > 5 && !allTaken) "All (${attempts?.size})" else null) { allTaken = true } }
                val at = attempts
                if (at == null) item { Text("Loading…", color = P2T.Muted) }
                else if (at.isEmpty()) item { P2TCard { Text("Nothing yet. Enter a code or scan a QR above.", color = P2T.Muted) } }
                else items(if (allTaken) at else at.take(5)) { a ->
                    val submitted = !a.isNull("submitted_at")
                    val published = a.optBoolean("result_published") && !a.isNull("score")
                    val status = if (published) "Score ${a.get("score")}" else if (!submitted) "In progress" else if (a.optBoolean("pending_key")) "Key pending" else when (a.optString("result_wait")) { "after_end" -> "After it closes"; "host" -> "Not published"; else -> if (a.optBoolean("result_hidden")) "Marks hidden" else "Not published" }
                    P2TCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.optString("title"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${a.optString("host")} · ${df.format(Date(a.optLong("started_at")))}", style = MaterialTheme.typography.bodySmall, color = P2T.Muted, maxLines = 1)
                                Spacer(Modifier.height(6.dp))
                                if (published) Pill(status, Color(0xFFECFDF5), P2T.OkInk) else if (!submitted) Pill(status, Color(0xFFFFFBEB), Color(0xFF92400E)) else Pill(status, Color(0xFFF1F5F9), P2T.Ink2)
                            }
                            Spacer(Modifier.width(8.dp))
                            if (submitted) FilledTonalButton(onClick = { nav.go(Screen.Web("/#/results/${a.optString("code")}", "My answers")) }, shape = RoundedCornerShape(12.dp)) { Text("Answers") }
                            else Button(onClick = { nav.go(Screen.Exam(a.optString("code"))) }, shape = RoundedCornerShape(12.dp)) { Text("Continue") }
                        }
                    }
                }
                val ts = tests
                if (!ts.isNullOrEmpty()) {
                    item { SectionTitle("Tests you host", "All") { nav.go(Screen.Web("/#/tests", "My tests")) } }
                    items(ts.take(5)) { t ->
                        P2TCard(Modifier.clickable { nav.go(Screen.Web("/#/test/${t.optString("id")}", "Results")) }, padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.optString("title"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${t.optInt("attempt_count")} joined · ${t.optInt("submitted_count")} submitted", style = MaterialTheme.typography.bodySmall, color = P2T.Muted)
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Pill(t.optString("code"), P2T.Tint2, P2T.Brand)
                                        val st = t.optString("status")
                                        Pill(st, if (st == "live") Color(0xFFECFDF5) else Color(0xFFF1F5F9), if (st == "live") P2T.OkInk else P2T.Ink2)
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = P2T.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinCard(modifier: Modifier, code: String, onCode: (String) -> Unit, nav: Nav, scope: kotlinx.coroutines.CoroutineScope, ctx: android.content.Context) {
    GradientCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Key, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("Join a test", color = Color.White, style = MaterialTheme.typography.titleLarge) }
        Text("Enter the code your teacher shared, or scan its QR.", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
        CodeField(code, onCode, onGo = { nav.go(Screen.Exam(code)) }, onDark = true)
        OutlinedButton(onClick = { scope.launch { scanTestCode(ctx)?.let { nav.go(Screen.Exam(it)) } } }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .4f))) {
            Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Scan QR code")
        }
    }
}

@Composable
private fun HostCard(modifier: Modifier, nav: Nav) {
    P2TCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.UploadFile, null, tint = P2T.Brand); Spacer(Modifier.width(8.dp)); Text("Host a test", style = MaterialTheme.typography.titleLarge) }
        Text("Scan a question paper with the camera; the test link is ready in minutes.", style = MaterialTheme.typography.bodySmall, color = P2T.Ink2)
        Button(onClick = { nav.go(Screen.Scan) }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text("Scan / upload a paper") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HostTile(Icons.Default.AutoAwesome, "AI paper maker", Modifier.weight(1f)) { nav.go(Screen.Web("/#/make", "Make a paper with AI")) }
            HostTile(Icons.Default.Description, "My papers", Modifier.weight(1f)) { nav.go(Screen.Web("/#/papers", "My papers")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HostTile(Icons.Default.Leaderboard, "My tests", Modifier.weight(1f)) { nav.go(Screen.Web("/#/tests", "My tests")) }
            HostTile(Icons.Default.CollectionsBookmark, "My bundles", Modifier.weight(1f)) { nav.go(Screen.Bundles) }
        }
    }
}

@Composable
private fun HostTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(P2T.Tint).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = P2T.Brand, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 2, lineHeight = 18.sp)
    }
}
