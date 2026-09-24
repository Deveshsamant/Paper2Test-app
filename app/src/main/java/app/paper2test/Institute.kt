package app.paper2test

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/** Institutes: "Paper2Test × <institute>" branding for students who came through an institute's link. */
object Institutes {
    /** First start after installing from the Play Store with the institute's link (…&referrer=institute%3D<slug>). */
    fun readInstallReferrer(ctx: Context) {
        val app = App.of(ctx)
        if (app.session.referrerChecked) return
        val client = runCatching { InstallReferrerClient.newBuilder(ctx).build() }.getOrNull() ?: return
        runCatching {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(code: Int) {
                    if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                        val ref = runCatching { client.installReferrer.installReferrer }.getOrNull().orEmpty()
                        Regex("(?:^|&)(?:institute|utm_content)=([a-z0-9-]{3,40})", RegexOption.IGNORE_CASE).find(ref)?.groupValues?.get(1)?.let { app.session.refInstitute = it.lowercase() }
                    }
                    if (code != InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE) app.session.referrerChecked = true
                    runCatching { client.endConnection() }
                }
                override fun onInstallReferrerServiceDisconnected() {}
            })
        }
    }

    /** Signed in with a remembered institute link: join it (once). Returns true when something changed. */
    suspend fun joinPending(app: App): Boolean {
        val slug = app.session.refInstitute ?: return false
        app.session.refInstitute = null
        return runCatching { app.api.post("/institutes/$slug/join", JSONObject()); true }.getOrDefault(false)
    }
}

/** An image from the site (institute logo), loaded once. */
@Composable
fun RemoteImage(path: String?, size: Dp, fallback: String) {
    val img by produceState<ImageBitmap?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching { URL(BuildConfig.SITE + path).openStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(size / 4)
    val i = img
    if (i != null) Image(i, null, Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    else Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        Text(fallback.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

/** "Paper2Test × Sharma Classes" with the institute's logo. */
@Composable
fun CoBrand(inst: JSONObject, logo: Dp = 28.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteImage(inst.optString("logo_url").takeIf { it.isNotBlank() && it != "null" }, logo, inst.optString("name"))
        Text("Paper2Test × ${inst.optString("name")}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
    }
}
