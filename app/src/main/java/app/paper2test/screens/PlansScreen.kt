package app.paper2test.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.paper2test.*
import app.paper2test.ui.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** What each plan gives (same promises as the website's Pricing page). */
private val FEATURES = mapOf(
    "student" to listOf("10 papers, valid for a year", "You + 5 friends per test", "Papers up to 80 pages", "Answer key later, results, answer sheets"),
    "teacher" to listOf("Papers up to 80 pages", "Excel export, no watermark", "Answer key later, results, answer sheets", "Host any test again for a new batch"),
    "coaching" to listOf("Your institute's name and logo", "Priority reading of papers", "Papers up to 80 pages", "Rank lists and every answer sheet"),
)
private val PLAN_META = mapOf(
    "student" to Triple(Icons.Default.School, "Student", "For your own practice with friends"),
    "teacher" to Triple(Icons.Default.CastForEducation, "Teacher", "For classes and tuition batches"),
    "coaching" to Triple(Icons.Default.CorporateFare, "Coaching", "For coaching institutes"),
)

/** Plans & paper packs, bought through Google Play (prices come from Google Play, set on the admin panel). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(nav: Nav, tab: Boolean = false) {
    val ctx = LocalContext.current
    val app = App.of(ctx)
    val scope = rememberCoroutineScope()
    val billing = app.billing
    var me by remember { mutableStateOf<JSONObject?>(null) }
    var items by remember { mutableStateOf<List<PlayItem>?>(null) }
    var available by remember { mutableStateOf(true) }
    var yearly by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }

    fun load() = scope.launch {
        runCatching { me = app.api.get("/plans/me") }
        runCatching { billing.items() }.onSuccess { (ok, list) -> available = ok; items = list }.onFailure { available = false; items = emptyList() }
    }
    DisposableEffect(Unit) {
        billing.onResult = { _, status ->
            busy = null
            msg = when (status) {
                "granted", "already" -> "Payment received - it's added to your account."
                "pending" -> "Payment is being processed. It is added as soon as Google confirms it."
                "other_account" -> "This purchase belongs to another Paper2Test account."
                "network" -> "Payment done, but we couldn't reach the server. Open this screen again in a minute."
                else -> if (status.startsWith("play_")) "Google Play could not complete the payment." else "Something went wrong ($status). Your payment is safe - reopen this screen to retry."
            }
            load()
        }
        load()
        onDispose { billing.onResult = null }
    }
    fun buy(it: PlayItem) {
        busy = it.sku; msg = null
        if (!billing.buy(ctx as Activity, it)) { busy = null; msg = "Google Play could not open the payment screen." }
    }

    val list = items
    val current = me?.optString("plan", "free") ?: "free"
    // Yearly saving vs 12 monthly payments (Teacher), shown on the switch.
    val save = list?.let { l ->
        val m = l.firstOrNull { it.sku == "teacher_m" }?.pricePaise; val y = l.firstOrNull { it.sku == "teacher_y" }?.pricePaise
        if (m != null && y != null && m > 0) ((1 - y.toDouble() / (m * 12)) * 100).toInt().takeIf { it > 0 } else null
    }

    Scaffold(containerColor = P2T.Canvas, topBar = {
        TopAppBar(title = { Text("Plans & packs", style = MaterialTheme.typography.titleLarge) }, navigationIcon = { if (!tab) IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = P2T.Canvas))
    }, bottomBar = { if (tab) TabBar(nav, "plans") }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header
            item {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.clip(CircleShape).background(P2T.Tint2).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, Modifier.size(16.dp), tint = P2T.Brand); Spacer(Modifier.width(4.dp))
                        Text("Plans for hosts", color = P2T.Brand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Simple, clear pricing", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text("Students always take tests free. You pay only for the papers you turn into tests.", color = P2T.Ink2, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
            item { CurrentPlan(me) }
            msg?.let { m -> item { Note(m, Icons.Default.Info) } }
            if (list == null) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else {
                if (!available) item { Note("Buying works in the Google Play version of the app. Prices shown here are for reference.", Icons.Default.Storefront) }
                // Monthly / yearly switch
                item {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P2T.Tint2).padding(4.dp)) {
                        listOf(false to "Monthly", true to "Yearly").forEach { (y, label) ->
                            val on = yearly == y
                            Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) P2T.Brand else Color.Transparent).clickable { yearly = y }.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Text(label, color = if (on) Color.White else P2T.Ink2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (y && save != null) { Spacer(Modifier.width(6.dp)); Text("SAVE $save%", Modifier.clip(CircleShape).background(if (on) Color(0x33FFFFFF) else Color(0xFFD1FAE5)).padding(horizontal = 7.dp, vertical = 2.dp), color = if (on) Color.White else P2T.OkInk, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                            }
                        }
                    }
                }
                listOf("student", "teacher", "coaching").forEach { plan ->
                    val sku = when (plan) { "student" -> "student_10"; else -> if (yearly) "${plan}_y" else "${plan}_m" }
                    list.firstOrNull { it.sku == sku }?.let { it ->
                        item(key = it.sku) { PlanCard(plan, it, popular = plan == "teacher", isCurrent = current == plan, busy = busy == it.sku, buyable = available) { buy(it) } }
                    }
                }
                // Paper packs
                item {
                    Column(Modifier.padding(top = 10.dp)) {
                        Text("Paper packs", style = MaterialTheme.typography.titleLarge)
                        Text("Credits never expire. 30 students per test. Buy only when you need one.", color = P2T.Muted, fontSize = 13.sp)
                    }
                }
                list.filter { it.kind == "pack" }.sortedBy { it.credits ?: 0 }.forEachIndexed { i, it ->
                    item(key = it.sku) { PackRow(it, best = i == 1, busy = busy == it.sku, buyable = available) { buy(it) } }
                }
                item { HowItWorks() }
                item { Faq() }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Payments by Google Play (UPI, cards, Play balance). Plans renew automatically until you cancel - cancel any time in Google Play → Payments & subscriptions.", color = P2T.Muted, fontSize = 12.sp)
                        TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=app.paper2test"))) }, contentPadding = PaddingValues(0.dp)) { Text("Manage subscriptions") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, P2T.Line, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = P2T.Brand); Spacer(Modifier.width(10.dp))
        Text(text, color = P2T.Ink2, fontSize = 13.sp)
    }
}

/** "Current plan" balance card (Stitch: current account balance). */
@Composable
private fun CurrentPlan(me: JSONObject?) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(P2T.Tint, Color.White))).border(1.dp, P2T.Tint3, shape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("CURRENT PLAN", color = P2T.Brand, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
        if (me == null) { Text("Loading…", color = P2T.Muted); return@Column }
        val plan = me.optString("plan", "free")
        val label = if (me.optBoolean("is_admin")) "Admin" else if (plan == "free") "Free" else plan.replaceFirstChar { it.uppercase() }
        val lim = me.optJSONObject("limits")
        val perMonth = lim?.let { if (it.isNull("papers_per_month")) null else it.optInt("papers_per_month") }
        val used = if (perMonth != null) me.optInt("papers_this_month") else me.optInt("papers_total")
        val cap = perMonth ?: lim?.let { if (it.isNull("free_papers_total")) null else it.optInt("free_papers_total") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(label, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = P2T.Ink)
                    if (!me.isNull("plan_expires_at")) Text("  until ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(me.optLong("plan_expires_at")))}", color = P2T.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                if (cap != null) Text("$used / $cap papers ${if (perMonth != null) "this month" else "used"}", color = P2T.Ink2, fontSize = 13.sp)
            }
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, P2T.Tint3, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.WorkspacePremium, null, Modifier.size(28.dp), tint = P2T.Brand)
            }
        }
        if (cap != null && cap > 0) LinearProgressIndicator(progress = { (used.toFloat() / cap).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = P2T.Brand2, trackColor = P2T.Tint3, drawStopIndicator = {})
        val credits = me.optInt("paper_credits")
        val takers = lim?.let { if (it.isNull("takers_per_test")) "Unlimited" else it.optInt("takers_per_test").toString() }
        Text(listOfNotNull(if (credits > 0) "$credits paper credit${if (credits > 1) "s" else ""}" else null, takers?.let { "$it students per test" }).joinToString(" · "), color = P2T.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun PlanCard(plan: String, it: PlayItem, popular: Boolean, isCurrent: Boolean, busy: Boolean, buyable: Boolean, onBuy: () -> Unit) {
    val (icon, name, sub) = PLAN_META[plan] ?: Triple(Icons.Default.WorkspacePremium, it.title, "")
    val shape = RoundedCornerShape(20.dp)
    val per = when { it.type == "subs" && it.months == 12 -> "/ year"; it.type == "subs" -> "/ month"; else -> "for a year" }
    Box(Modifier.fillMaxWidth().padding(top = if (popular) 12.dp else 0.dp)) {
        Column(
            Modifier.fillMaxWidth().then(if (popular) Modifier.shadow(16.dp, shape, spotColor = P2T.Brand2) else Modifier).clip(shape).background(Color.White)
                .border(if (popular) 2.dp else 1.dp, if (popular) P2T.Brand2 else P2T.Line, shape).padding(18.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (popular) P2T.Brand else P2T.Tint2), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (popular) Color.White else P2T.Brand) }
                Spacer(Modifier.weight(1f))
                if (isCurrent) Pill("Your plan", bg = Color(0xFFECFDF5), fg = P2T.OkInk)
                else if (it.wasPaise != null && it.pricePaise != null) Pill("${((1 - it.pricePaise.toDouble() / it.wasPaise) * 100).toInt()}% off", bg = Color(0xFFECFDF5), fg = P2T.OkInk)
                else if (plan == "student") Pill("One-time", bg = P2T.Tint, fg = P2T.Ink2)
            }
            Text(name, style = MaterialTheme.typography.headlineSmall)
            Text(sub, color = P2T.Muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(it.price ?: "—", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, color = if (popular) P2T.Brand else P2T.Ink)
                Spacer(Modifier.width(6.dp)); Text(per, color = P2T.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                it.wasPaise?.let { w -> Spacer(Modifier.width(8.dp)); Text(rupees(w), color = P2T.Muted, fontSize = 14.sp, textDecoration = TextDecoration.LineThrough, modifier = Modifier.padding(bottom = 6.dp)) }
            }
            if (plan == "teacher" || plan == "coaching") {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(P2T.Tint).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = P2T.Indigo); Spacer(Modifier.width(8.dp))
                    Text(if (plan == "teacher") "20 papers a month + 200 students per test" else "100 papers a month + unlimited students", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = P2T.Ink)
                }
            }
            FEATURES[plan]?.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = if (popular) P2T.Brand2 else P2T.Ok); Spacer(Modifier.width(10.dp))
                    Text(f, fontSize = 14.sp, color = P2T.Ink2)
                }
            }
            Spacer(Modifier.height(2.dp))
            if (isCurrent && plan != "student") OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Current plan") }
            else if (popular) Button(onClick = onBuy, enabled = buyable && it.details != null && !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Text(if (busy) "Opening Google Play…" else "Get $name", fontSize = 16.sp); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
            }
            else FilledTonalButton(onClick = onBuy, enabled = buyable && it.details != null && !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text(if (busy) "Opening Google Play…" else "Get $name", fontSize = 15.sp) }
        }
        if (popular) Row(Modifier.align(Alignment.TopCenter).offset(y = (-12).dp).clip(CircleShape).background(P2T.Brand).padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = Color.White); Spacer(Modifier.width(4.dp))
            Text("MOST POPULAR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
        }
    }
}

