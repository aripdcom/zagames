package com.aripd.zagames.ui.unlock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aripd.zagames.R
import com.aripd.zagames.platform.GameEntry
import com.aripd.zagames.platform.Pack
import com.aripd.zagames.platform.RestoreResult
import com.aripd.zagames.platform.zaString
import com.aripd.zagames.ui.common.GameTopBar

/**
 * Kilitli bir oyuna dokunulunca açılan ekran: oyunun paketi ve "Tümü"
 * seçeneği, mağazanın yerel fiyatıyla. Fiyat gelmediyse (mağaza yok, ürün
 * Console'da tanımlı değil) düğme kapalı kalır ve nedenini söyler.
 */
@Composable
fun UnlockScreen(
    game: GameEntry,
    games: List<GameEntry>,
    prices: Map<String, String>,
    onBuy: (productId: String) -> Unit,
    onRestore: () -> Unit,
    onExit: () -> Unit,
    /** Son geri yüklemenin sonucu; null = henüz denenmedi. */
    restoreResult: RestoreResult? = null,
) {
    val pack = game.pack ?: return
    BackHandler { onExit() }
    val packLabel = stringResource(pack.labelRes)
    val packGames = games.filter { it.pack == pack }.map { stringResource(it.titleRes) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        GameTopBar(title = stringResource(R.string.unlock_title), onExit = onExit)
        LazyColumn(
            modifier = Modifier.testTag(UNLOCK_LIST_TAG),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = zaString(R.string.unlock_in_pack_fmt, stringResource(game.titleRes), packLabel),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                OfferCard(
                    title = packLabel,
                    body = packGames.joinToString(", "),
                    price = prices[pack.productId],
                    onBuy = { onBuy(pack.productId) },
                )
            }
            item {
                OfferCard(
                    title = stringResource(R.string.unlock_all),
                    body = stringResource(R.string.unlock_all_desc),
                    price = prices[Pack.ALL_PRODUCT_ID],
                    onBuy = { onBuy(Pack.ALL_PRODUCT_ID) },
                    highlight = true,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.unlock_once_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                TextButton(onClick = onRestore) {
                    Text(stringResource(R.string.unlock_restore))
                }
                if (restoreResult != null) {
                    Text(
                        text = stringResource(
                            when (restoreResult) {
                                RestoreResult.FOUND -> R.string.unlock_restore_found
                                RestoreResult.NONE -> R.string.unlock_restore_none
                                RestoreResult.UNAVAILABLE -> R.string.unlock_store_unavailable
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

/** Testlerin liste düğümünü bulup kaydırması için. */
const val UNLOCK_LIST_TAG = "unlock_list"

@Composable
private fun OfferCard(
    title: String,
    body: String,
    price: String?,
    onBuy: () -> Unit,
    highlight: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (highlight) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Button(onClick = onBuy, enabled = price != null, modifier = Modifier.padding(top = 4.dp)) {
                Text(price ?: stringResource(R.string.unlock_store_unavailable))
            }
        }
    }
}
