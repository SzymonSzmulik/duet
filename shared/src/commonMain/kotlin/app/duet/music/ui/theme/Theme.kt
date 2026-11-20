package app.duet.music.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.duet.music.resources.Res
import app.duet.music.resources.instrumentsans
import app.duet.music.resources.doto
import app.duet.music.resources.figtree
import app.duet.music.resources.manrope
import app.duet.music.resources.archivo
import app.duet.music.resources.spacegrotesk
import app.duet.music.resources.spacemono_bold
import app.duet.music.resources.spacemono_regular
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import org.jetbrains.compose.resources.Font

/** The user-chosen accent (Settings), provided by [DuetTheme]. */
val LocalAccent = staticCompositionLocalOf { Color(0xFFFF4E6A) }

/** The current [DuetStyle], provided by [DuetTheme]. */
val LocalStyle = staticCompositionLocalOf { DuetStyles.first() }

/** The font for titles, provided by [DuetTheme] (a style can use a different one from the body text). */
val LocalTitleFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/** Fonts a style can use, with what Settings calls them. */
enum class StyleFont(val label: String) {
    InstrumentSans("Instrument Sans"), Figtree("Figtree"), Manrope("Manrope"), Archivo("Archivo"),
    SpaceGrotesk("Space Grotesk"), SpaceMono("Space Mono"), Doto("Dot matrix"),
}

/** Home: two-up tiles over shelves, plain lists, or big cards. */
enum class HomeLayout(val label: String) { Tiles("Tiles"), List("List"), Big("Big cards") }

/** What sits behind Now Playing: the blurred cover, the plain ground, or the cover itself filling the screen. */
enum class PlayerBackground(val label: String) { Blur("Blurred"), Ground("Plain"), Cover("Full cover") }

enum class PlayStyle(val label: String) { Icon("Icon"), Outline("Outline"), Filled("Filled"), Glass("Glass") }

enum class SeekStyle(val label: String) { Line("Line"), Bar("Bar"), Dots("Dots") }

/** The mini player's size. */
enum class MiniSize(val label: String) { Slim("Slim bar"), Card("Card"), Large("Large card") }

/** How much room between parts of Home. */
enum class Spacing(val label: String, val gap: Int) { Compact("Compact", 14), Comfy("Comfortable", 22), Airy("Airy", 30) }

/** What the ground has besides the song colour (Look Studio "Background effect"). */
enum class BgEffect(val label: String) {
    Flat("Flat"), Glow("Glow at the top"), Aurora("Aurora"), Blur("Blurred cover"), Grain("Film grain"), Vignette("Dark edges"), Dots("Dot grid")
}

/** How strong the shadow under the player's cover is, in dp. */
enum class CoverShadow(val label: String, val dp: Int) { None("None", 0), Soft("Soft", 12), Deep("Deep", 28) }

/** What Home says at the top. */
enum class HomeHeader(val label: String) { Greeting("Good evening"), Title("Home"), Name("Greeting + your name"), None("No header") }

/** The player's play button size, as a multiple of the usual. */
enum class PlaySize(val label: String, val scale: Float) { Small("Small", 0.76f), Medium("Medium", 1f), Large("Large", 1.24f) }

/** Where the cover sits in the room the player gives it. */
enum class CoverPosition(val label: String) { Top("Higher up"), Center("Middle"), Bottom("Lower down") }

/** What sits under the player's controls. */
enum class PlayerBelow(val label: String) { Icons("Icons"), Lyrics("Lyrics link"), Next("Up next"), Nothing("Nothing") }

/** The mini player's colour: the song's, see-through, or plain. */
enum class MiniColor { Song, Glass, Plain }

/** The playing song's artwork colour, which the ground takes; null when nothing plays. */
val LocalGroundTint = androidx.compose.runtime.compositionLocalOf<Color?> { null }

