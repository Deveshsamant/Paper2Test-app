package app.paper2test.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import java.io.File

private const val MAX_PX = 2000
private const val PAGE_LIMIT = 80

/** Where the pages come from. Images are uploaded in the order picked; a PDF is rendered page by page on the phone. */
private sealed class Source {
    abstract val pageCount: Int
    data class Images(val uris: List<Uri>) : Source() { override val pageCount get() = uris.size }
    data class Pdf(val file: File, val name: String, override val pageCount: Int) : Source()
}

/** Downscale to <=2000 px and JPEG-encode (same as the website) so uploads stay small and free-tier friendly. */
private fun encodeBitmap(bmp: Bitmap): ByteArray {
    val scale = minOf(1f, MAX_PX.toFloat() / maxOf(bmp.width, bmp.height))
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    val out = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    if (scaled !== bmp) scaled.recycle()
    bmp.recycle()
    return out
}

private suspend fun encodeImage(ctx: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_PX + 400) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })!! }
    encodeBitmap(bmp)
}

/** PdfRenderer draws onto a transparent bitmap, so paint it white first or the JPEG comes out black. */
private fun renderPdfPage(page: PdfRenderer.Page): ByteArray {
    val scale = MAX_PX.toFloat() / maxOf(page.width, page.height)
    val bmp = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.WHITE)
    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    return encodeBitmap(bmp)
}

/** Copy the picked PDF into the cache (content URIs from cloud providers are not always seekable) and count its pages. */
private suspend fun importPdf(ctx: Context, uri: Uri): Source.Pdf = withContext(Dispatchers.IO) {
    val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "paper.pdf"
    val file = File(ctx.cacheDir, "upload.pdf")
    ctx.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
    val pages = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd -> PdfRenderer(pfd).use { it.pageCount } }
    Source.Pdf(file, name, pages)
}

/** Camera scan, gallery photos or a PDF -> pages uploaded to the background pipeline -> share code. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = App.of(ctx)
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60") }
    var instructions by remember { mutableStateOf("") }
    var plus by remember { mutableStateOf("1") }
    var minus by remember { mutableStateOf("0.25") }
    var source by remember { mutableStateOf<Source?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var paperId by remember { mutableStateOf<String?>(null) }

    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uris = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages?.map { it.imageUri } ?: emptyList()
            if (uris.isNotEmpty()) source = Source.Images(uris)
        }
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PAGE_LIMIT)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val prev = (source as? Source.Images)?.uris ?: emptyList()   // a second pick appends, so long papers can be chosen in batches
        source = Source.Images((prev + uris).take(PAGE_LIMIT))
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = "Reading PDF…"
        scope.launch {
            try { source = importPdf(ctx, uri); status = null } catch (e: Exception) { status = "Failed: could not read that PDF (${e.message})" }
        }
    }
    fun openScanner() {
        val opts = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(PAGE_LIMIT)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        scope.launch {
            try { val intent = GmsDocumentScanning.getClient(opts).getStartScanIntent(ctx as Activity).await(); scanner.launch(IntentSenderRequest.Builder(intent).build()) }
            catch (e: Exception) { status = "Scanner unavailable: ${e.message}" }
        }
    }
    fun upload() {
        val src = source ?: return
        busy = true; status = "Creating paper…"; progress = 0f
        scope.launch {
            try {
                val defaultTitle = (src as? Source.Pdf)?.name?.removeSuffix(".pdf") ?: "Scanned paper"
                val paper = app.api.post("/papers", JSONObject().put("title", title.ifBlank { defaultTitle }).put("page_count", src.pageCount)).getJSONObject("paper")
                val id = paper.getString("id"); paperId = id
                suspend fun send(i: Int, jpeg: ByteArray) {
                    status = "Uploading page ${i + 1} of ${src.pageCount}…"
                    app.api.post("/papers/$id/pages/${i + 1}/upload", JSONObject().put("kind", "image").put("mime", "image/jpeg").put("data_b64", Base64.encodeToString(jpeg, Base64.NO_WRAP)))
                    progress = (i + 1f) / src.pageCount
                }
                when (src) {
                    is Source.Images -> src.uris.forEachIndexed { i, uri -> send(i, encodeImage(ctx, uri)) }
                    is Source.Pdf -> withContext(Dispatchers.IO) {
                        ParcelFileDescriptor.open(src.file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                            PdfRenderer(pfd).use { r -> for (i in 0 until r.pageCount) { val jpeg = r.openPage(i).use { renderPdfPage(it) }; send(i, jpeg) } }
                        }
                    }
                }
                status = "Starting extraction…"
                val marking = JSONObject().put("default", JSONObject().put("correct", plus.toDoubleOrNull() ?: 1.0).put("wrong", minus.toDoubleOrNull() ?: 0.25))
                result = app.api.post("/papers/$id/process", JSONObject().put("auto_test", JSONObject().put("duration_min", duration.toIntOrNull() ?: 60).put("marking", marking)).put("link_now", true).apply { if (instructions.isNotBlank()) put("instructions", instructions.trim()) })
                status = null
            } catch (e: ApiException) {
                status = when (e.code) {
                    "plan_limit" -> "Failed: your plan's paper limit is used up — open Pricing on the website to buy a pack or plan."
                    "page_limit" -> "Failed: this paper has more pages than your plan allows (max ${e.body.optInt("max")}; paid plans allow 80)."
                    else -> "Failed: ${e.code}"
                }
            } catch (e: Exception) { status = "Failed: ${e.message}" }
            finally { busy = false }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Create a test") }, navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { pad ->
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(duration, { duration = it.filter { c -> c.isDigit() }.take(3) }, Modifier.weight(1.2f), label = { Text("Minutes") }, singleLine = true)
                    OutlinedTextField(plus, { plus = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, Modifier.weight(1f), label = { Text("+ correct") }, singleLine = true)
                    OutlinedTextField(minus, { minus = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, Modifier.weight(1f), label = { Text("− wrong") }, singleLine = true)
                }
                // Optional instructions the AI follows while reading ("only questions 1-50", "skip the Hindi part").
                OutlinedTextField(instructions, { instructions = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Instructions for the AI (optional)") }, placeholder = { Text("e.g. Only questions 1-50 · Skip the Hindi part") }, maxLines = 3)
                Text("Add the question paper", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openScanner() }, Modifier.weight(1f), enabled = !busy) { Text("Camera") }
                    OutlinedButton(onClick = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), enabled = !busy) { Text("Photos") }
                    OutlinedButton(onClick = { pdf.launch(arrayOf("application/pdf")) }, Modifier.weight(1f), enabled = !busy) { Text("PDF") }
                }
                when (val s = source) {
                    null -> Text("Scan with the camera, pick photos of every page from the gallery, or upload the PDF (up to $PAGE_LIMIT pages). Add the answer-key page too if you have it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is Source.Images -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.pageCount} page photo(s) ready — tap Photos again to add more.", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { source = null }, enabled = !busy) { Text("Clear") }
                    }
                    is Source.Pdf -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.name} — ${s.pageCount} page(s)", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { source = null }, enabled = !busy) { Text("Clear") }
                    }
                }
                Button(onClick = { upload() }, Modifier.fillMaxWidth(), enabled = source != null && !busy) { Text(if (busy) "Uploading…" else "Upload & create test link") }
                if (busy) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
                status?.let { Text(it, color = if (it.startsWith("Failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
