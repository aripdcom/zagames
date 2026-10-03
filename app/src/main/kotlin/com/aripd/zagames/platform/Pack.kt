package com.aripd.zagames.platform

import androidx.annotation.StringRes
import com.aripd.zagames.R

/**
 * Ücretli katmanın paketleri. [GameEntry.pack] null olan oyun herkese açıktır.
 *
 * Ürün kimlikleri Play Console'daki uygulama içi ürünlerle (tek seferlik,
 * tüketilmeyen) birebir aynı olmalı ve yayından sonra değiştirilmemeli:
 * eski satın almalar bu kimliklerle gelir.
 */
enum class Pack(val productId: String, @StringRes val labelRes: Int) {
    WORD("pack_word", R.string.pack_word),
    PUZZLE_BOARD("pack_puzzle_board", R.string.pack_puzzle_board),
    ARCADE("pack_arcade", R.string.pack_arcade),
    ADVENTURE("pack_adventure", R.string.pack_adventure),
    ;

    companion object {
        /** Her paketi ve ileride eklenecek bütün oyunları açan ürün. */
        const val ALL_PRODUCT_ID = "pack_all"

        /** Play'den fiyatı ve sahipliği sorulan bütün ürünler. */
        val productIds: List<String> = entries.map { it.productId } + ALL_PRODUCT_ID
    }
}

/** Sahip olunan ürün kimliklerine göre oyun açık mı. */
fun GameEntry.isUnlocked(owned: Set<String>): Boolean {
    val pack = pack ?: return true
    return Pack.ALL_PRODUCT_ID in owned || pack.productId in owned
}
