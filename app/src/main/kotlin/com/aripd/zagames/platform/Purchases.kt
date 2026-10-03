package com.aripd.zagames.platform

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Satın alma durumu. Uygulamanın geri kalanı yalnız bu arayüzü görür; Play
 * Billing yalnız `play` çeşidinde derlenir (bkz. `src/play`), `libre`
 * çeşidinde ve kilit bayrağı kapalıyken [AllUnlocked] kullanılır.
 */
interface Purchases {
    /** false ise kilit yoktur, her oyun açıktır. */
    val enabled: Boolean

    /** Sahip olunan ürün kimlikleri ([Pack.productId], [Pack.ALL_PRODUCT_ID]). */
    val owned: StateFlow<Set<String>>

    /** Ürün kimliği → mağazanın biçimlediği yerel fiyat; gelmeyen ürün yoktur. */
    val prices: StateFlow<Map<String, String>>

    /** Satın alma akışını başlatır; sonuç [owned] üzerinden gelir. */
    fun buy(activity: Activity, productId: String)

    /**
     * Satın almaları mağazadan yeniden okur (telefon değişince, iadeden sonra).
     * Sonuç oyuncuya gösterilir; aksi halde düğme sessiz kalıyordu (cihaz testi, PR #116).
     */
    fun restore(onResult: (RestoreResult) -> Unit = {})

    fun release()
}

/** Geri yüklemenin oyuncuya söylenen sonucu. */
enum class RestoreResult {
    /** Bu Google hesabında en az bir satın alma bulundu. */
    FOUND,

    /** Mağaza cevap verdi, satın alma yok. */
    NONE,

    /** Mağazaya ulaşılamadı. */
    UNAVAILABLE,
}

/** Kilitsiz dağıtım: her oyun açık, mağazaya hiç bağlanılmaz. */
object AllUnlocked : Purchases {
    override val enabled = false
    override val owned: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override val prices: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    override fun buy(activity: Activity, productId: String) = Unit
    override fun restore(onResult: (RestoreResult) -> Unit) = Unit
    override fun release() = Unit
}
