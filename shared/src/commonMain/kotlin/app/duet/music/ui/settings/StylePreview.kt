package app.duet.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.domain.Shelf
import app.duet.music.domain.Track
import app.duet.music.player.PlaybackStatus
import app.duet.music.player.PlayerState
import app.duet.music.player.QueueItem
import app.duet.music.ui.components.MiniPlayer
import app.duet.music.ui.components.MiniPlayerUi
import app.duet.music.ui.components.Tab
import app.duet.music.ui.components.TabBar
import app.duet.music.ui.home.HomeScreen
import app.duet.music.ui.home.HomeShortcut
import app.duet.music.ui.home.HomeUi
import app.duet.music.ui.desktop.DesktopPlayerBar
import app.duet.music.ui.desktop.DesktopSidebar
import app.duet.music.ui.desktop.LocalDesktop
import app.duet.music.ui.player.PlayerControls
import app.duet.music.ui.player.StudioPlayer
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.DuetStyle
import app.duet.music.ui.theme.DuetTheme
import app.duet.music.ui.theme.LocalGroundTint
import app.duet.music.ui.theme.OnArtTheme
import app.duet.music.ui.theme.duetGround
import app.duet.music.ui.theme.groundBottom

/** The size of the phone the preview draws, in dp: the real screens are laid out at this size, then shrunk. */
private const val PhoneW = 390
private const val PhoneH = 844
/** The computer window the preview draws, in dp. */
private const val DeskW = 1180
private const val DeskH = 740

/** A song colour for the preview's ground (a pink-purple). */
private val PreviewSong = Color(0xFF8E3B6B)

/**
 * A style's Home and Now Playing side by side, as the app draws them: the real screens with made-up songs, laid
 * out at phone size and shrunk into two frames (the style editor and the shared-style page).
 */
@Composable
internal fun StylePreview(style: DuetStyle, accent: Long, modifier: Modifier) {
    DuetTheme(accent = Color(accent), style = style) {
        CompositionLocalProvider(LocalGroundTint provides PreviewSong) {
            if (LocalDesktop.current) Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Frame(Modifier.fillMaxWidth(), DeskW, DeskH) { DesktopPicture(player = false) }
                Frame(Modifier.fillMaxWidth(), DeskW, DeskH) { DesktopPicture(player = true) }
            } else Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                Frame(Modifier.width(156.dp), PhoneW, PhoneH) { HomePicture() }
                Frame(Modifier.width(156.dp), PhoneW, PhoneH) { PlayerPicture() }
            }
        }
    }
}

/** A frame holding a screen drawn at [w] x [h] dp and scaled to the frame's width; it ignores touches. */
@Composable
private fun Frame(modifier: Modifier, w: Int, h: Int, screen: @Composable () -> Unit) {
    val shape = RoundedCornerShape(if (w > h) 10.dp else 18.dp)
    BoxWithConstraints(modifier.aspectRatio(w.toFloat() / h).clip(shape).duetGround().border(1.dp, DuetColors.Line, shape)) {
        val scale = maxWidth.value / w
        Box(
            Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(w.dp, h.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) },
        ) { screen() }
        // A picture, not a screen: swallow every touch.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
    }
}

private val Songs = listOf(
    Track("ytmusic", "1", "Hallucinate", "Dua Lipa"), Track("ytmusic", "2", "Houdini", "Dua Lipa"), Track("spotify", "3", "Blinding Lights", "The Weeknd"),
    Track("ytmusic", "4", "Levitating", "Dua Lipa"), Track("spotify", "5", "Midnight City", "M83"), Track("ytmusic", "6", "Redbone", "Childish Gambino"),
)

private val SampleHome = HomeUi(
    headline = "Good evening",
    recent = Songs,
    saved = emptyList(),
    shelves = listOf(
        Shelf("ytmusic", "Made for you", collections = listOf(
            Collection("ytmusic", "a", SearchKind.Playlists, "Daily mix 1", "Dua Lipa, The Weeknd"),
            Collection("ytmusic", "b", SearchKind.Playlists, "Late night", "Chill and slow"),
            Collection("ytmusic", "c", SearchKind.Playlists, "Gym", "High energy"),
        )),
        Shelf("ytmusic", "New releases", songs = Songs.reversed()),
    ),
    services = listOf(Triple("ytmusic", "YT Music", 0xFFFF0033L), Triple("spotify", "Spotify", 0xFF1DB954L)),
    shortcuts = listOf("Liked songs", "Late night", "Gym", "Daily mix 1", "Road trip", "Focus").mapIndexed { i, t -> HomeShortcut("s$i", t, null) {} },
    userName = "Alex",
)

/** Home with the mini player and tab bar under it, as App draws them. */
@Composable
private fun HomePicture() {
    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            ui = SampleHome, contentPadding = PaddingValues(bottom = 150.dp), onProfile = {}, onSettings = {},
            onPlay = { _, _ -> }, onOpenCollection = {}, onSearch = {},
        )
        val bottom = groundBottom()
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(24.dp).background(Brush.verticalGradient(listOf(bottom.copy(alpha = 0f), bottom))))
            val flush = app.duet.music.ui.theme.LocalStyle.current.miniSize == app.duet.music.ui.theme.MiniSize.Slim
            Box(Modifier.fillMaxWidth().background(bottom).padding(start = if (flush) 0.dp else 8.dp, end = if (flush) 0.dp else 8.dp, bottom = if (flush) 0.dp else 6.dp)) {
                MiniPlayer(
                    MiniPlayerUi("Hallucinate", "YT Music", Color(0xFFFF0033), null, isPlaying = true, isLoading = false, progress = 0.35f),
                    onTogglePlay = {}, onOpen = {}, onPrevious = {}, onNext = {}, liked = false,
                )
            }
            TabBar(Tab.Home, onSelect = {}, modifier = Modifier.background(bottom).padding(bottom = 12.dp))
        }
    }
}

private val PreviewPlayer = PlayerState(
    queue = Songs.take(3).mapIndexed { i, t -> QueueItem(i.toLong(), t) }, index = 0, status = PlaybackStatus.Playing,
    positionMs = 72_000, durationMs = 206_000,
)

/** The computer layout: sidebar, Home or the player in the middle, and the player bar, as App draws them. */
@Composable
private fun DesktopPicture(player: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            DesktopSidebar(Tab.Home, onTab = {}, onAccounts = {}, onSettings = {})
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (player) PlayerPicture()
                else HomeScreen(
                    ui = SampleHome, contentPadding = PaddingValues(bottom = 24.dp), onProfile = {}, onSettings = {},
                    onPlay = { _, _ -> }, onOpenCollection = {}, onSearch = {},
                )
            }
        }
        DesktopPlayerBar(PreviewPlayer, PlayerControls.None, liked = false, onToggleLike = {}, onOpenPlayer = {}, onOpenQueue = {}, onOpenLyrics = {})
    }
}

/** The player, always dark as in the app. */
@Composable
private fun PlayerPicture() {
    val state = PreviewPlayer
    OnArtTheme {
        StudioPlayer(
            state, Songs.first(), "YT Music", live = false, PlayerControls.None, onClose = {}, onOpenQueue = {}, onOpenLyrics = {},
            showLyricsButton = true, showSourceLine = true, save = null, onOpenArtist = null, liked = false, onToggleLike = {},
            sleepMinutes = 0, onSleep = {}, artFallback = PreviewSong,
        )
    }
}
