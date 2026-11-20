package app.duet.music.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import app.duet.music.ui.theme.duetGround
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.selectable
import app.duet.music.ui.player.PlayerControls
import app.duet.music.player.PlaybackStatus
import app.duet.music.player.PlayerState
import app.duet.music.player.RepeatMode
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.Tab
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.player.formatTime
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType
import app.duet.music.ui.theme.LocalStyle
import app.duet.music.ui.theme.duetShape

/** True when the app is drawn for a computer (set by [app.duet.music.ui.App]); the style editor and preview read it. */
val LocalDesktop = androidx.compose.runtime.staticCompositionLocalOf { false }

/** On a Mac the title bar is transparent and the traffic-light buttons sit over the app: this much space (in the UI's own dp) stays free at the top. */
val LocalTitleBarInset = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

/** How wide the desktop layout's sidebar is (icons only: narrow), and how tall its player bar: the style's choice. */
@Composable
fun desktopSidebarWidth(): Dp = LocalStyle.current.let { if (it.sidebarLabels) it.sidebarWidth.dp else 76.dp }
@Composable
fun desktopBarHeight(): Dp = LocalStyle.current.barHeight.dp

/** Desktop's left sidebar: the four sections, then Accounts and Settings. */
@Composable
fun DesktopSidebar(tab: Tab, onTab: (Tab) -> Unit, onAccounts: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.width(desktopSidebarWidth()).fillMaxHeight().background(DuetColors.Surface.copy(alpha = 0.55f)).padding(start = 12.dp, end = 12.dp, top = 20.dp + LocalTitleBarInset.current, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val labels = LocalStyle.current.sidebarLabels
        if (labels) Text(DuetType.titled("Duet"), style = DuetType.Display(26), modifier = Modifier.padding(start = 12.dp, bottom = 20.dp))
        else Spacer(Modifier.height(44.dp))
        Tab.entries.forEach { SidebarRow(it.label, it.icon, selected = it == tab, labels = labels) { onTab(it) } }
        Spacer(Modifier.weight(1f))
        SidebarRow("Accounts", DuetIcons.Person, selected = false, labels = labels, onClick = onAccounts)
        SidebarRow("Settings", DuetIcons.Settings, selected = false, labels = labels, onClick = onSettings)
    }
}

@Composable
private fun SidebarRow(label: String, icon: ImageVector, selected: Boolean, labels: Boolean, onClick: () -> Unit) {
    val ink = if (selected) DuetColors.Text else DuetColors.TextDim
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(duetShape(10))
            .then(if (selected) Modifier.background(DuetColors.Text.copy(alpha = 0.1f)) else Modifier)
            .selectable(selected, role = Role.Tab, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (labels) Arrangement.spacedBy(14.dp) else Arrangement.Center,
    ) {
        Icon(icon, contentDescription = if (labels) null else label, tint = if (selected) DuetColors.Accent else ink, modifier = Modifier.size(22.dp))
        if (labels) Text(label, style = DuetType.RowTitle.copy(fontSize = 15.sp), color = ink, maxLines = 1)
    }
}