/** A whole look (Settings > Appearance > Style): colours, corner roundness and type. */
data class DuetStyle(
    val id: String,
    val name: String,
    val isLight: Boolean,
    /** Accent picked along with the style; the user can still change it. */
    val accent: Long,
    val onAccent: Color,
    val ground: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val line: Color,
    val text: Color,
    val textDim: Color,
    val miniPlayerTint: Color,
    val textDimOnTint: Color,
    /** Multiplies every corner radius: 0 is square, 1 the default. */
    val roundness: Float,
    /** What Settings > Appearance > Style calls it ([name] names its highlight colour). */
    val lookName: String = name,
    val font: StyleFont = StyleFont.InstrumentSans,
    val titleFont: StyleFont = font,
    /** Titles in capitals. */
    val capsTitles: Boolean = false,
    /** Titles in a lighter weight. */
    val lightTitles: Boolean = false,
    /** Multiplies text size. */
    val textScale: Float = 1f,
    /** How much of the playing song's colour tints the top of the ground (0 keeps the plain ground). */
    val songWash: Float = 0.8f,
    /** What the ground has besides the song colour: a glow of the highlight from the top edge, and others. */
    val bgEffect: BgEffect = BgEffect.Glow,
    /** The highlight colour also marks the open tab, chosen words and the seek bar. */
    val accentEverywhere: Boolean = false,
    val home: HomeLayout = HomeLayout.Tiles,
    val miniColor: MiniColor = MiniColor.Song,
    /** The mini player has a previous button too. */
    val miniPrevious: Boolean = true,
    /** The tab bar floats as a rounded bar above the bottom edge. */
    val floatingTabs: Boolean = false,
    /** Tab names under the icons. */
    val tabLabels: Boolean = false,
    val playerBackground: PlayerBackground = PlayerBackground.Blur,
    val play: PlayStyle = PlayStyle.Outline,
    val seek: SeekStyle = SeekStyle.Bar,
    /** Now Playing side margins, in dp. */
    val playerMargin: Int = 24,
    val miniSize: MiniSize = MiniSize.Card,
    val spacing: Spacing = Spacing.Comfy,
    /** Now Playing cover width as a share of the screen width (it never outgrows the room it has). */
    val coverSize: Float = 1f,
    /** The All / YT Music / Spotify words on Home and Library. */
    val serviceSwitch: Boolean = true,
    /** The service switch as chips with a dot in each service's colour, not plain words. */
    val serviceChips: Boolean = false,
    /** The mini player has a heart (like) and play only. */
    val miniLike: Boolean = false,
    val coverShadow: CoverShadow = CoverShadow.Soft,
    val homeHeader: HomeHeader = HomeHeader.Greeting,
    /** Multiplies the size of Home's cards. */
    val cardScale: Float = 1f,
    /** The mini player's line of progress along its bottom edge. */
    val miniProgress: Boolean = true,
    val playSize: PlaySize = PlaySize.Medium,
    val coverPosition: CoverPosition = CoverPosition.Center,
    /** Now Playing space between its parts, in dp. */
    val playerGap: Int = 22,
    /** Now Playing title and artist centred instead of on the left. */
    val titleCentred: Boolean = false,
    val playerBelow: PlayerBelow = PlayerBelow.Icons,
    /** Computers only: the sidebar's width in dp, whether it shows names beside its icons, and the player bar's height. */
    val sidebarWidth: Int = 232,
    val sidebarLabels: Boolean = true,
    val barHeight: Int = 92,
    /** In light mode, the dark version this was made from. */
    val dark: DuetStyle? = null,
) {
    /** Colours for pages drawn over artwork (Now Playing, Lyrics), which are always dark; fonts and shapes stay. */
    fun onArt(): DuetStyle = dark ?: this

    /** The light mode of this style: same fonts, shapes and layout on a white ground (D64). */
    fun light(): DuetStyle = if (isLight) this else copy(
        isLight = true, dark = this,
        // A white highlight would vanish on white: it turns near-black, with white on it.
        onAccent = if (accent == 0xFFFFFFFFL) Color.White else onAccent,
        // A pale shade of this style's own ground, so a custom background still shows in light mode.
        ground = androidx.compose.ui.graphics.lerp(Color(0xFFF7F6F4), ground, 0.06f), surface = Color(0xFFEDECEA), surfaceRaised = Color(0xFFFFFFFF), line = Color(0xFFE0DFDC),
        text = Color(0xFF111113), textDim = Color(0xFF6E6E73), textDimOnTint = Color(0xFF55555A),
        miniPlayerTint = if (miniColor == MiniColor.Song) Color(0xFFEDECEA) else Color(0xB8FFFFFF),
    )
}

// Every style runs the same screens; only the look differs (D64, D65). The three built-in styles are the owner's
// own Look Studio looks; people make more in the style editor (ui/settings/StyleEditor.kt).
val DuetStyles = listOf(
    // The owner's look (2026-11-19, D63): Instrument Sans in bold, a song-colour ground with a glow of the highlight
    // at the top, 18px corners, blurred-cover player, big outlined play button.
    DuetStyle(
        "studio", "White", isLight = false, accent = 0xFFFFFFFF, onAccent = Color(0xFF000000),
        ground = Color(0xFF140A12), surface = Color(0xFF241B23), surfaceRaised = Color(0xFF30272E), line = Color(0xFF3A3139),
        miniPlayerTint = Color(0xFF241B23), text = Color(0xFFF4F4F5), textDim = Color(0xFF9A9AA2), textDimOnTint = Color(0xFFB4B4BC),
        roundness = 1.4f, lookName = "Studio", accentEverywhere = true,
    ),
    // The owner's Nothing OS look (2026-11-20): dot-matrix capitals over Space Mono, warm black with a red glow at
    // the top, red on everything that's on, dotted seek bar, floating tab bar.
    DuetStyle(
        "nothing", "Red", isLight = false, accent = 0xFFD71921, onAccent = Color(0xFFFFFFFF),
        ground = Color(0xFF0F0C0A), surface = Color(0xFF1E1B19), surfaceRaised = Color(0xFF2A2725), line = Color(0x1FFFFFFF),
        miniPlayerTint = Color(0x9E1E1E22), text = Color(0xFFF4F4F5), textDim = Color(0xFF9A9AA2), textDimOnTint = Color(0xFFB4B4BC),
        roundness = 1.4f, lookName = "Nothing", font = StyleFont.SpaceMono, titleFont = StyleFont.Doto, capsTitles = true,
        textScale = 0.92f, songWash = 0f, accentEverywhere = true, home = HomeLayout.List, miniColor = MiniColor.Glass, floatingTabs = true,
        playerBackground = PlayerBackground.Ground, play = PlayStyle.Filled, seek = SeekStyle.Dots, playerMargin = 12,
    ),
    // The owner's Apple Music upgrade (2026-11-20): song colour, big cards, a see-through floating tab bar with
    // names, blurred player with a plain play icon and a thin line.
    DuetStyle(
        "airy", "White", isLight = false, accent = 0xFFFFFFFF, onAccent = Color(0xFF000000),
        ground = Color(0xFF140A12), surface = Color(0xFF241B23), surfaceRaised = Color(0xFF30272E), line = Color(0x1FFFFFFF),
        miniPlayerTint = Color(0x9E1E1E22), text = Color(0xFFF4F4F5), textDim = Color(0xFF9A9AA2), textDimOnTint = Color(0xFFB4B4BC),
        roundness = 0.75f, lookName = "Airy", songWash = 0.5f, accentEverywhere = true, home = HomeLayout.Big,
        miniColor = MiniColor.Glass, miniPrevious = false, floatingTabs = true, tabLabels = true, play = PlayStyle.Icon, seek = SeekStyle.Line,
    ),
)