@Composable
private fun PackRow(it: PlayItem, best: Boolean, busy: Boolean, buyable: Boolean, onBuy: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val n = it.credits ?: 1
    val perPaper = it.pricePaise?.let { p -> rupees(p / n) }
    Box {
        Row(Modifier.fillMaxWidth().clip(shape).background(Color.White).border(if (best) 2.dp else 1.dp, if (best) P2T.Brand2 else P2T.Line, shape).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(if (best) P2T.Tint3 else P2T.Tint), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("$n", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = P2T.Brand)
                Text(if (n == 1) "PAPER" else "PAPERS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = P2T.Muted)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$n paper${if (n > 1) "s" else ""}", style = MaterialTheme.typography.titleMedium)
                Text(if (n > 1 && perPaper != null) "$perPaper per paper" else "One more paper, any time", color = P2T.Muted, fontSize = 13.sp)
            }
            Button(onClick = onBuy, enabled = buyable && it.details != null && !busy, shape = RoundedCornerShape(12.dp),
                colors = if (best) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = P2T.Tint2, contentColor = P2T.Brand),
                contentPadding = PaddingValues(horizontal = 14.dp)) { Text(it.price ?: "—", fontWeight = FontWeight.Bold) }
        }
        if (best) Text("BEST VALUE", Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = (-9).dp).clip(CircleShape).background(Color(0xFFE2DFFF)).padding(horizontal = 9.dp, vertical = 3.dp),
            color = P2T.Indigo, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun HowItWorks() {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(shape).background(P2T.Tint).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.HelpOutline, null, tint = P2T.Brand); Spacer(Modifier.width(8.dp)); Text("How papers are counted", style = MaterialTheme.typography.titleLarge) }
        listOf(
            "Upload a paper" to "Scan the pages or pick a PDF. Questions, options, diagrams and Hindi text are read for you.",
            "One paper = one credit" to "Each paper you turn into a test uses one paper from your plan (or one pack credit). Hosting it again is free.",
            "Share and see results" to "Share the link, code or QR. Scores, rank list and every answer sheet come in live.",
        ).forEachIndexed { i, (t, d) ->
            Row {
                Box(Modifier.size(28.dp).clip(CircleShape).background(P2T.Brand), contentAlignment = Alignment.Center) { Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Spacer(Modifier.width(12.dp))
                Column { Text(t, style = MaterialTheme.typography.titleMedium); Text(d, color = P2T.Ink2, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun Faq() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text("Questions", style = MaterialTheme.typography.titleLarge)
        listOf(
            "Do students have to pay?" to "No. Students join with the link, code or QR and take tests free. Signing in is optional.",
            "Do pack credits expire?" to "No. Pack credits never expire. The Student plan's 10 papers are valid for a year; Teacher and Coaching papers reset every month.",
            "Can I add the answer key later?" to "Yes. Run the test first and add or fix the key later - every score is recalculated automatically.",
            "Can I cancel my plan?" to "Yes, any time in Google Play → Payments & subscriptions. Your plan stays active until the end of the period you paid for.",
        ).forEach { (q, a) -> FaqItem(q, a) }
    }
}

@Composable
private fun FaqItem(q: String, a: String) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, P2T.Line, shape).clickable { open = !open }.padding(14.dp).animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = P2T.Brand)
        }
        if (open) Text(a, color = P2T.Ink2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
