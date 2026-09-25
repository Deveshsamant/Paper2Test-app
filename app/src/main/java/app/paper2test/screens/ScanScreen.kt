package app.paper2test.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.graphics.Color as UiColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.paper2test.ui.*
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
    var picked by remember { mutableStateOf<String?>(null) } // "scan" | "photos" | "pdf" - highlights the tile
    var status by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var paperId by remember { mutableStateOf<String?>(null) }

    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uris = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages?.map { it.imageUri } ?: emptyList()
            if (uris.isNotEmpty()) { source = Source.Images(uris); picked = "scan" }
        }
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PAGE_LIMIT)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val prev = (source as? Source.Images)?.uris ?: emptyList()   // a second pick appends, so long papers can be chosen in batches
        source = Source.Images((prev + uris).take(PAGE_LIMIT)); picked = "photos"
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = "Reading PDF…"
        scope.launch {
            try { source = importPdf(ctx, uri); picked = "pdf"; status = null } catch (e: Exception) { status = "Failed: could not read that PDF (${e.message})" }
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

    val test = result?.optJSONObject("test")
    Scaffold(
        containerColor = P2T.Canvas,
        topBar = { TopAppBar(title = { Text("Create a test", style = MaterialTheme.typography.titleLarge) }, navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = P2T.Canvas)) },
        bottomBar = {
            if (test == null) Surface(color = UiColor.White, shadowElevation = 12.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    status?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (it.startsWith("Failed") || it.startsWith("Scanner")) MaterialTheme.colorScheme.error else P2T.Ink2) }
                    if (busy) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = P2T.Indigo, trackColor = P2T.Tint2, drawStopIndicator = {})
                    Button(onClick = { upload() }, Modifier.fillMaxWidth().height(52.dp), enabled = source != null && !busy, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.UploadFile, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if (busy) "Uploading…" else "Upload & create test link", fontSize = 16.sp)
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (test != null) {
                GradientCard {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = UiColor.White); Spacer(Modifier.width(8.dp)); Text("Test link created", color = UiColor.White, style = MaterialTheme.typography.titleLarge) }
                    Text(test.optString("code"), color = UiColor.White, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = 8.sp)
                    Text(test.optString("url"), color = UiColor(0xD9FFFFFF), fontSize = 13.sp)
                    val share = "${title.ifBlank { "Mock test" }}\nLink: ${test.optString("url")}\nCode: ${test.optString("code")}"
                    Button(onClick = { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, share), "Share test")) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = UiColor.White, contentColor = P2T.Brand)) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share link")
                    }
                }
                P2TCard {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.HourglassTop, null, tint = P2T.Warn); Spacer(Modifier.width(8.dp)); Text("Reading your paper", style = MaterialTheme.typography.titleMedium) }
                    Text("The AI reads about 4 pages a minute on our server - you can close the app. The link opens by itself when it's done; review or fix questions and add the answer key on the paper page.", color = P2T.Ink2, fontSize = 14.sp)
                    OutlinedButton(onClick = { nav.replace(Screen.Home); nav.go(Screen.Web("/#/paper/$paperId", "Paper")) }, Modifier.fillMaxWidth()) { Text("Review the paper / add answer key") }
                }
            } else {
                StepHeader(1, "Add the question paper")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SourceTile(Icons.Default.DocumentScanner, "Scan", "Camera", picked == "scan", !busy, Modifier.weight(1f)) { openScanner() }
                    SourceTile(Icons.Default.PhotoLibrary, "Photos", "Gallery", picked == "photos", !busy, Modifier.weight(1f)) { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    SourceTile(Icons.Default.PictureAsPdf, "PDF", "Document", picked == "pdf", !busy, Modifier.weight(1f)) { pdf.launch(arrayOf("application/pdf")) }
                }
                source?.let { s ->
                    P2TCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isPdf = s is Source.Pdf
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(if (isPdf) UiColor(0xFFFEF2F2) else P2T.Tint), contentAlignment = Alignment.Center) {
                                Icon(if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image, null, tint = if (isPdf) UiColor(0xFFDC2626) else P2T.Brand)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (s is Source.Pdf) s.name else "${s.pageCount} page photo${if (s.pageCount == 1) "" else "s"}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (s is Source.Pdf) "${s.pageCount} page${if (s.pageCount == 1) "" else "s"}" else "Tap Photos again to add more pages", fontSize = 12.sp, color = P2T.Muted)
                            }
                            TextButton(onClick = { source = null; picked = null }, enabled = !busy) { Text("Clear") }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, Modifier.size(16.dp).padding(top = 2.dp), tint = P2T.Muted); Spacer(Modifier.width(6.dp))
                    Text("Up to $PAGE_LIMIT pages. Add the answer-key page too if you have it.", fontSize = 12.sp, color = P2T.Muted)
                }
                StepHeader(2, "Test details")
                P2TCard {
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Paper title") }, placeholder = { Text("e.g. SSC CGL 2023 Tier-1 Shift 2") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(duration, { duration = it.filter { c -> c.isDigit() }.take(3) }, Modifier.weight(1.1f), label = { Text("Minutes") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(plus, { plus = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, Modifier.weight(1f), label = { Text("+ correct") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = UiColor(0xFFF0FDF4), focusedContainerColor = UiColor(0xFFF0FDF4), unfocusedBorderColor = UiColor(0xFFA7F3D0)))
                        OutlinedTextField(minus, { minus = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, Modifier.weight(1f), label = { Text("− wrong") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = UiColor(0xFFFEF2F2), focusedContainerColor = UiColor(0xFFFEF2F2), unfocusedBorderColor = UiColor(0xFFFECACA)))
                    }
                }
                // Optional instructions the AI follows while reading ("only questions 1-50", "skip the Hindi part").
                P2TCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(UiColor(0xFFEEF2FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = P2T.Indigo) }
                        Spacer(Modifier.width(10.dp))
                        Text("Instructions for the AI", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Pill("Optional", bg = P2T.Tint, fg = P2T.Muted)
                    }
                    OutlinedTextField(instructions, { instructions = it.take(500) }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. Only questions 1-50 · Skip the Hindi part") }, minLines = 2, maxLines = 4, shape = RoundedCornerShape(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Skip the Hindi part", "Only questions 1-50", "Key on last page").forEach { hint ->
                            Text("+ $hint", Modifier.clip(CircleShape).border(1.dp, P2T.Tint3, CircleShape).background(P2T.Tint).clickable {
                                instructions = listOf(instructions.trim(), hint).filter { it.isNotBlank() }.joinToString(" · ").take(500)
                            }.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp, color = P2T.Brand, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StepHeader(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(P2T.Brand), contentAlignment = Alignment.Center) { Text("$n", color = UiColor.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SourceTile(icon: ImageVector, label: String, sub: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).background(if (selected) P2T.Tint else UiColor.White).border(if (selected) 2.dp else 1.dp, if (selected) P2T.Brand else P2T.Line, shape)
        .clickable(enabled = enabled, onClick = onClick).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) P2T.Tint3 else P2T.Tint), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (selected) P2T.Brand else P2T.Ink2) }
        Text(label, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (selected) P2T.Brand else P2T.Ink)
        Text(sub, fontSize = 11.sp, color = P2T.Muted, textAlign = TextAlign.Center)
    }
}