/**
 * Starting points in the style editor: the three built-in styles plus four more looks (Spotify-, YouTube Music-,
 * mono- and glass-like) that aren't in the Settings list (D67). Each takes the highlight everywhere, like the owner's.
 */
val StyleTemplates = DuetStyles + listOf(
    DuetStyle(
        "shelf", "Green", isLight = false, accent = 0xFF1DB954, onAccent = Color(0xFF000000),
        ground = Color(0xFF121214), surface = Color(0xFF1F1F22), surfaceRaised = Color(0xFF2A2A2E), line = Color(0x1FFFFFFF),
        miniPlayerTint = Color(0xFF1F1F22), text = Color(0xFFF4F4F5), textDim = Color(0xFF9A9AA2), textDimOnTint = Color(0xFFB4B4BC),
        roundness = 0.3f, lookName = "Shelf", font = StyleFont.Figtree, songWash = 0.3f, accentEverywhere = true, tabLabels = true,
        playerBackground = PlayerBackground.Ground, play = PlayStyle.Filled, seek = SeekStyle.Line, spacing = Spacing.Compact, coverSize = 0.86f,
    ),
    DuetStyle(
        "night", "White", isLight = false, accent = 0xFFFFFFFF, onAccent = Color(0xFF000000),
        ground = Color(0xFF000000), surface = Color(0xFF161616), surfaceRaised = Color(0xFF222222), line = Color(0x1FFFFFFF),
        miniPlayerTint = Color(0xFF171717), text = Color(0xFFF4F4F5), textDim = Color(0xFF9A9AA2), textDimOnTint = Color(0xFFB4B4BC),
        roundness = 0.6f, lookName = "Night", font = StyleFont.Archivo, songWash = 0f, accentEverywhere = true, home = HomeLayout.List,
        miniColor = MiniColor.Plain, miniSize = MiniSize.Slim, miniPrevious = false, tabLabels = true, playerBackground = PlayerBackground.Cover,
        play = PlayStyle.Icon, seek = SeekStyle.Line,
    ),
    DuetStyle(
        "mono", "White", isLight = false, accent = 0xFFFFFFFF, onAccent = Color(0xFF000000),
        ground = Color(0xFF000000), surface = Color(0xFF161616), surfaceRaised = Color(0xFF222222), line = Color(0x2EFFFFFF),
        miniPlayerTint = Color(0xFF171717), text = Color(0xFFF4F4F5), textDim = Color(0xFF9A9AA2), textDimOnTint = Color(0xFFB4B4BC),
        roundness = 0f, lookName = "Mono", font = StyleFont.SpaceGrotesk, capsTitles = true, songWash = 0f, bgEffect = BgEffect.Flat, accentEverywhere = true,
        home = HomeLayout.List, miniColor = MiniColor.Plain, miniSize = MiniSize.Slim, miniPrevious = false, playerBackground = PlayerBackground.Ground,
        seek = SeekStyle.Line, spacing = Spacing.Compact, coverSize = 0.7f, serviceSwitch = false,
    ),
    DuetStyle(
        "glass", "White", isLight = false, accent = 0xFFFFFFFF, onAccent = Color(0xFF000000),
        ground = Color(0xFF140A12), surface = Color(0xFF241B23), surfaceRaised = Color(0xFF30272E), line = Color(0x1FFFFFFF),
        miniPlayerTint = Color(0x9E1E1E22), text = Color(0xFFF4F4F5), textDim = Color(0xFF9A9AA2), textDimOnTint = Color(0xFFB4B4BC),
        roundness = 1.1f, lookName = "Glass", font = StyleFont.Manrope, lightTitles = true, songWash = 0.7f, accentEverywhere = true,
        home = HomeLayout.Big, miniColor = MiniColor.Glass, miniSize = MiniSize.Large, floatingTabs = true, play = PlayStyle.Glass,
        spacing = Spacing.Airy,
    ),
)

