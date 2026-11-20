package app.duet.music.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide preferences, persisted on the device (SharedPreferences on Android, java.util.prefs on
 * desktop). Extension-specific settings live in [ExtensionSettings], one store per extension, so
 * extensions never read each other's values (SPEC §3b).
 */
class AppSettings(private val store: Settings = Settings()) {
    private val _accent = MutableStateFlow(store.getLong(KEY_ACCENT, DEFAULT_ACCENT))
    /** Accent color (ARGB), one of [ACCENTS]. */
    val accent: StateFlow<Long> = _accent.asStateFlow()

    fun setAccent(argb: Long) {
        store.putLong(KEY_ACCENT, argb)
        _accent.value = argb
    }

    private val _style = MutableStateFlow(store.getString(KEY_STYLE, "marquee"))
    /** Look of the whole app, a style id (see ui.theme.DuetStyles). */
    val style: StateFlow<String> = _style.asStateFlow()

    fun setStyle(id: String) {
        store.putString(KEY_STYLE, id)
        _style.value = id
    }

    private val _lightMode = MutableStateFlow(store.getBoolean(KEY_LIGHT_MODE, false))
    /** Every style comes in dark and light; true shows the light one. */
    val lightMode: StateFlow<Boolean> = _lightMode.asStateFlow()

    fun setLightMode(on: Boolean) {
        store.putBoolean(KEY_LIGHT_MODE, on)
        _lightMode.value = on
    }

    private val _customLooks = MutableStateFlow(CustomLook.decodeAll(store.getString(KEY_CUSTOM_LOOKS, "")))
    /** Styles people made in the style editor, newest last. */
    val customLooks: StateFlow<List<CustomLook>> = _customLooks.asStateFlow()

    /** Adds [look], or replaces the one with its id. */
    fun saveCustomLook(look: CustomLook) {
        val list = _customLooks.value.filter { it.id != look.id } + look
        store.putString(KEY_CUSTOM_LOOKS, CustomLook.encodeAll(list))
        _customLooks.value = list
    }

    /** Deletes a custom style; if it was in use, the default style comes back. */
    fun deleteCustomLook(id: String) {
        val list = _customLooks.value.filter { it.id != id }
        store.putString(KEY_CUSTOM_LOOKS, CustomLook.encodeAll(list))
        _customLooks.value = list
        if (_style.value == id) setStyle(DEFAULT_STYLE)
    }

    private val _shopName = MutableStateFlow(store.getString(KEY_SHOP_NAME, ""))
    /** The username this phone shares styles under; blank until chosen. */
    val shopName: StateFlow<String> = _shopName.asStateFlow()

    fun setShopName(name: String) {
        store.putString(KEY_SHOP_NAME, name)
        _shopName.value = name
    }

    /**
     * This phone's secret for the shared-styles server: proves the username is ours. Made once, kept on the phone.
     * shortcut: kotlin.random isn't a secure generator; fine while the key only guards deleting shared styles,
     * use a platform SecureRandom if it ever guards more.
     */
    val shopKey: String get() = store.getString(KEY_SHOP_KEY, "").ifBlank {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        (1..40).map { chars[kotlin.random.Random.nextInt(chars.length)] }.joinToString("").also { store.putString(KEY_SHOP_KEY, it) }
    }

    /** Switches to a style with its own highlight colour. */
    fun chooseStyle(id: String, accent: Long) {
        setStyle(id)
        setAccent(accent)
    }

    /**
     * Once per redesign: puts everyone on [styleId] with its own [accent] (old style ids and
     * highlight colours from earlier versions would otherwise mix with the new look).
     */
    fun resetLookOnce(version: Int, styleId: String, accent: Long) {
        if (store.getInt(KEY_LOOK_VERSION, 0) >= version) return
        setStyle(styleId)
        setAccent(accent)
        store.putInt(KEY_LOOK_VERSION, version)
    }

    private val _guideHidden = MutableStateFlow(store.getBoolean(KEY_GUIDE_HIDDEN, false))
    /** The user hid the "Getting started" card on Home. */
    val guideHidden: StateFlow<Boolean> = _guideHidden.asStateFlow()

    fun setGuideHidden(hidden: Boolean) {
        store.putBoolean(KEY_GUIDE_HIDDEN, hidden)
        _guideHidden.value = hidden
    }

    /** On/off settings ([Flag]); each is live, so screens and players follow a change at once. */
    private val flags = Flag.entries.associateWith { MutableStateFlow(store.getBoolean(it.key, it.default)) }

    fun flag(f: Flag): StateFlow<Boolean> = flags.getValue(f)

    fun setFlag(f: Flag, on: Boolean) {
        store.putBoolean(f.key, on)
        flags.getValue(f).value = on
    }

