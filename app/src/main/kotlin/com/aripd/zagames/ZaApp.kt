package com.aripd.zagames

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.aripd.zagames.ui.common.LocalWordLang
import com.aripd.zagames.ui.common.rememberWordLang
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.aripd.zagames.platform.Changelog
import com.aripd.zagames.platform.GameEntry
import com.aripd.zagames.platform.GameRegistry
import com.aripd.zagames.platform.LocalZaHaptics
import com.aripd.zagames.platform.LocalZaSound
import com.aripd.zagames.platform.ScoreStore
import com.aripd.zagames.platform.SettingsStore
import com.aripd.zagames.platform.SoundPlayer
import com.aripd.zagames.platform.ZaLocale
import com.aripd.zagames.platform.appLocale
import com.aripd.zagames.platform.createPurchases
import com.aripd.zagames.platform.isUnlocked
import com.aripd.zagames.platform.gatedBy
import com.aripd.zagames.ui.about.AboutScreen
import com.aripd.zagames.ui.hub.HubScreen
import com.aripd.zagames.ui.hub.LanguageScreen
import com.aripd.zagames.ui.unlock.UnlockScreen

/**
 * Uygulama kökü: ana menü ile oyunlar arasında geçişi, rekor akışını ve
 * paylaşılan ses çaları yönetir.
 */