/** Highlight colours offered in Settings and the style editor (the Look Studio swatches). */
val HighlightChoices = listOf(
    0xFFFFFFFFL to "White", 0xFFD71921L to "Red", 0xFF1DB954L to "Green", 0xFFFA2D48L to "Pink",
    0xFFFF5A36L to "Orange", 0xFF7C6CFFL to "Violet", 0xFF38BDF8L to "Blue", 0xFFF5C542L to "Gold",
)

/** Background colours a style can sit on (the Look Studio backgrounds, dark ones). */
private val Grounds = listOf(
    "song" to ("Song tint" to 0xFF140A12), "warm" to ("Warm black" to 0xFF0F0C0A), "charcoal" to ("Charcoal" to 0xFF121214),
    "black" to ("Black" to 0xFF000000), "graphite" to ("Graphite" to 0xFF1C1D20), "navy" to ("Midnight" to 0xFF0D1020), "slate" to ("Slate blue" to 0xFF141B26),
    "forest" to ("Deep green" to 0xFF0B1712), "wine" to ("Wine" to 0xFF1A0A10),
)

/**
 * One setting of a look, in the order the style editor walks through them. Each stores an option's value in a
 * custom style's changes; [hint] is what the guide says on that step.
 */
enum class LookKnob(val label: String, val options: List<Pair<String, String>>) {
    Ground("Background", Grounds.map { it.first to it.second.first }),
    Wash("Song colour", listOf("0" to "None", "0.3" to "A little", "0.6" to "Some", "0.85" to "Lots")),
    Effect("Background effect", BgEffect.entries.map { it.name to it.label }),
    Font("Font", StyleFont.entries.filter { it != StyleFont.Doto }.map { it.name to it.label }),
    TitleFont("Title font", listOf("same" to "Same as text") + StyleFont.entries.map { it.name to it.label }),
    Titles("Titles", listOf("bold" to "Bold", "light" to "Light", "caps" to "CAPS")),
    TextSize("Text size", listOf("0.92" to "Small", "1" to "Medium", "1.1" to "Large")),
    Corners("Corners", listOf("0" to "Square", "0.6" to "Small", "1.1" to "Round", "1.6" to "Rounder")),
    Header("Header", HomeHeader.entries.map { it.name to it.label }),
    Home("Home", HomeLayout.entries.map { it.name to it.label }),
    CardSize("Card size", listOf("0.8" to "Small", "1" to "Medium", "1.25" to "Large")),
    Shadow("Cover shadows", CoverShadow.entries.map { it.name to it.label }),
    SpacingKnob("Spacing", Spacing.entries.map { it.name to it.label }),
    Services("Service switch", listOf("on" to "Plain words", "chips" to "Chips with dots", "off" to "Hide")),
    MiniSizeKnob("Bar size", MiniSize.entries.map { it.name to it.label }),
    MiniBar("Now-playing bar", listOf(MiniColor.Song.name to "Song colour", MiniColor.Glass.name to "See-through", MiniColor.Plain.name to "Plain")),
    MiniProgress("Progress line", listOf("on" to "Line", "off" to "None")),
    MiniButtons("Bar buttons", listOf("all" to "Previous, play, next", "playnext" to "Play + next", "like" to "Like + play")),
    Tabs("Tab bar", listOf("flat" to "Flat", "float" to "Floating")),
    TabNames("Tab names", listOf("off" to "Icons only", "on" to "Icons + words")),
    Player("Player", PlayerBackground.entries.map { it.name to it.label }),
    Cover("Cover size", listOf("0.7" to "Small", "0.86" to "Medium", "1" to "Edge to edge")),
    Play("Play button", PlayStyle.entries.map { it.name to it.label }),
    PlayButtonSize("Play button size", PlaySize.entries.map { it.name to it.label }),
    CoverPos("Cover position", CoverPosition.entries.map { it.name to it.label }),
    PlayerGap("Spacing between parts", listOf("12" to "Tight", "22" to "Normal", "32" to "Spread out")),
    SideMargins("Side margins", listOf("12" to "Narrow", "24" to "Normal", "36" to "Wide")),
    TitleAlign("Title alignment", listOf("left" to "Left", "center" to "Centred")),
    Below("Under the controls", PlayerBelow.entries.map { it.name to it.label }),
    // Computers only (the editor shows these on desktop; phones ignore them).
    SidebarWidth("Sidebar width", listOf("190" to "Narrow", "232" to "Normal", "280" to "Wide")),
    SidebarNames("Sidebar names", listOf("on" to "Icons + words", "off" to "Icons only")),
    BarHeight("Player bar height", listOf("76" to "Compact", "92" to "Normal", "112" to "Tall")),
    Seek("Seek bar", SeekStyle.entries.map { it.name to it.label }),
    ;

