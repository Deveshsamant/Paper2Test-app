package app.paper2test.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.paper2test.*
import app.paper2test.ui.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** Plans & paper packs, bought through Google Play (prices come from Google Play, set on the admin panel). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(nav: Nav) {
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

    Scaffold(containerColor = P2T.Canvas, topBar = {
        TopAppBar(title = { Text("Plans & packs", style = MaterialTheme.typography.titleLarge) }, navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = P2T.Canvas))
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { CurrentPlan(me) }
            msg?.let { m -> item { P2TCard(padding = 14.dp) { Text(m, color = P2T.Ink2, fontSize = 14.sp) } } }
            val list = items
            if (list == null) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else {
                if (!available) item { P2TCard(padding = 14.dp) { Text("Buying in the app works in the Google Play version of Paper2Test.", color = P2T.Ink2, fontSize = 14.sp) } }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Plans", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(P2T.Tint).padding(3.dp)) {
                            listOf(false to "Monthly", true to "Yearly").forEach { (y, label) ->
                                Text(label, Modifier.clip(RoundedCornerShape(9.dp)).background(if (yearly == y) Color.White else Color.Transparent).clickable { yearly = y }.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = if (yearly == y) P2T.Brand else P2T.Ink2, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                val planSkus = listOf(if (yearly) "teacher_y" else "teacher_m", if (yearly) "coaching_y" else "coaching_m", "student_10")
                planSkus.mapNotNull { s -> list.firstOrNull { it.sku == s } }.forEach { it ->
                    item(key = it.sku) {
                        OfferCard(it, popular = it.sku.startsWith("teacher"), busy = busy == it.sku, per = if (it.type == "subs") (if (yearly) "/ year" else "/ month") else "one time") {
                            busy = it.sku; msg = null
                            if (!billing.buy(ctx as Activity, it)) { busy = null; msg = "Google Play could not open the payment screen." }
                        }
                    }
                }
                item { Text("Paper packs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp)) }
                list.filter { it.kind == "pack" }.forEach { it ->
                    item(key = it.sku) {
                        OfferCard(it, popular = false, busy = busy == it.sku, per = "one time") {
                            busy = it.sku; msg = null
                            if (!billing.buy(ctx as Activity, it)) { busy = null; msg = "Google Play could not open the payment screen." }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Payments by Google Play. Plans renew automatically until you cancel - cancel any time in Google Play → Payments & subscriptions. Students always take tests free.", color = P2T.Muted, fontSize = 12.sp)
                        TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=app.paper2test"))) }, contentPadding = PaddingValues(0.dp)) { Text("Manage subscriptions") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentPlan(me: JSONObject?) {
    GradientCard {
        if (me == null) { Text("Your plan", color = Color.White, style = MaterialTheme.typography.titleMedium); return@GradientCard }
        val plan = me.optString("plan", "free")
        val label = if (me.optBoolean("is_admin")) "Admin" else if (plan == "free") "Free plan" else "${plan.replaceFirstChar { it.uppercase() }} plan"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WorkspacePremium, null, tint = Color.White); Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (!me.isNull("plan_expires_at")) Text("until ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(me.optLong("plan_expires_at")))}", color = Color(0xD9FFFFFF), fontSize = 12.sp)
        }
        val lim = me.optJSONObject("limits")
        val perMonth = lim?.let { if (it.isNull("papers_per_month")) null else it.optInt("papers_per_month") }
        val used = if (perMonth != null) me.optInt("papers_this_month") else me.optInt("papers_total")
        val cap = perMonth ?: lim?.let { if (it.isNull("free_papers_total")) null else it.optInt("free_papers_total") }
        Text(buildString {
            if (cap != null) append("$used / $cap papers ${if (perMonth != null) "this month" else "used"}")
            val credits = me.optInt("paper_credits")
            if (credits > 0) append("${if (isNotEmpty()) " · " else ""}$credits paper credit${if (credits > 1) "s" else ""}")
            val takers = lim?.let { if (it.isNull("takers_per_test")) "unlimited" else it.optInt("takers_per_test").toString() }
            if (takers != null) append("${if (isNotEmpty()) " · " else ""}$takers students per test")
        }, color = Color(0xE6FFFFFF), fontSize = 13.sp)
        if (cap != null && cap > 0) LinearProgressIndicator(progress = { (used.toFloat() / cap).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Color.White, trackColor = Color(0x33FFFFFF), drawStopIndicator = {})
    }
}

@Composable
private fun OfferCard(it: PlayItem, popular: Boolean, busy: Boolean, per: String, onBuy: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Color.White).border(if (popular) 2.dp else 1.dp, if (popular) P2T.Brand2 else P2T.Line, shape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(it.title.substringBefore(" · "), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (popular) Pill("Most popular", bg = Color(0xFFECFDF5), fg = P2T.OkInk)
        }
        Text(it.blurb, color = P2T.Ink2, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(it.price ?: "—", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = P2T.Ink)
            Spacer(Modifier.width(6.dp)); Text(per, color = P2T.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Button(onClick = onBuy, enabled = it.details != null && !busy, shape = RoundedCornerShape(12.dp)) { Text(if (busy) "Opening…" else "Buy") }
        }
    }
}
