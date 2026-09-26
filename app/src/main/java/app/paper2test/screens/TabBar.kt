package app.paper2test.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.paper2test.App
import app.paper2test.Nav
import app.paper2test.Screen
import app.paper2test.ui.P2T
import org.json.JSONArray

/** One bottom tab: [screen] null = Home. */
data class Tab(val id: String, val label: String, val icon: ImageVector, val screen: Screen?)

/** The tabs of a space: running an institute, studying at one, or the personal Paper2Test. */
fun tabsFor(kind: String, owner: Boolean): List<Tab> = listOf(Tab("home", "Home", Icons.Default.Home, null)) + when (kind) {
    "institute" -> listOfNotNull(
        Tab("batches", "Batches", Icons.Default.Groups, Screen.Web("/#/batches", "Batches", "batches")),
        Tab("tests", "Tests", Icons.Default.Quiz, Screen.Web("/#/tests", "Tests", "tests")),
        Tab("students", "Students", Icons.Default.School, Screen.Web("/#/students", "Students", "students")),
        if (owner) Tab("staff", "Staff", Icons.Default.Badge, Screen.Web("/#/staff", "Staff", "staff")) else null,
    )
    "student" -> listOf(
        Tab("mybatches", "Batches", Icons.Default.Groups, Screen.Web("/#/mybatches", "My batches", "mybatches")),
        Tab("itests", "Tests", Icons.Default.Quiz, Screen.Web("/#/itests", "Tests", "itests")),
        Tab("iresults", "Results", Icons.Default.Insights, Screen.Web("/#/iresults", "Results", "iresults")),
    )
    else -> listOf(
        Tab("store", "Store", Icons.Default.Storefront, Screen.Web("/#/store", "Store", "store")),
        Tab("exams", "Exams", Icons.Default.CalendarMonth, Screen.Web("/#/exams", "Exams & dates", "exams")),
        Tab("plans", "Plans", Icons.Default.WorkspacePremium, Screen.PlansTab),
    )
}

/** Kind of the current space and whether I own it, from the spaces Home cached on this phone. */
fun currentSpace(app: App): Pair<String, Boolean> {
    val list = runCatching { JSONArray(app.session.spacesJson ?: "[]") }.getOrNull() ?: JSONArray()
    val all = (0 until list.length()).map { list.getJSONObject(it) }
    val cur = all.firstOrNull { it.optString("id") == app.session.space } ?: all.firstOrNull()
    return (cur?.optString("kind")?.takeIf { it.isNotBlank() } ?: "personal") to (cur?.optString("role") == "Owner")
}

/** The bottom bar on Home and on every tab page, so moving between tabs never shows a back arrow. */
@Composable
fun TabBar(nav: Nav, selected: String, kind: String? = null, owner: Boolean? = null) {
    val app = App.of(LocalContext.current)
    val (k, o) = if (kind != null) kind to (owner ?: false) else currentSpace(app)
    NavigationBar(containerColor = P2T.Card, tonalElevation = 0.dp) {
        tabsFor(k, o).forEach { t ->
            NavigationBarItem(selected = t.id == selected, onClick = { if (t.id != selected) nav.tab(t.screen ?: Screen.Home) },
                icon = { Icon(t.icon, null) }, label = { Text(t.label, maxLines = 1) })
        }
    }
}