    /** This setting's current option in [style]. */
    fun valueIn(style: DuetStyle): String = when (this) {
        Ground -> Grounds.firstOrNull { it.second.second == style.ground.toArgbLong() }?.first ?: Grounds.first().first
        Wash -> options.minBy { kotlin.math.abs(it.first.toFloat() - style.songWash) }.first
        Effect -> style.bgEffect.name
        Font -> style.font.name
        TitleFont -> if (style.titleFont == style.font) "same" else style.titleFont.name
        Shadow -> style.coverShadow.name
        Header -> style.homeHeader.name
        CardSize -> options.minBy { kotlin.math.abs(it.first.toFloat() - style.cardScale) }.first
        MiniProgress -> if (style.miniProgress) "on" else "off"
        PlayButtonSize -> style.playSize.name
        CoverPos -> style.coverPosition.name
        PlayerGap -> options.minBy { kotlin.math.abs(it.first.toInt() - style.playerGap) }.first
        SideMargins -> options.minBy { kotlin.math.abs(it.first.toInt() - style.playerMargin) }.first
        TitleAlign -> if (style.titleCentred) "center" else "left"
        Below -> style.playerBelow.name
        SidebarWidth -> options.minBy { kotlin.math.abs(it.first.toInt() - style.sidebarWidth) }.first
        SidebarNames -> if (style.sidebarLabels) "on" else "off"
        BarHeight -> options.minBy { kotlin.math.abs(it.first.toInt() - style.barHeight) }.first
        Titles -> if (style.capsTitles) "caps" else if (style.lightTitles) "light" else "bold"
        TextSize -> options.minBy { kotlin.math.abs(it.first.toFloat() - style.textScale) }.first
        Corners -> options.minBy { kotlin.math.abs(it.first.toFloat() - style.roundness) }.first
        Home -> style.home.name
        SpacingKnob -> style.spacing.name
        Services -> if (!style.serviceSwitch) "off" else if (style.serviceChips) "chips" else "on"
        Cover -> options.minBy { kotlin.math.abs(it.first.toFloat() - style.coverSize) }.first
        MiniSizeKnob -> style.miniSize.name
        MiniBar -> style.miniColor.name
        MiniButtons -> if (style.miniLike) "like" else if (style.miniPrevious) "all" else "playnext"
        Tabs -> if (style.floatingTabs) "float" else "flat"
        TabNames -> if (style.tabLabels) "on" else "off"
        Player -> style.playerBackground.name
        Play -> style.play.name
        Seek -> style.seek.name
    }
}

private fun Color.toArgbLong(): Long = (toArgb().toLong() and 0xFFFFFFFFL)

