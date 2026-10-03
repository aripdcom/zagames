package com.aripd.zagames.platform

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Play Billing ile tek seferlik paket satın alma.
 *
 * Sunucu yok: sahiplik açılışta Play'e sorulur ve çevrimdışı açılış için
 * cihazda saklanır. Kaynak GPL olduğundan bu kilit yumuşaktır; amaç DRM değil,
 * Play'den alan oyuncuya düzgün bir satın alma ve geri yükleme deneyimi
 * (bkz. store/checklist.md, §8).
 */
class BillingPurchases(context: Context) : Purchases, PurchasesUpdatedListener {

    private val prefs = context.applicationContext.getSharedPreferences("za_purchases", Context.MODE_PRIVATE)

    override val enabled = true

    private val ownedState = MutableStateFlow(prefs.getStringSet(KEY_OWNED, null).orEmpty().toSet())
    override val owned: StateFlow<Set<String>> = ownedState.asStateFlow()

    private val priceState = MutableStateFlow<Map<String, String>>(emptyMap())
    override val prices: StateFlow<Map<String, String>> = priceState.asStateFlow()

    @Volatile
    private var details: Map<String, ProductDetails> = emptyMap()

    /**
     * Bağlantı kurulunca koşacak işler ve bağlantı kurulamazsa çağrılacak
     * karşılıkları; Billing geri çağrıları ana iş parçacığında gelir.
     */
    private val pending = mutableListOf<Pair<() -> Unit, () -> Unit>>()
    private var connecting = false

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        whenReady {
            loadPrices()
            queryOwned()
        }
    }

    override fun buy(activity: Activity, productId: String) {
        whenReady {
            val product = details[productId]
            if (product == null) {
                loadPrices()
                return@whenReady
            }
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()),
                )
                .build()
            client.launchBillingFlow(activity, params)
        }
    }

    override fun restore(onResult: (RestoreResult) -> Unit) {
        whenReady(onFail = { onResult(RestoreResult.UNAVAILABLE) }) {
            if (details.isEmpty()) loadPrices()
            queryOwned(onResult)
        }
    }

    override fun release() {
        pending.clear()
        client.endConnection()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> record(purchases.orEmpty(), replace = false)
            // Başka cihazda alınmış ya da yerel kayıt silinmiş: Play'den yeniden oku.
            BillingResponseCode.ITEM_ALREADY_OWNED -> queryOwned()
            else -> Unit
        }
    }

    private fun whenReady(onFail: () -> Unit = {}, action: () -> Unit) {
        if (client.isReady) {
            action()
            return
        }
        pending += action to onFail
        if (connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                val actions = pending.toList()
                pending.clear()
                if (result.responseCode == BillingResponseCode.OK) {
                    actions.forEach { (action, _) -> action() }
                } else {
                    actions.forEach { (_, onFail) -> onFail() }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Bir sonraki buy/restore yeniden bağlanır.
                connecting = false
                val actions = pending.toList()
                pending.clear()
                actions.forEach { (_, onFail) -> onFail() }
            }
        })
    }

    private fun loadPrices() {
        val products = Pack.productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, queried ->
            if (result.responseCode != BillingResponseCode.OK) return@queryProductDetailsAsync
            val list = queried.productDetailsList
            details = list.associateBy { it.productId }
            priceState.value = list
                .mapNotNull { d -> d.oneTimePurchaseOfferDetails?.formattedPrice?.let { d.productId to it } }
                .toMap()
        }
    }

    private fun queryOwned(onResult: (RestoreResult) -> Unit = {}) {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            // Sorgu başarısızsa yerel kayıt korunur: çevrimdışı oyuncu kilitlenmesin.
            if (result.responseCode != BillingResponseCode.OK) {
                onResult(RestoreResult.UNAVAILABLE)
                return@queryPurchasesAsync
            }
            record(purchases, replace = true)
            onResult(if (ownedState.value.isEmpty()) RestoreResult.NONE else RestoreResult.FOUND)
        }
    }

    /**
     * Tamamlanmış satın almaları kaydeder ve onaylar (Play, 3 gün içinde
     * onaylanmayanı iade eder). Bekleyen ödeme (ör. nakit) açmaz; tamamlanınca
     * [onPurchasesUpdated] ya da bir sonraki açılış yakalar. [replace] true ise
     * liste Play'in tam cevabıdır: iade edilen ürün kaydan düşer.
     */
    private fun record(purchases: List<Purchase>, replace: Boolean) {
        val bought = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        bought.filterNot { it.isAcknowledged }.forEach { purchase ->
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { }
        }
        val ids = bought.flatMap { it.products }.toSet()
        val next = if (replace) ids else ownedState.value + ids
        ownedState.value = next
        prefs.edit().putStringSet(KEY_OWNED, next).apply()
    }

    private companion object {
        const val KEY_OWNED = "owned"
    }
}
