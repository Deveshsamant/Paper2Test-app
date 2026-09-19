package app.paper2test.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.paper2test.*
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Downscale to <=2000 px and JPEG-encode (same as the website) so uploads stay small and free-tier friendly. */
private suspend fun encodePage(ctx: android.content.Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })!! }
    val scale = minOf(1f, 2000f / maxOf(bmp.width, bmp.height))
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
}

/** Camera -> ML Kit document scanner -> pages uploaded to the background pipeline -> share code. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = App.of(ctx)
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60") }
    var pages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var paperId by remember { mutableStateOf<String?>(null) }

    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) pages = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages?.map { it.imageUri } ?: emptyList()
    }
    fun openScanner() {
        val opts = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(60)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        scope.launch {
            try { val intent = GmsDocumentScanning.getClient(opts).getStartScanIntent(ctx as Activity).await(); scanner.launch(IntentSenderRequest.Builder(intent).build()) }
            catch (e: Exception) { status = "Scanner unavailable: ${e.message}" }
        }
    }
    fun upload() {
        busy = true; status = "Creating paper…"; progress = 0f
        scope.launch {
            try {
                val paper = app.api.post("/papers", JSONObject().put("title", title.ifBlank { "Scanned paper" }).put("page_count", pages.size)).getJSONObject("paper")
                val id = paper.getString("id"); paperId = id
                pages.forEachIndexed { i, uri ->
                    status = "Uploading page ${i + 1} of ${pages.size}…"
                    val b64 = Base64.encodeToString(encodePage(ctx, uri), Base64.NO_WRAP)
                    app.api.post("/papers/$id/pages/${i + 1}/upload", JSONObject().put("kind", "image").put("mime", "image/jpeg").put("data_b64", b64))
                    progress = (i + 1f) / pages.size
                }
                status = "Starting extraction…"
                result = app.api.post("/papers/$id/process", JSONObject().put("auto_test", JSONObject().put("duration_min", duration.toIntOrNull() ?: 60)).put("link_now", true))
                status = null
            } catch (e: ApiException) { status = "Failed: ${e.code}" } catch (e: Exception) { status = "Failed: ${e.message}" }
            finally { busy = false }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Scan a paper") }, navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val test = result?.optJSONObject("test")
            if (test != null) {
                Text("Test link created", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(test.optString("code"), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(test.optString("url"))
                Text("Extraction runs on the server (about 4 pages a minute). The link opens automatically when it is done. You can review or fix questions on the paper page.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                val share = "${title.ifBlank { "Mock test" }}\nLink: ${test.optString("url")}\nCode: ${test.optString("code")}"
                Button(onClick = { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, share), "Share test")) }, Modifier.fillMaxWidth()) { Text("Share link") }
                OutlinedButton(onClick = { nav.replace(Screen.Home); nav.go(Screen.Web("/#/paper/$paperId", "Paper")) }, Modifier.fillMaxWidth()) { Text("Review the paper / add answer key") }
            } else {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Paper title") }, placeholder = { Text("e.g. SSC CGL 2023 Tier-1 Shift 2") }, singleLine = true)
                OutlinedTextField(duration, { duration = it.filter { c -> c.isDigit() }.take(3) }, Modifier.width(200.dp), label = { Text("Test duration (min)") }, singleLine = true)
                Button(onClick = { openScanner() }, Modifier.fillMaxWidth(), enabled = !busy) { Text(if (pages.isEmpty()) "Open camera scanner" else "Re-scan (${pages.size} pages)") }
                Text(if (pages.isEmpty()) "Scan every page of the question paper (and the answer-key page if you have it). Gallery photos work too." else "${pages.size} page(s) ready.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { upload() }, Modifier.fillMaxWidth(), enabled = pages.isNotEmpty() && !busy) { Text(if (busy) "Uploading…" else "Upload & create test link") }
                if (busy) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
                status?.let { Text(it, color = if (it.startsWith("Failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