/** The style with a person's changes on top (unknown values are ignored, so old saves can't break it). */
fun DuetStyle.customised(changes: Map<String, String>): DuetStyle = changes.entries.fold(this) { st, (key, v) ->
    when (LookKnob.entries.firstOrNull { it.name == key }) {
        LookKnob.Ground -> Grounds.firstOrNull { it.first == v }?.second?.second?.let { argb ->
            val c = Color(argb)
            val surface = androidx.compose.ui.graphics.lerp(c, Color.White, 0.09f)
            st.copy(ground = c, surface = surface, surfaceRaised = androidx.compose.ui.graphics.lerp(c, Color.White, 0.15f),
                miniPlayerTint = if (st.miniColor == MiniColor.Glass) st.miniPlayerTint else surface)
        } ?: st
        LookKnob.Wash -> v.toFloatOrNull()?.let { st.copy(songWash = it.coerceIn(0f, 1f)) } ?: st
        LookKnob.Effect -> BgEffect.entries.firstOrNull { it.name == v }?.let { st.copy(bgEffect = it) } ?: st
        // The title font follows the text font until it has been chosen on its own (dot matrix is for titles only).
        LookKnob.Font -> StyleFont.entries.firstOrNull { it.name == v }?.let {
            if (it == StyleFont.Doto) st.copy(titleFont = it) else st.copy(font = it, titleFont = if (st.titleFont == st.font) it else st.titleFont)
        } ?: st
        LookKnob.TitleFont -> if (v == "same") st.copy(titleFont = st.font) else StyleFont.entries.firstOrNull { it.name == v }?.let { st.copy(titleFont = it) } ?: st
        LookKnob.Shadow -> CoverShadow.entries.firstOrNull { it.name == v }?.let { st.copy(coverShadow = it) } ?: st
        LookKnob.Header -> HomeHeader.entries.firstOrNull { it.name == v }?.let { st.copy(homeHeader = it) } ?: st
        LookKnob.CardSize -> v.toFloatOrNull()?.let { st.copy(cardScale = it.coerceIn(0.6f, 1.5f)) } ?: st
        LookKnob.MiniProgress -> st.copy(miniProgress = v == "on")
        LookKnob.PlayButtonSize -> PlaySize.entries.firstOrNull { it.name == v }?.let { st.copy(playSize = it) } ?: st
        LookKnob.CoverPos -> CoverPosition.entries.firstOrNull { it.name == v }?.let { st.copy(coverPosition = it) } ?: st
        LookKnob.PlayerGap -> v.toIntOrNull()?.let { st.copy(playerGap = it.coerceIn(4, 40)) } ?: st
        LookKnob.SideMargins -> v.toIntOrNull()?.let { st.copy(playerMargin = it.coerceIn(0, 48)) } ?: st
        LookKnob.TitleAlign -> st.copy(titleCentred = v == "center")
        LookKnob.Below -> PlayerBelow.entries.firstOrNull { it.name == v }?.let { st.copy(playerBelow = it) } ?: st
        LookKnob.SidebarWidth -> v.toIntOrNull()?.let { st.copy(sidebarWidth = it.coerceIn(140, 360)) } ?: st
        LookKnob.SidebarNames -> st.copy(sidebarLabels = v == "on")
        LookKnob.BarHeight -> v.toIntOrNull()?.let { st.copy(barHeight = it.coerceIn(60, 140)) } ?: st
        LookKnob.Titles -> st.copy(capsTitles = v == "caps", lightTitles = v == "light")
        LookKnob.TextSize -> v.toFloatOrNull()?.let { st.copy(textScale = it.coerceIn(0.8f, 1.2f)) } ?: st
        LookKnob.Corners -> v.toFloatOrNull()?.let { st.copy(roundness = it.coerceIn(0f, 2f)) } ?: st
        LookKnob.Home -> HomeLayout.entries.firstOrNull { it.name == v }?.let { st.copy(home = it) } ?: st
        LookKnob.MiniBar -> MiniColor.entries.firstOrNull { it.name == v }?.let {
            st.copy(miniColor = it, miniPlayerTint = if (it == MiniColor.Glass) Color(0x9E1E1E22) else st.surface)
        } ?: st
        LookKnob.MiniButtons -> st.copy(miniPrevious = v == "all", miniLike = v == "like")
        LookKnob.MiniSizeKnob -> MiniSize.entries.firstOrNull { it.name == v }?.let { st.copy(miniSize = it) } ?: st
        LookKnob.SpacingKnob -> Spacing.entries.firstOrNull { it.name == v }?.let { st.copy(spacing = it) } ?: st
        LookKnob.Services -> st.copy(serviceSwitch = v != "off", serviceChips = v == "chips")
        LookKnob.Cover -> v.toFloatOrNull()?.let { st.copy(coverSize = it.coerceIn(0.5f, 1f)) } ?: st
        LookKnob.Tabs -> st.copy(floatingTabs = v == "float")
        LookKnob.TabNames -> st.copy(tabLabels = v == "on")
        LookKnob.Player -> PlayerBackground.entries.firstOrNull { it.name == v }?.let { st.copy(playerBackground = it) } ?: st
        LookKnob.Play -> PlayStyle.entries.firstOrNull { it.name == v }?.let { st.copy(play = it) } ?: st
        LookKnob.Seek -> SeekStyle.entries.firstOrNull { it.name == v }?.let { st.copy(seek = it) } ?: st
        // Saves from before the background effect: the glow was on or off.
        null -> if (key == "Glow") st.copy(bgEffect = if (v == "on") BgEffect.Glow else BgEffect.Flat) else st
    }
}

fun styleById(id: String): DuetStyle = StyleTemplates.firstOrNull { it.id == id } ?: DuetStyles.first()

/** A person's own style: a built-in one with their changes, highlight colour and name on top. */
fun styleOf(look: app.duet.music.settings.CustomLook): DuetStyle =
    styleById(look.base).customised(look.changes).copy(id = look.id, lookName = look.name, accent = look.accent,
        onAccent = if (isLightArgb(look.accent)) Color.Black else Color.White)

/** A built-in or custom style by id; unknown ids fall back to the default. */
fun styleById(id: String, custom: List<app.duet.music.settings.CustomLook>): DuetStyle =
    custom.firstOrNull { it.id == id }?.let(::styleOf) ?: styleById(id)

private fun isLightArgb(argb: Long): Boolean {
    val c = Color(argb)
    return 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue > 0.67f
}

/**
 * The screen's ground: the playing song's colour strong at the top (80% wash) fading into the ground by 62% of
 * the way down (plain ground when nothing plays), under the style's [BgEffect]: by default a soft glow of the
 * highlight colour from the top edge.
 */
