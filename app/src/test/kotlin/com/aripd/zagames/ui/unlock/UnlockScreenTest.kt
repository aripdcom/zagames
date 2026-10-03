package com.aripd.zagames.ui.unlock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.zagames.R
import com.aripd.zagames.game
import com.aripd.zagames.platform.GameRegistry
import com.aripd.zagames.platform.Pack
import com.aripd.zagames.platform.RestoreResult
import com.aripd.zagames.setZaContent
import com.aripd.zagames.str
import com.aripd.zagames.ui.hub.HubScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnlockScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val prices = mapOf(Pack.PUZZLE_BOARD.productId to "$1.99", Pack.ALL_PRODUCT_ID to "$3.99")

    @Test
    fun showsPackAndAllWithPrices() {
        val bought = mutableListOf<String>()
        rule.setZaContent {
            UnlockScreen(
                game = game("tavla"),
                games = GameRegistry.games,
                prices = prices,
                onBuy = { bought += it },
                onRestore = {},
                onExit = {},
            )
        }
        rule.onNodeWithText(str(R.string.pack_puzzle_board)).assertIsDisplayed()
        rule.onNodeWithText("$1.99").performClick()
        rule.onNodeWithText("$3.99").performClick()
        assertEquals(listOf(Pack.PUZZLE_BOARD.productId, Pack.ALL_PRODUCT_ID), bought)
    }

    @Test
    fun missingPriceDisablesBuy() {
        rule.setZaContent {
            UnlockScreen(
                game = game("kuyu"),
                games = GameRegistry.games,
                prices = emptyMap(),
                onBuy = {},
                onRestore = {},
                onExit = {},
            )
        }
        rule.onAllNodesWithText(str(R.string.unlock_store_unavailable))[0].assertIsNotEnabled()
    }

    @Test
    fun lockedCardShowsUnlockInsteadOfPlay() {
        rule.setZaContent {
            HubScreen(
                games = listOf(game("blok"), game("tavla")),
                highScores = emptyMap(),
                onPlay = {},
                isUnlocked = { it.pack == null },
            )
        }
        rule.onNodeWithText(str(R.string.play)).assertIsDisplayed()
        rule.onNodeWithText("🔒 " + str(R.string.unlock)).assertIsDisplayed()
    }

    @Test
    fun restoreResultIsShown() {
        rule.setZaContent {
            UnlockScreen(
                game = game("kuyu"),
                games = GameRegistry.games,
                prices = prices,
                onBuy = {},
                onRestore = {},
                onExit = {},
                restoreResult = RestoreResult.NONE,
            )
        }
        val message = str(R.string.unlock_restore_none)
        rule.onNodeWithTag(UNLOCK_LIST_TAG).performScrollToNode(hasText(message))
        rule.onNodeWithText(message).assertIsDisplayed()
    }
}
