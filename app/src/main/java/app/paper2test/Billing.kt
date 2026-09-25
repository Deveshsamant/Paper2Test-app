package app.paper2test

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/** One thing on sale: our SKU (pack_1, teacher_m...) and Google Play's product for it (price comes from Google). */
data class PlayItem(val sku: String, val type: String, val productId: String, val basePlanId: String?, val title: String, val blurb: String, val kind: String, val plan: String?,
                    val details: ProductDetails?, val offerToken: String?, val price: String?,
                    /** Price in paise (Google Play's, or the website price when Google Play is not available on this install). */
                    val pricePaise: Long?, val wasPaise: Long?, val credits: Int?, val months: Int?)

/** Google Play Billing for plans and paper packs. Google takes the payment; our server checks the purchase with
 *  Google and gives the plan / credits to the signed-in account (the same account the website uses), then marks it
 *  used / confirmed with Google. Pending payments (e.g. UPI still processing) are picked up later on start. */
class PlayBilling(context: Context, private val app: App) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** Called after each purchase is checked: (sku, "granted" | "already" | "pending" | error code). */
    var onResult: ((String, String) -> Unit)? = null
    private var account: String? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.forEach { p -> scope.launch { verify(p) } }
            else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) onResult?.invoke("", "play_${result.responseCode}")
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Connect to Google Play; false when billing is not available (e.g. an install that did not come from Play). */
    suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) { if (cont.isActive) cont.resume(r.responseCode == BillingClient.BillingResponseCode.OK) }
                override fun onBillingServiceDisconnected() { if (cont.isActive) cont.resume(false) }
            })
        }
    }

    /** Our plans / packs with Google Play prices. Items not (yet) on Google Play come back with price = null. */
    suspend fun items(): Pair<Boolean, List<PlayItem>> {
        val cfg = app.api.get("/plans/play")
        account = cfg.optString("account").ifBlank { null }
        val arr = cfg.getJSONArray("products")
        val raw = (0 until arr.length()).map { arr.getJSONObject(it) }
        val ok = cfg.optBoolean("enabled") && connect()
        val details = if (ok) query(raw.filter { it.optString("type") == "inapp" }.map { it.optString("product_id") }.distinct(), BillingClient.ProductType.INAPP) +
            query(raw.filter { it.optString("type") == "subs" }.map { it.optString("product_id") }.distinct(), BillingClient.ProductType.SUBS) else emptyList()
        return ok to raw.map { j -> item(j, details.firstOrNull { it.productId == j.optString("product_id") }) }
    }

    private fun item(j: JSONObject, d: ProductDetails?): PlayItem {
        var token: String? = null; var price: String? = null; var micros: Long? = null
        if (d != null && j.optString("type") == "subs") {
            val offer = d.subscriptionOfferDetails?.firstOrNull { it.basePlanId == j.optString("base_plan_id") && it.offerId == null }
            val phase = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()
            token = offer?.offerToken; price = phase?.formattedPrice; micros = phase?.priceAmountMicros
        } else if (d != null) {
            val o = d.oneTimePurchaseOfferDetailsList?.firstOrNull()
            token = o?.offerToken; price = o?.formattedPrice ?: d.oneTimePurchaseOfferDetails?.formattedPrice
            micros = o?.priceAmountMicros ?: d.oneTimePurchaseOfferDetails?.priceAmountMicros
        }
        // Not from Google Play (e.g. a test install): show the website price, but buying stays off (no product details).
        val webPaise = j.optLong("price_paise").takeIf { it > 0 }
        val paise = micros?.let { it / 10_000 } ?: webPaise
        if (price == null && webPaise != null) price = rupees(webPaise)
        val was = j.optLong("original_price_paise").takeIf { it > 0 && paise != null && it > paise }
        return PlayItem(j.optString("sku"), j.optString("type"), j.optString("product_id"), j.optString("base_plan_id").ifBlank { null }, j.optString("title"), j.optString("blurb"),
            j.optString("kind"), j.optString("plan").ifBlank { null }, d, token, price, paise, was,
            j.optInt("credits").takeIf { it > 0 }, j.optInt("months").takeIf { it > 0 })
    }

    private suspend fun query(ids: List<String>, type: String): List<ProductDetails> {
        if (ids.isEmpty()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder().setProductList(ids.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(type).build() }).build()
        return suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { r, res -> if (cont.isActive) cont.resume(if (r.responseCode == BillingClient.BillingResponseCode.OK) res.productDetailsList else emptyList()) }
        }
    }

    /** Open Google Play's payment sheet for this item. */
    fun buy(activity: Activity, item: PlayItem): Boolean {
        val d = item.details ?: return false
        val p = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).apply { item.offerToken?.let { setOfferToken(it) } }.build()
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(p)).apply { account?.let { setObfuscatedAccountId(it) } }.build()
        return client.launchBillingFlow(activity, flow).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** Send a purchase to our server, which checks it with Google and gives the plan / credits. */
    private suspend fun verify(p: Purchase) {
        val productId = p.products.firstOrNull() ?: return
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED && p.purchaseState != Purchase.PurchaseState.PENDING) return
        val status = try { app.api.post("/plans/play/verify", JSONObject().put("token", p.purchaseToken).put("product_id", productId)).optString("status", "granted") }
            catch (e: ApiException) { e.code } catch (e: Exception) { "network" }
        onResult?.invoke(productId, status)
    }

    /** On start: purchases Google knows about that our server may not have seen yet (app closed mid-payment, UPI
     *  payment that finished later). The server ignores ones it already handled. */
    suspend fun restore() {
        if (!connect()) return
        for (type in listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)) {
            val list = suspendCancellableCoroutine<List<Purchase>> { cont ->
                client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { r, ps -> if (cont.isActive) cont.resume(if (r.responseCode == BillingClient.BillingResponseCode.OK) ps else emptyList()) }
            }
            // Unconsumed one-time purchases and unconfirmed subscriptions still need our server.
            list.filter { type == BillingClient.ProductType.INAPP || !it.isAcknowledged }.forEach { verify(it) }
        }
    }
}

/** ₹ from paise, without ".00" for whole rupees. */
fun rupees(paise: Long): String = if (paise % 100 == 0L) "₹" + java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(paise / 100)
    else "₹" + String.format(java.util.Locale.US, "%.2f", paise / 100.0)