@Composable
fun Modifier.duetGround(): Modifier {
    val style = LocalStyle.current
    val tint = LocalGroundTint.current
    val accent = LocalAccent.current
    val song = tint != null && style.songWash != 0f
    // The blurred cover look is the song colour at full strength.
    val washed = if (!song) this.background(style.ground)
        else this.background(songGradient(if (style.bgEffect == BgEffect.Blur) style.copy(songWash = 1f) else style, tint!!))
    val ink = if (style.isLight) Color.Black else Color.White
    return when (style.bgEffect) {
        BgEffect.Flat, BgEffect.Blur -> washed
        BgEffect.Glow -> washed.drawBehind { topGlow(accent.copy(alpha = 0.25f), 0.5f, 0f) }
        BgEffect.Aurora -> washed.drawBehind {
            topGlow((tint ?: accent).copy(alpha = 0.5f), 0.1f, 0.05f)
            topGlow(accent.copy(alpha = 0.35f), 0.9f, 0.18f)
        }
        BgEffect.Vignette -> washed.drawBehind {
            drawRect(Brush.radialGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = if (style.isLight) 0.12f else 0.55f),
                center = center, radius = size.maxDimension * 0.75f))
        }
        BgEffect.Dots -> washed.drawBehind {
            val step = 18.dp.toPx()
            val w = size.width; val h = size.height
            val points = buildList { var y = step / 2; while (y < h) { var x = step / 2; while (x < w) { add(Offset(x, y)); x += step }; y += step } }
            drawPoints(points, androidx.compose.ui.graphics.PointMode.Points, ink.copy(alpha = 0.12f), 2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        }
        BgEffect.Grain -> washed.drawWithCache {
            val random = kotlin.random.Random(7)
            val points = List(2500) { Offset(random.nextFloat() * size.width, random.nextFloat() * size.height) }
            onDrawBehind { drawPoints(points, androidx.compose.ui.graphics.PointMode.Points, ink.copy(alpha = 0.09f), 1.5f) }
        }
    }
}

/** A soft ellipse of [color] fading out, centred [x] of the way across the top edge ([y] of the way down). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.topGlow(color: Color, x: Float, y: Float) {
    // An ellipse 120% of the width across and 45% of the height down, fading out 70% of the way (the studio's CSS).
    val c = Offset(size.width * x, size.height * y)
    val r = size.width * 1.2f
    scale(scaleX = 1f, scaleY = size.height * 0.45f / r, pivot = c) {
        drawCircle(Brush.radialGradient(0f to color, 0.7f to Color.Transparent, center = c, radius = r), radius = r, center = c)
    }
}

/**
 * A song-colour ground: the song's colour at the top running into a deep (dark mode) or pale (light mode) shade of
 * the same colour at the bottom, so the bottom is never one fixed colour.
 */
fun songGradient(style: DuetStyle, tint: Color): Brush =
    Brush.verticalGradient(0f to songTop(style, tint), 1f to songBottom(style, tint))

private fun songTop(style: DuetStyle, tint: Color): Color =
    if (style.isLight) androidx.compose.ui.graphics.lerp(Color.White, tint, 0.45f * style.songWash + 0.15f)
    else androidx.compose.ui.graphics.lerp(Color.Black, tint, 0.35f + 0.55f * style.songWash)

private fun songBottom(style: DuetStyle, tint: Color): Color =
    if (style.isLight) androidx.compose.ui.graphics.lerp(Color.White, tint, 0.1f)
    else androidx.compose.ui.graphics.lerp(Color.Black, tint, 0.14f)

/** The colour at the bottom of the screen (behind the mini player and tabs), matching [duetGround]. */
@Composable
fun groundBottom(): Color {
    val style = LocalStyle.current
    val tint = LocalGroundTint.current
    return if (tint == null || style.songWash == 0f) style.ground else songBottom(style, tint)
}

/** Rounded corners scaled by the style's roundness. */
@Composable
@ReadOnlyComposable
fun duetShape(radius: Int): RoundedCornerShape = RoundedCornerShape((radius * LocalStyle.current.roundness).dp)

/** Colors from design/tokens.json (`color.*`), per [DuetStyle]. */
object DuetColors {
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalAccent.current
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalStyle.current.onAccent
    val Ground: Color @Composable @ReadOnlyComposable get() = LocalStyle.current.ground
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalStyle.current.surface
    val SurfaceRaised: Color @Composable @ReadOnlyComposable get() = LocalStyle.current.surfaceRaised
    val Line: Color @Composable @ReadOnlyComposable get() = LocalStyle.current.line
    val Text: Color @Composable @ReadOnlyComposable get() = LocalStyle.current.text
    val TextDim: Color @Composable @ReadOnlyComposable get() = LocalStyle.current.textDim
    val MiniPlayerTint: Color @Composable @ReadOnlyComposable get() = LocalStyle.current.miniPlayerTint
    val TextDimOnTint: Color @Composable @ReadOnlyComposable get() = LocalStyle.current.textDimOnTint
    val SourceSpotify = Color(0xFF3ECF7A)
    val SourceYouTubeMusic = Color(0xFFFF6B6B)
}

/** Sizes and radii from design/tokens.json (`size.*`, `radius.*`, `spacing.*`), in dp. */
object DuetDimens {
    const val ScreenPaddingX = 16
    const val MinTouchTarget = 44
    const val MiniPlayerHeight = 58
    const val TabBarHeight = 56
    const val RowArt = 48
    const val QuickTile = 52
    const val ShelfCard = 128
    const val RadiusTile = 4
    const val RadiusCard = 8
    const val RadiusChip = 8
    const val RadiusMiniPlayer = 14
}

/** Instrument Sans: one variable font file, its weight axis set per weight (it tops out at 700). */
@Composable
fun instrumentSans(): FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { w ->
        Font(Res.font.instrumentsans, w, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
            androidx.compose.ui.text.font.FontVariation.weight(minOf(w.weight, 700)),
        ))
    },
)

