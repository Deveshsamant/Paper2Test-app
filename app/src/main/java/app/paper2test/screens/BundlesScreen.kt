package app.paper2test.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.paper2test.*
import kotlinx.coroutines.launch
import org.json.JSONObject

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

    Scaffold(topBar = { TopAppBar(title = { Text("My bundles") }, navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            val list = bundles
            if (list == null) item { Text("Loading…") }
            else if (list.isEmpty()) item { Text("You don't own any bundles yet. Browse and buy bundles on paper2test.app; they appear here for taking tests.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else items(list) { b ->
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(b.optString("title"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    val cap = if (b.isNull("max_attempts_per_test")) null else b.optInt("max_attempts_per_test")
                    Text("${b.optInt("item_count")} tests · ${cap?.let { "$it attempts per test" } ?: "unlimited attempts"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val items = b.getJSONArray("items")
                    for (i in 0 until items.length()) {
                        val it = items.getJSONObject(i); val p = it.optJSONObject("progress")
                        val capped = cap != null && p != null && !p.optBoolean("open") && p.optInt("attempts") >= cap
                        val prog = when { p == null -> "not started"; p.optBoolean("open") -> "in progress"; else -> "${p.optInt("attempts")} attempt(s)${if (!p.isNull("best")) " · best ${p.get("best")}" else ""}" }
                        ListItem(headlineContent = { Text(it.optString("title")) }, supportingContent = { Text("${it.optInt("question_count")} Qs · ${it.optInt("duration_sec") / 60} min · $prog") },
                            trailingContent = { TextButton(onClick = { if (capped) nav.go(Screen.Web("/#/results/${it.optString("code")}", "My answers")) else nav.go(Screen.Exam(it.optString("code"))) }) { Text(if (capped) "Answers" else if (p?.optBoolean("open") == true) "Continue" else if (p != null) "Retake" else "Start") } })
                    }
                } }
            }
        }
    }
}
