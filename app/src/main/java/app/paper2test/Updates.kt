package app.paper2test

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.paper2test.ui.*

/** App updates. The website, the exam room and every page shown in the app's built-in browser are always current
 *  (they load from the server). The app's own screens need a new version from Google Play: the admin panel's App page
 *  sets the newest version and the oldest one still allowed, and the app checks it on start. */
enum class UpdateNeed { NONE, OPTIONAL, REQUIRED }
data class UpdateInfo(val need: UpdateNeed, val latestName: String, val message: String, val playUrl: String)

object Updates {
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=app.paper2test"

    suspend fun check(app: App): UpdateInfo {
        val v = app.api.get("/app/version")
        val code = BuildConfig.VERSION_CODE
        val need = when {
            code < v.optInt("min_code", 1) -> UpdateNeed.REQUIRED
            code < v.optInt("latest_code", 1) -> UpdateNeed.OPTIONAL
            else -> UpdateNeed.NONE
        }
        return UpdateInfo(need, v.optString("latest_name"), v.optString("message"), v.optString("play_url", PLAY_URL))
    }

    /** Paper2Test's page in the Play Store app (or the website version of it). */
    fun openPlay(ctx: Context, url: String = PLAY_URL) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=app.paper2test")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: ActivityNotFoundException) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Full screen: this version is too old to use (the admin raised the minimum). */
@Composable
fun UpdateRequired(info: UpdateInfo, onUpdate: () -> Unit) {
    Box(Modifier.fillMaxSize().background(P2T.Canvas).padding(24.dp), contentAlignment = Alignment.Center) {
        P2TCard(padding = 24.dp) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(P2T.Tint2).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SystemUpdate, null, tint = P2T.Brand, modifier = Modifier.size(30.dp))
            }
            Text("Update required", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(info.message.ifBlank { "This version of Paper2Test is no longer supported. Update to version ${info.latestName} to continue - it only takes a moment." },
                color = P2T.Ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Button(onClick = onUpdate, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Update on Google Play", fontSize = 16.sp) }
        }
    }
}

/** Small card above the bottom of the screen: a new version is available, or has downloaded and needs a restart. */
@Composable
fun UpdateBanner(text: String, action: String, onAction: () -> Unit, onClose: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).background(P2T.Ink).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action, color = Color(0xFF93C5FD), fontWeight = FontWeight.Bold) }
        if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Not now", tint = Color(0xB3FFFFFF)) }
    }
}