/** A style's font as a family; Doto has a weight axis, Space Mono comes as regular and bold. */
@Composable
fun fontFamily(font: StyleFont): FontFamily = when (font) {
    StyleFont.InstrumentSans -> instrumentSans()
    StyleFont.SpaceMono -> FontFamily(
        Font(Res.font.spacemono_regular, FontWeight.Normal), Font(Res.font.spacemono_regular, FontWeight.Medium),
        Font(Res.font.spacemono_bold, FontWeight.SemiBold), Font(Res.font.spacemono_bold, FontWeight.Bold), Font(Res.font.spacemono_bold, FontWeight.ExtraBold),
    )
    StyleFont.Doto -> FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { w ->
            Font(Res.font.doto, w, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w.weight)))
        },
    )
    StyleFont.Figtree -> variable(Res.font.figtree, 900)
    StyleFont.Manrope -> variable(Res.font.manrope, 800)
    StyleFont.Archivo -> variable(Res.font.archivo, 900)
    StyleFont.SpaceGrotesk -> variable(Res.font.spacegrotesk, 700)
}

/** A variable font file with its weight axis set per weight, capped at the font's heaviest. */
@Composable
private fun variable(res: org.jetbrains.compose.resources.FontResource, max: Int): FontFamily = FontFamily(
    listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { w ->
        Font(res, w, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(minOf(w.weight, max))))
    },
)

/** Named text styles from design/tokens.json (`type.*`), on top of the theme's font family. */
object DuetType {
    val ScreenTitle @Composable get() = display(30)
    val SectionTitle @Composable get() = display(20, tracking = -0.01f)
    /** Big titles (hero, player). */
    @Composable
    fun Display(size: Int): TextStyle = display(size)

    /** A title's words as the style shows them (capitals in some styles; Compose has no text-transform). */
    @Composable
    @ReadOnlyComposable
    fun titled(text: String): String = if (LocalStyle.current.capsTitles) text.uppercase() else text
    val RowTitle @Composable get() = themed(15, FontWeight.SemiBold)
    val RowSub @Composable get() = themed(13, FontWeight.Normal)
    val Chip @Composable get() = themed(14, FontWeight.SemiBold)

    /** Titles: bold, slightly tightened, close lines. */
    @Composable
    private fun display(size: Int, tracking: Float = -0.02f): TextStyle =
        MaterialTheme.typography.bodyMedium.merge(TextStyle(
            fontFamily = LocalTitleFamily.current,
            fontSize = size.sp, fontWeight = if (LocalStyle.current.lightTitles) FontWeight.Normal else FontWeight.Bold, lineHeight = (size * 1.08).sp,
            letterSpacing = androidx.compose.ui.unit.TextUnit(if (LocalStyle.current.capsTitles) 0.04f else tracking, androidx.compose.ui.unit.TextUnitType.Em),
        ))

    @Composable
    private fun themed(size: Int, weight: FontWeight): TextStyle =
        MaterialTheme.typography.bodyMedium.merge(TextStyle(fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.3).sp))
}

@Composable
fun DuetTheme(accent: Color = Color(0xFFFF4E6A), style: DuetStyle = DuetStyles.first(), content: @Composable () -> Unit) {
    // A white highlight would vanish on a light ground: it shows near-black there.
    val accent = if (style.isLight && accent.red > 0.9f && accent.green > 0.9f && accent.blue > 0.9f) Color(0xFF111113) else accent
    val family = fontFamily(style.font)
    val titleFamily = if (style.titleFont == style.font) family else fontFamily(style.titleFont)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
    val scheme = if (style.isLight) lightColorScheme() else darkColorScheme()
    MaterialTheme(
        colorScheme = scheme.copy(
            primary = accent,
            onPrimary = style.onAccent,
            background = style.ground,
            onBackground = style.text,
            surface = style.ground,
            onSurface = style.text,
            surfaceVariant = style.surface,
            onSurfaceVariant = style.textDim,
            outline = style.line,
        ),
        typography = typography,
        // Sheets, dialogs and menus take the same tight corners as the rest of the app (Material's are 28dp).
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(6.dp),
            large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(12.dp),
        ),
    ) {
        SystemBarIcons(darkIcons = style.isLight)
        // Screens draw on the ground color directly (no Surface), so set the default text/icon color here.
        CompositionLocalProvider(
            LocalAccent provides accent,
            LocalStyle provides style,
            LocalTitleFamily provides titleFamily,
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale * style.textScale),
            LocalContentColor provides style.text,
            LocalTextStyle provides typography.bodyMedium,
            content = content,
        )
    }
}

/** Pages drawn over artwork stay dark in light styles, keeping the style's font and corners. */
@Composable
fun OnArtTheme(content: @Composable () -> Unit) {
    val style = LocalStyle.current.onArt()
    SystemBarIcons(darkIcons = false)
    CompositionLocalProvider(LocalStyle provides style, LocalContentColor provides style.text, content = content)
}

/** Dark status/navigation bar icons on light styles, light icons on dark ones (no-op off Android). */
@Composable
expect fun SystemBarIcons(darkIcons: Boolean)
