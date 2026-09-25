package app.paper2test.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.paper2test.*
import app.paper2test.ui.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/** One test inside a bundle, with what the student can do next. */
private data class BundleTest(val title: String, val code: String, val meta: String, val state: String, val action: String, val section: String? = null)

private fun testsOf(b: JSONObject): List<BundleTest> {
    val cap = if (b.isNull("max_attempts_per_test")) null else b.optInt("max_attempts_per_test")
    val items = b.optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).map { i ->
        val it = items.getJSONObject(i); val p = it.optJSONObject("progress")
        val sec = it.optString("section_id").takeIf { s -> s.isNotBlank() && s != "null" }
        val base = "${it.optInt("question_count")} Qs · ${it.optInt("duration_sec") / 60} min"
        val attempts = p?.optInt("attempts") ?: 0
        val best = if (p != null && !p.isNull("best")) " · best ${p.get("best")}" else ""
        when {
            p == null -> BundleTest(it.optString("title"), it.optString("code"), "$base · not attempted", "new", "Start", sec)
            p.optBoolean("open") -> BundleTest(it.optString("title"), it.optString("code"), "$base · in progress", "open", "Continue", sec)
            cap != null && attempts >= cap -> BundleTest(it.optString("title"), it.optString("code"), "$base$best", "done", "Answers", sec)
            else -> BundleTest(it.optString("title"), it.optString("code"), "$base$best${cap?.let { c -> " · ${c - attempts} attempt${if (c - attempts == 1) "" else "s"} left" } ?: ""}", "done", "Retake", sec)
        }
    }
}

/** Bundles the user owns. Purchasing happens on the website (Play policy: no third-party payments for in-app digital goods). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundlesScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = App.of(ctx)
    val scope = rememberCoroutineScope()
    var bundles by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { scope.launch { try { val a = app.api.get("/store/mine").getJSONArray("bundles"); bundles = (0 until a.length()).map { a.getJSONObject(it) } } catch (e: Exception) { error = e.message } } }

    Scaffold(containerColor = P2T.Canvas, topBar = {
        TopAppBar(title = { Text("My bundles", style = MaterialTheme.typography.titleLarge) }, navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = P2T.Canvas))
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            val list = bundles
            if (list == null) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else {
                items(list) { b -> BundleCard(b, nav) }
                item { StoreHint(empty = list.isEmpty()) { nav.go(Screen.Web("/#/store", "Store")) } }
            }
        }
    }
}

@Composable
private fun BundleCard(b: JSONObject, nav: Nav) {
    val tests = testsOf(b)
    val done = tests.count { it.state == "done" }
    val cap = if (b.isNull("max_attempts_per_test")) null else b.optInt("max_attempts_per_test")
    val files = b.optJSONArray("files")?.length() ?: 0
    var showAll by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, P2T.Line, shape)) {
        // Gradient header: exam, title, facts, progress
        Column(Modifier.fillMaxWidth().background(P2T.Hero).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            b.optString("exam").takeIf { it.isNotBlank() && it != "null" }?.let { Pill(it.uppercase(), bg = Color(0x29FFFFFF), fg = Color.White) }
            Text(b.optString("title"), color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeaderFact(Icons.Default.Quiz, "${tests.size} tests")
                HeaderFact(Icons.Default.Replay, cap?.let { "$it attempts each" } ?: "Unlimited attempts")
                if (files > 0) HeaderFact(Icons.Default.Description, "$files PDF${if (files > 1) "s" else ""}")
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Your progress", color = Color(0xD9FFFFFF), fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("$done / ${tests.size} done", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { if (tests.isEmpty()) 0f else done.toFloat() / tests.size }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = P2T.Ok, trackColor = Color(0x33FFFFFF), drawStopIndicator = {})
        }
        // Optional sections: tests without a section first, then each section under its own heading.
        val secArr = b.optJSONArray("sections")
        val sections = (0 until (secArr?.length() ?: 0)).map { secArr!!.getJSONObject(it) }.map { it.optString("id") to it.optString("title") }
        val known = sections.map { it.first }.toSet()
        val ordered = tests.filter { it.section == null || it.section !in known } + sections.flatMap { (id, _) -> tests.filter { it.section == id } }
        val shown = if (showAll) ordered else ordered.take(4)
        var lastSection: String? = null
        shown.forEachIndexed { i, t ->
            val sec = t.section?.takeIf { it in known }
            if (sec != null && sec != lastSection) {
                Text(sections.first { it.first == sec }.second, Modifier.fillMaxWidth().background(P2T.Tint).padding(horizontal = 14.dp, vertical = 8.dp), fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = P2T.Brand)
            } else if (i > 0) HorizontalDivider(color = P2T.Line)
            lastSection = sec
            TestRow(t) { if (t.action == "Answers") nav.go(Screen.Web("/#/results/${t.code}", "My answers")) else nav.go(Screen.Exam(t.code)) }
        }
        if (tests.size > 4) TextButton(onClick = { showAll = !showAll }, Modifier.fillMaxWidth()) { Text(if (showAll) "Show fewer" else "+${tests.size - 4} more tests in this series") }
        OutlinedButton(onClick = { nav.go(Screen.Web("/#/store/${b.optString("slug")}", b.optString("title"))) }, Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(if (files > 0) "Details & study material ($files PDF${if (files > 1) "s" else ""})" else "Bundle details", Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HeaderFact(icon: ImageVector, text: String) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x24FFFFFF)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = Color.White); Spacer(Modifier.width(4.dp))
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TestRow(t: BundleTest, onClick: () -> Unit) {
    val (icon, tint, bg) = when (t.state) {
        "open" -> Triple(Icons.Default.Timelapse, P2T.Warn, Color(0xFFFFFBEB))
        "done" -> Triple(Icons.Default.CheckCircle, P2T.Ok, Color(0xFFECFDF5))
        else -> Triple(Icons.Default.PlayCircle, P2T.Brand, P2T.Tint)
    }
    Row(Modifier.fillMaxWidth().background(if (t.state == "open") Color(0xFFFFFDF5) else Color.White).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = tint) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = P2T.Ink)
            Text(t.meta, fontSize = 12.sp, color = if (t.state == "open") Color(0xFFB45309) else P2T.Muted, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        when (t.action) {
            "Start" -> FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp)) { Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Start") }
            "Continue" -> Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF7E0), contentColor = Color(0xFFB45309)), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Continue") }
            "Retake" -> OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp)) { Icon(Icons.Default.Replay, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Retake") }
            else -> TextButton(onClick = onClick) { Icon(Icons.Default.Visibility, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Answers") }
        }
    }
}

@Composable
private fun StoreHint(empty: Boolean, onStore: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, P2T.Line, shape).background(Color(0x80FFFFFF)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(P2T.Tint), contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory2, null, tint = P2T.Brand) }
        Text(if (empty) "No bundles yet" else "Looking for another series?", style = MaterialTheme.typography.titleMedium)
        Text("Mock-test series you buy on paper2test.app appear here, ready to take in the app.", color = P2T.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onStore, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Storefront, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Browse the store") }
    }
}