/**
 * Desktop's player bar along the bottom: the song on the left, controls and the seek bar in the middle, and
 * queue, lyrics and the full player on the right. Clicking the song opens the full player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopPlayerBar(
    state: PlayerState, controls: PlayerControls, liked: Boolean?, onToggleLike: () -> Unit,
    onOpenPlayer: () -> Unit, onOpenQueue: () -> Unit, onOpenLyrics: () -> Unit, modifier: Modifier = Modifier,
) {
    val track = state.track
    val playing = state.status == PlaybackStatus.Playing
    val duration = state.durationMs?.takeIf { it > 0 }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: duration?.let { (state.positionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    Row(
        modifier.fillMaxWidth().height(desktopBarHeight()).background(app.duet.music.ui.components.miniTint(track?.artworkUrl).copy(alpha = 0.9f)).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The song.
        Row(
            Modifier.weight(1f).clip(duetShape(10)).then(if (track != null) Modifier.clickable(role = Role.Button, onClickLabel = "Open player", onClick = onOpenPlayer) else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (track != null) {
                Artwork(track.artworkUrl, diagonal(0xFF2A2622, 0xFF141210), size = (LocalStyle.current.barHeight - 28).coerceIn(44, 80), radius = 8)
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = DuetType.RowTitle.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = DuetType.RowSub, color = DuetColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (liked != null) IconButton(onClick = onToggleLike, modifier = Modifier.size(40.dp)) {
                    Icon(if (liked) DuetIcons.HeartFilled else DuetIcons.Heart, if (liked) "Unlike ${track.title}" else "Like ${track.title}", tint = DuetColors.Text, modifier = Modifier.size(22.dp))
                }
            } else Text("Nothing playing", style = DuetType.RowSub, color = DuetColors.TextDim)
        }
        // Controls and the seek bar.
        Column(Modifier.weight(1.6f).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BarButton(DuetIcons.Shuffle, "Shuffle", if (state.shuffle) DuetColors.Accent else DuetColors.TextDim, controls.toggleShuffle)
                if (LocalStyle.current.miniPrevious) BarButton(DuetIcons.Previous, "Previous", DuetColors.Text, controls.previous, enabled = track != null)
                // The style's play button: filled in the highlight, outlined, glass, or just the icon.
                val ps = LocalStyle.current.play
                val look = when (ps) {
                    app.duet.music.ui.theme.PlayStyle.Filled -> Modifier.background(DuetColors.Accent)
                    app.duet.music.ui.theme.PlayStyle.Outline -> Modifier.border(2.dp, DuetColors.Text, duetShape(50))
                    app.duet.music.ui.theme.PlayStyle.Glass -> Modifier.background(DuetColors.Text.copy(alpha = 0.16f))
                    app.duet.music.ui.theme.PlayStyle.Icon -> Modifier
                }
                IconButton(onClick = controls.togglePlayPause, enabled = track != null, modifier = Modifier.size(46.dp).clip(duetShape(50)).then(look)) {
                    Icon(if (playing) DuetIcons.Pause else DuetIcons.Play, contentDescription = if (playing) "Pause" else "Play",
                        tint = if (ps == app.duet.music.ui.theme.PlayStyle.Filled) DuetColors.OnAccent else DuetColors.Text, modifier = Modifier.size(26.dp))
                }
                BarButton(DuetIcons.Next, "Next", DuetColors.Text, controls.next, enabled = state.hasNext)
                BarButton(
                    if (state.repeat == RepeatMode.One) DuetIcons.RepeatOne else DuetIcons.Repeat, "Repeat",
                    if (state.repeat != RepeatMode.Off) DuetColors.Accent else DuetColors.TextDim, controls.cycleRepeat,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(formatTime(state.positionMs), style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.width(44.dp))
                Slider(
                    value = fraction,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { f -> duration?.let { controls.seekTo((f * it).toLong()) } }; dragging = null },
                    enabled = duration != null,
                    thumb = { Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (duration != null) DuetColors.Text else DuetColors.Line)) },
                    colors = SliderDefaults.colors(
                        thumbColor = DuetColors.Text, activeTrackColor = DuetColors.Accent, inactiveTrackColor = DuetColors.Line,
                        disabledThumbColor = DuetColors.Line, disabledActiveTrackColor = DuetColors.Line, disabledInactiveTrackColor = DuetColors.Line,
                    ),
                    modifier = Modifier.weight(1f).height(24.dp),
                )
                Text(if (duration != null) formatTime(duration) else "LIVE", style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.width(44.dp))
            }
        }
        // Queue, lyrics, full player.
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            BarButton(DuetIcons.Lyrics, "Lyrics", DuetColors.TextDim, onOpenLyrics, enabled = track != null)
            BarButton(DuetIcons.Queue, "Queue", DuetColors.TextDim, onOpenQueue, enabled = track != null)
            BarButton(DuetIcons.ChevronDown, "Open player", DuetColors.TextDim, onOpenPlayer, enabled = track != null, rotated = true)
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit, enabled: Boolean = true, rotated: Boolean = false) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp).semantics { contentDescription = label }) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(22.dp).then(if (rotated) Modifier.rotate(180f) else Modifier))
    }
}

/**
 * On a computer a screen is a centred column of reading width, not the phone layout stretched across the window
 * (a cover at 62% of a wide window is huge). [full] screens (the player, lyrics, car mode) lay themselves out;
 * [ground] draws the song-coloured ground behind a page so what is under it does not show beside the column.
 */
@androidx.compose.runtime.Composable
fun DesktopFit(full: Boolean, ground: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
    if (!LocalDesktop.current) { content(); return }
    // Full-width pages still need the ground: a page is an overlay, and what is under it would show through.
    if (full) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().then(if (ground) androidx.compose.ui.Modifier.duetGround() else androidx.compose.ui.Modifier)) { content() }; return }
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().then(if (ground) androidx.compose.ui.Modifier.duetGround() else androidx.compose.ui.Modifier),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter,
    ) {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.widthIn(max = 2400.dp).fillMaxSize()) { content() }
    }
}

/** What the Mac menu bar asks the app to do (the app owns the screens, so it listens). */
enum class AppCommand { Search, Settings, NowPlaying, Lyrics }

/** A line from the menu bar to [app.duet.music.ui.App]. */
class AppCommands {
    internal val flow = kotlinx.coroutines.flow.MutableSharedFlow<AppCommand>(extraBufferCapacity = 8)
    fun send(c: AppCommand) { flow.tryEmit(c) }
}
