package app.paper2test

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.tasks.await

/** Scans a QR with the Play services code scanner (no camera permission handling needed) and returns a test code. */
suspend fun scanTestCode(ctx: Context): String? {
    val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    val raw = runCatching { GmsBarcodeScanning.getClient(ctx, opts).startScan().await().rawValue }.getOrNull() ?: return null
    return Regex("/t/([A-Za-z0-9]{4,10})").find(raw)?.groupValues?.get(1)?.uppercase()
        ?: Regex("^([A-Za-z0-9]{6})$").find(raw.trim())?.groupValues?.get(1)?.uppercase()
}