@Composable
fun ZaApp() {
    val context = LocalContext.current
    val scoreStore = remember { ScoreStore(context) }
    val settings = remember { SettingsStore(context) }
    val highScores = remember {
        mutableStateMapOf<String, Long>().apply {
            GameRegistry.games.forEach { put(it.id, scoreStore.highScore(it.id)) }
        }
    }
    val lastPlayed = remember {
        mutableStateMapOf<String, Long>().apply {
            GameRegistry.games.forEach { put(it.id, settings.lastPlayed(it.id)) }
        }
    }
    var hubCategory by remember { mutableStateOf(settings.hubCategory) }

    var soundOn by remember { mutableStateOf(settings.soundEnabled) }
    val soundPlayer = remember { SoundPlayer(context) { soundOn } }
    DisposableEffect(Unit) {
        onDispose { soundPlayer.release() }
    }

    // Ücretli katman: libre çeşidinde ve bayrak kapalıyken her oyun açık.
    val purchases = remember { createPurchases(context) }
    DisposableEffect(purchases) {
        onDispose { purchases.release() }
    }
    val owned by purchases.owned.collectAsState()
    val prices by purchases.prices.collectAsState()
    val unlocked: (GameEntry) -> Boolean = { game ->
        !purchases.enabled || game.isUnlocked(owned)
    }

    var hapticsOn by remember { mutableStateOf(settings.hapticsEnabled) }
    val systemHaptics = LocalHapticFeedback.current
    val gatedHaptics = remember(systemHaptics) { systemHaptics.gatedBy { hapticsOn } }

    var currentGameId by rememberSaveable { mutableStateOf<String?>(null) }
    val currentGame = GameRegistry.games.firstOrNull { it.id == currentGameId }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    var unlockGameId by rememberSaveable { mutableStateOf<String?>(null) }
    val unlockGame = GameRegistry.games.firstOrNull { it.id == unlockGameId }

    // Kullanıcının açık dil seçimi ve o an çizilen dil. Seçim uygulanınca
    // etkinlik yeniden oluşur, ikisi de yeni değerle okunur.
    val effectiveLanguage = ZaLocale.normalize(appLocale()) ?: "en"
    val selectedLanguage = remember(effectiveLanguage) { ZaLocale.selected(context) }

    // Güncelleme algısı: ilk kurulumda sürüm sessizce kaydedilir; sonraki bir
    // güncellemede Yenilikler kartı çıkar ve bu sürümden sonra eklenen oyunlar
    // "Yeni" rozeti alır (oturum boyunca).
    val currentCode = remember { Changelog.versionCode(Changelog.installedVersion(context)) }
    val updatedFrom = remember {
        val seen = settings.lastSeenVersionCode
        when {
            seen == 0 -> {
                settings.lastSeenVersionCode = currentCode
                null
            }
            seen < currentCode -> seen
            else -> null
        }
    }
    var showWhatsNew by rememberSaveable { mutableStateOf(updatedFrom != null) }

    BackHandler(enabled = currentGame != null) { currentGameId = null }

    fun openGame(game: GameEntry) {
        val now = System.currentTimeMillis()
        settings.recordPlay(game.id, now)
        lastPlayed[game.id] = now
        currentGameId = game.id
    }

    // Satın alma tamamlanınca açma ekranı kapanır ve oyun doğrudan başlar.
    LaunchedEffect(owned, unlockGameId) {
        val game = unlockGame ?: return@LaunchedEffect
        if (unlocked(game)) {
            unlockGameId = null
            openGame(game)
        }
    }

    // Kelime oyunlarının dili: ekranın derinlerinde klavye sırası, harf büyütme
    // ve taş etiketleri için gerekiyor, tek yerden sağlanır. Oyuncunun seçimi
    // yoksa arayüzün diline uyar; o dilin listesi yoksa İngilizceye düşer.
    // İki kaynağa bağlı: oyuncu arayüz dilini değiştirince de, oyun içindeki
    // seçiciden kelime dilini değiştirince de yeniden okunur. İkincisi eksikti;
    // sözlük yeni dile geçerken klavye eski dilde kalıyordu.
    val wordLang = rememberWordLang(effectiveLanguage, showLanguage)

    CompositionLocalProvider(
        LocalZaSound provides soundPlayer,
        LocalZaHaptics provides gatedHaptics,
        LocalWordLang provides wordLang,
    ) {
        if (currentGame == null && showAbout) {
            AboutScreen(onExit = { showAbout = false })
        } else if (currentGame == null && showLanguage) {
            LanguageScreen(
                selected = selectedLanguage,
                effective = effectiveLanguage,
                onPick = { tag ->
                    showLanguage = false
                    ZaLocale.choose(context, tag)
                },
                onExit = { showLanguage = false },
            )
        } else if (currentGame == null && unlockGame != null) {
            UnlockScreen(
                game = unlockGame,
                games = GameRegistry.games,
                prices = prices,
                onBuy = { productId -> context.findActivity()?.let { purchases.buy(it, productId) } },
                onRestore = { purchases.restore() },
                onExit = { unlockGameId = null },
            )
        } else if (currentGame == null) {
            HubScreen(
                games = GameRegistry.games,
                highScores = highScores,
                lastPlayed = lastPlayed,
                category = hubCategory,
                onCategory = { picked ->
                    hubCategory = picked
                    settings.hubCategory = picked
                },
                onPlay = { game ->
                    if (unlocked(game)) {
                        openGame(game)
                    } else {
                        unlockGameId = game.id
                    }
                },
                isUnlocked = unlocked,
                soundOn = soundOn,
                onToggleSound = {
                    soundOn = !soundOn
                    settings.soundEnabled = soundOn
                },
                hapticsOn = hapticsOn,
                onToggleHaptics = {
                    hapticsOn = !hapticsOn
                    settings.hapticsEnabled = hapticsOn
                },
                onAbout = { showAbout = true },
                languageCode = effectiveLanguage,
                onLanguage = { showLanguage = true },
                whatsNew = if (showWhatsNew) Changelog.latest else null,
                onDismissWhatsNew = {
                    showWhatsNew = false
                    settings.lastSeenVersionCode = currentCode
                },
                newSinceCode = updatedFrom ?: Int.MAX_VALUE,
            )
        } else {
            currentGame.screen(
                highScores[currentGame.id] ?: 0L,
                { score ->
                    scoreStore.submit(currentGame.id, score)
                    if (score > (highScores[currentGame.id] ?: 0L)) {
                        highScores[currentGame.id] = score
                    }
                },
                { currentGameId = null },
            )
        }
    }
}

/** Satın alma akışı Activity ister; Compose bağlamı onu sarmalayabilir. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
