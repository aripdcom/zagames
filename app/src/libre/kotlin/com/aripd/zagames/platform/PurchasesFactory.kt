package com.aripd.zagames.platform

import android.content.Context

/**
 * Libre dağıtım (GitHub, F-Droid): ödeme kodu ve Play kütüphanesi yok, her
 * oyun açık. Manifestte izin yok sözü bu çeşitte birebir geçerli kalır.
 */
@Suppress("UNUSED_PARAMETER")
fun createPurchases(context: Context): Purchases = AllUnlocked
