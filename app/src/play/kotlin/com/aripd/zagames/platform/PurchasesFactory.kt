package com.aripd.zagames.platform

import android.content.Context
import com.aripd.zagames.BuildConfig

/**
 * Play dağıtımı. Kilit, `-PzaPaywall=true` ile derlenene kadar kapalıdır:
 * Play Console'da ürünler tanımlanmadan kilit açılırsa fiyat gelmez ve
 * oyunlar açılamaz.
 */
fun createPurchases(context: Context): Purchases =
    if (BuildConfig.PAYWALL) BillingPurchases(context) else AllUnlocked
