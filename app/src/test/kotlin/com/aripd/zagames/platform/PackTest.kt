package com.aripd.zagames.platform

import com.aripd.zagames.game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ücretli katmanın paket dağılımı ve sahiplik kuralı. */
class PackTest {

    @Test
    fun freeGamesAreTheAgreedSix() {
        val free = GameRegistry.games.filter { it.pack == null }.map { it.id }.toSet()
        assertEquals(setOf("blok", "snake", "2048", "mines", "besharf", "vergici"), free)
    }

    @Test
    fun packsHoldTheAgreedGames() {
        fun ids(pack: Pack) = GameRegistry.games.filter { it.pack == pack }.map { it.id }.toSet()
        assertEquals(setOf("kiskac", "turetme", "dizgi"), ids(Pack.WORD))
        assertEquals(setOf("sudoku", "kakuro", "tavla", "toplam"), ids(Pack.PUZZLE_BOARD))
        assertEquals(setOf("kuyu", "gecit", "balkon", "viraj", "filo", "raket", "tuse"), ids(Pack.ARCADE))
        assertEquals(setOf("ucurtma", "dalgic", "bostan", "sincap", "cekirge", "cici"), ids(Pack.ADVENTURE))
    }

    @Test
    fun productIdsAreUnique() {
        assertEquals(Pack.productIds.size, Pack.productIds.toSet().size)
    }

    @Test
    fun ownershipRules() {
        assertTrue("ücretsiz oyun hep açık", game("blok").isUnlocked(emptySet()))
        assertFalse(game("tavla").isUnlocked(emptySet()))
        assertTrue(game("tavla").isUnlocked(setOf(Pack.PUZZLE_BOARD.productId)))
        assertFalse("başka paket açmaz", game("tavla").isUnlocked(setOf(Pack.WORD.productId)))
        assertTrue("Tümü her paketi açar", GameRegistry.games.all { it.isUnlocked(setOf(Pack.ALL_PRODUCT_ID)) })
    }

    @Test
    fun allUnlockedHasNoLock() {
        assertFalse(AllUnlocked.enabled)
    }
}