    private val _hiddenSections = MutableStateFlow(
        store.getString(KEY_HIDDEN_SECTIONS, "").split('\n').filter { it.isNotBlank() }.toSet(),
    )
    /** Home sections the user turned off: fixed ids ([HomeSection]) and service shelves ("sourceId:Title"). */
    val hiddenSections: StateFlow<Set<String>> = _hiddenSections.asStateFlow()

    fun setSectionShown(id: String, shown: Boolean) {
        val next = if (shown) _hiddenSections.value - id else _hiddenSections.value + id
        _hiddenSections.value = next
        store.putString(KEY_HIDDEN_SECTIONS, next.joinToString("\n"))
    }

    private val _sleepMinutes = MutableStateFlow(0)
    /** Sleep timer length in minutes; 0 is off. Not saved: a timer is for tonight only. */
    val sleepMinutes: StateFlow<Int> = _sleepMinutes.asStateFlow()
    fun setSleepMinutes(m: Int) { _sleepMinutes.value = m }

    /** Settings > Sound. */
    val sound = SoundSettings(store)

    private val _listenBrainzToken = MutableStateFlow(store.getString(KEY_LISTENBRAINZ, ""))
    /** Settings > Playback > ListenBrainz: the user's own token (listenbrainz.org/settings); empty = off. */
    val listenBrainzToken: StateFlow<String> = _listenBrainzToken.asStateFlow()
    fun setListenBrainzToken(token: String) { store.putString(KEY_LISTENBRAINZ, token.trim()); _listenBrainzToken.value = token.trim() }

    /** A namespaced store for one extension's own settings. */
    fun forExtension(extensionId: String) = ExtensionSettings(store, "ext.$extensionId.")

    companion object {
        /** Accent choices (Settings > Look); the first is the default. */
        val ACCENTS = listOf(0xFFFF4E6AL, 0xFF8F7BFFL, 0xFFFFB84DL, 0xFF5EE0D0L)
        /** The default style's (Cinematic) own accent: gold. */
        const val DEFAULT_ACCENT = 0xFFC9A46AL
        private const val KEY_ACCENT = "app.accent"
        private const val KEY_LISTENBRAINZ = "scrobble.listenbrainz.token"
        const val KEY_STYLE = "app.style"
        private const val KEY_CUSTOM_LOOKS = "app.customLooks"
        private const val KEY_LIGHT_MODE = "app.lightMode"
        private const val KEY_SHOP_NAME = "shop.username"
        private const val KEY_SHOP_KEY = "shop.key"
        /** The first built-in style (ui.theme.DuetStyles). */
        const val DEFAULT_STYLE = "studio"
        private const val KEY_LOOK_VERSION = "app.lookVersion"
        private const val KEY_GUIDE_HIDDEN = "app.guideHidden"
        private const val KEY_HIDDEN_SECTIONS = "home.hiddenSections"
    }
}

/** Home's own sections (service shelves are listed by their titles). */
enum class HomeSection(val id: String, val title: String) {
    QuickPicks("quick_picks", "Quick picks"),
    JumpBackIn("jump_back_in", "Jump back in"),
    Saved("saved", "Saved on this phone"),
    Recent("recent", "Recently played"),
    Radio("radio", "Mixes"),
}

/** App-wide on/off settings, with the words Settings shows for them. */
enum class Flag(val key: String, val default: Boolean, val title: String, val detail: String) {
    TutorialSeen("ui.tutorialSeen", false, "Tutorial seen", "The first-launch tour was finished or skipped"),
    SkipSilence("play.skipSilence", false, "Skip silence", "Jump over quiet parts at the start and end of songs"),
    PauseOnUnplug("play.pauseOnUnplug", true, "Pause when headphones disconnect", "Stops the music instead of playing it out loud"),
    KeepPlayingInBackground("play.keepPlaying.v2", false, "Keep playing after closing the app", "Off: swiping Duet away from recent apps stops the music"),
    LyricsButton("player.lyricsButton", true, "Lyrics button in the player", "Shows the Lyrics button on the full player"),
    SourceLine("player.sourceLine", true, "Source and quality in the player", "The line that says which service plays the song and how well"),
    AutoPlayRadioInCar("auto.playFavouriteRadio", false, "Play a favourite station in the car", "When Android Auto connects and nothing is playing, start the favourite station you listened to last"),
    DownloadWifiOnly("downloads.wifiOnly", true, "Download on Wi-Fi only", "Saves your mobile data"),
}

class ExtensionSettings(private val store: Settings, private val prefix: String) {
    fun getBoolean(key: String, default: Boolean) = store.getBoolean(prefix + key, default)
    fun putBoolean(key: String, value: Boolean) = store.putBoolean(prefix + key, value)
}
