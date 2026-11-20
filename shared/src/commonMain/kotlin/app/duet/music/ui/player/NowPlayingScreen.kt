package app.duet.music.ui.player

import androidx.compose.foundation.layout.fillMaxHeight
import app.duet.music.ui.theme.duetGround
import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.verticalScroll
import app.duet.music.ui.components.pressScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.extension.SourceRegistry
import app.duet.music.player.PlaybackCoordinator
import app.duet.music.player.PlaybackStatus
import app.duet.music.player.PlayerState
import app.duet.music.player.RepeatMode
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.DuetDimens
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType

private val Soft = Color.White.copy(alpha = 0.82f)

/** Full-screen player (design/screens/NowPlaying.dc.html). */
@Composable
fun NowPlayingScreen(
    state: PlayerState,
    sources: SourceRegistry,
    controls: PlaybackCoordinator,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    /** Settings > Player. */
    showLyricsButton: Boolean = true,
    showSourceLine: Boolean = true,
    /** The Save (download) button, or null when this song can't be saved. */
    save: SaveUi? = null,
    /** Tapping the artist line opens the artist's page; null where there's no artist (radio). */
    onOpenArtist: (() -> Unit)? = null,
    /** The heart beside the title; null where a song can't be liked (radio). */
    liked: Boolean? = null,
    onToggleLike: () -> Unit = {},
    /** Sleep timer (minutes left as set, 0 off) and its setter; the moon in the icon row. */
    sleepMinutes: Int = 0,
    onSleep: ((Int) -> Unit)? = null,
) {
    val track = state.track ?: return
    val source = sources[track.sourceId]
    val live = state.durationMs == null && state.status != PlaybackStatus.Loading && source?.manifest?.capabilities?.contains(app.duet.music.extension.Capability.LiveRadio) == true
    StudioPlayer(state, track, source?.manifest?.name, live, PlayerControls.of(controls), onClose, onOpenQueue, onOpenLyrics, showLyricsButton, showSourceLine, save, onOpenArtist, liked, onToggleLike, sleepMinutes, onSleep)
}

/** What the player screen asks of playback: the real coordinator, or nothing in the style editor's preview. */
internal class PlayerControls(
    val seekTo: (Long) -> Unit, val toggleShuffle: () -> Unit, val previous: () -> Unit,
    val togglePlayPause: () -> Unit, val next: () -> Unit, val cycleRepeat: () -> Unit,
) {
    companion object {
        val None = PlayerControls({}, {}, {}, {}, {}, {})
        fun of(c: PlaybackCoordinator) = PlayerControls(c::seekTo, c::toggleShuffle, c::previous, c::togglePlayPause, c::next, c::cycleRepeat)
    }
}

/**
 * The player (Look Studio, D63): the cover blurred over the whole screen, the cover itself edge to edge in
 * the middle with a deep shadow, the title on the left, a thick seek bar, an outlined play button.
 */
@Composable
internal fun StudioPlayer(
    state: PlayerState, track: app.duet.music.domain.Track, sourceName: String?, live: Boolean, controls: PlayerControls,
    onClose: () -> Unit, onOpenQueue: () -> Unit, onOpenLyrics: () -> Unit, showLyricsButton: Boolean, showSourceLine: Boolean,
    save: SaveUi?, onOpenArtist: (() -> Unit)?, liked: Boolean?, onToggleLike: () -> Unit,
    sleepMinutes: Int, onSleep: ((Int) -> Unit)?,
    /** The cover's colour when the song has no artwork (the style editor's preview uses a song-like colour). */
    artFallback: Color = Color(0xFF3A3D5A),
) {
    val art = rememberArtColor(track.artworkUrl, fallback = artFallback)
    val style = app.duet.music.ui.theme.LocalStyle.current
    val desktop = app.duet.music.ui.desktop.LocalDesktop.current
    // The full-cover look has no separate cover; on a computer, where the cover is always shown beside the controls, it takes the blurred look.
    val bg = style.playerBackground.let { if (desktop && it == app.duet.music.ui.theme.PlayerBackground.Cover) app.duet.music.ui.theme.PlayerBackground.Blur else it }
    // On a computer the player draws no ground of its own: the window's song-coloured ground shows through, so the sidebar and the player are one piece.
    Box(if (desktop) Modifier.fillMaxSize() else if (bg != app.duet.music.ui.theme.PlayerBackground.Ground) Modifier.fillMaxSize().background(app.duet.music.ui.theme.songGradient(style.copy(songWash = 1f), art))
        else Modifier.fillMaxSize().duetGround()) {
        // Blur needs Android 12+; older phones get the colour gradient underneath.
        // Full cover: the artwork fills the top and fades into black under the controls.
        if (bg == app.duet.music.ui.theme.PlayerBackground.Cover) {
            if (track.artworkUrl != null) coil3.compose.AsyncImage(
                model = largeArtwork(track.artworkUrl), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.25f to Color.Transparent, 0.42f to Color.Black.copy(alpha = 0.15f), 0.66f to Color.Black.copy(alpha = 0.85f), 1f to Color.Black)))
        }
        if (bg == app.duet.music.ui.theme.PlayerBackground.Blur) androidx.compose.animation.Crossfade(track.artworkUrl, animationSpec = androidx.compose.animation.core.tween(600), label = "blur") { url ->
            if (url != null) coil3.compose.AsyncImage(
                model = url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f; alpha = 0.7f }.blur(60.dp),
            )
        }
        if (bg == app.duet.music.ui.theme.PlayerBackground.Blur) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        val header: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
                    Icon(DuetIcons.ChevronDown, contentDescription = "Close player", tint = Color.White)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (showSourceLine && sourceName != null) "Playing from $sourceName" else "", fontSize = 13.sp, color = Soft,
                        maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    // Stream quality in small letters under the source (radio says LIVE on the seek bar instead).
                    if (!live) state.quality?.let {
                        Text(it, fontSize = 10.sp, color = DuetColors.TextDim, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                val menu = app.duet.music.ui.components.LocalTrackMenu.current
                IconButton(onClick = { menu(track) }, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
                    Icon(DuetIcons.More, contentDescription = "More options for ${track.title}", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        val cover: @Composable (Modifier) -> Unit = { slot ->
            // The cover takes the space that's left and stays square: the full width when there's room.
            if (!desktop && bg == app.duet.music.ui.theme.PlayerBackground.Cover) Spacer(slot)
            else androidx.compose.foundation.layout.BoxWithConstraints(slot, contentAlignment = if (desktop) Alignment.Center else when (style.coverPosition) {
                app.duet.music.ui.theme.CoverPosition.Top -> Alignment.TopCenter
                app.duet.music.ui.theme.CoverPosition.Center -> Alignment.Center
                app.duet.music.ui.theme.CoverPosition.Bottom -> Alignment.BottomCenter
            }) {
                val side = if (desktop) minOf(maxWidth, maxHeight) * style.coverSize.coerceIn(0.5f, 1f) else minOf(maxWidth * style.coverSize, maxHeight)
                val shape = RoundedCornerShape(24.dp)
                Box(
                    Modifier.size(side).shadow(style.coverShadow.dp.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(art),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.animation.Crossfade(track.artworkUrl, animationSpec = androidx.compose.animation.core.tween(450), label = "cover") { url ->
                        if (url != null) Box(Modifier.fillMaxSize()) {
                            // The small picture (already loaded in the lists) shows at once and stays if the large one never comes.
                            coil3.compose.AsyncImage(model = url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (largeArtwork(url) != url) coil3.compose.AsyncImage(
                                model = largeArtwork(url), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                            )
                        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(if (live) DuetIcons.Radio else DuetIcons.Library, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(72.dp))
                        }
                    }
                }
            }
        }
        val rest: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
            Row(verticalAlignment = Alignment.Bottom) {
                // A centred title balances the heart on the right so it sits in the middle of the screen.
                if (style.titleCentred && liked != null) Spacer(Modifier.size(DuetDimens.MinTouchTarget.dp))
                val align = if (style.titleCentred) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
                Column(Modifier.weight(1f), horizontalAlignment = if (style.titleCentred) Alignment.CenterHorizontally else Alignment.Start) {
                    Text(DuetType.titled(track.title), style = DuetType.Display(26), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = align)
                    Text(track.artist, fontSize = 16.sp, color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align,
                        modifier = Modifier.padding(top = 4.dp).then(if (onOpenArtist == null) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = "Open artist", onClick = onOpenArtist)))
                }
                if (liked != null) IconButton(onClick = onToggleLike, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
                    Icon(if (liked) DuetIcons.HeartFilled else DuetIcons.Heart, if (liked) "Unlike ${track.title}" else "Like ${track.title}",
                        tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            Column {
                SeekBar(state, controls.seekTo, badge = if (live) "LIVE" else null)
                if (state.status == PlaybackStatus.Error) Text(
                    if (live) "This station isn't on the air right now. Try another one." else "Can't play this. (${state.error})",
                    style = DuetType.RowSub, color = Color(0xFFFFB4A8), modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Toggle(DuetIcons.Shuffle, "Shuffle", on = state.shuffle, onClick = controls.toggleShuffle)
                SkipButton(DuetIcons.Previous, "Previous", enabled = true, onClick = controls.previous)
                PlayButton(state, controls.togglePlayPause)
                SkipButton(DuetIcons.Next, "Next", enabled = state.hasNext, onClick = controls.next)
                Toggle(
                    if (state.repeat == RepeatMode.One) DuetIcons.RepeatOne else DuetIcons.Repeat, "Repeat", on = state.repeat != RepeatMode.Off,
                    stateText = when (state.repeat) { RepeatMode.Off -> "off"; RepeatMode.All -> "all"; RepeatMode.One -> "one" },
                    onClick = controls.cycleRepeat,
                )
            }
            when (style.playerBelow) {
                // Icons only: save, lyrics, sleep timer, queue.
                app.duet.music.ui.theme.PlayerBelow.Icons -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    save?.let { BarIcon(it.icon, it.label, it.onClick) }
                    if (showLyricsButton && !live) BarIcon(DuetIcons.Lyrics, "Lyrics", onOpenLyrics)
                    if (onSleep != null) SleepButton(sleepMinutes, onSleep)
                    BarIcon(DuetIcons.Queue, "Queue", onOpenQueue)
                }
                app.duet.music.ui.theme.PlayerBelow.Lyrics -> if (showLyricsButton && !live) BelowLine("Lyrics", onOpenLyrics) else BelowLine("Queue", onOpenQueue)
                app.duet.music.ui.theme.PlayerBelow.Next -> BelowLine(
                    state.queue.getOrNull(state.index + 1)?.track?.let { "Up next: ${it.title}" } ?: "Queue", onOpenQueue,
                )
                app.duet.music.ui.theme.PlayerBelow.Nothing -> Unit
            }
        }
        if (desktop) {
            // Computer: the cover big on the left, the song and its controls in a column on the right; nothing phone-shaped.
            // The close / more row spans the whole top; the cover and the controls fill the rest, side by side.
            Column(Modifier.fillMaxSize().padding(horizontal = (style.playerMargin + 8).dp, vertical = 16.dp)) {
                header()
                // On an ultrawide screen the cover and controls stay together in the middle instead of drifting to opposite ends.
                Row(Modifier.weight(1f).widthIn(max = 1500.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    cover(Modifier.weight(1.3f).fillMaxHeight())
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((style.playerGap + 12).dp, Alignment.CenterVertically)) { rest() }
                }
            }
        } else Column(
            Modifier.align(Alignment.TopCenter).fillMaxHeight().fillMaxWidth()
                .statusBarsPadding().navigationBarsPadding().padding(horizontal = style.playerMargin.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(style.playerGap.dp),
        ) {
            header()
            cover(Modifier.weight(1f).fillMaxWidth())
            rest()
        }
    }
}

/** One line under the controls that opens something: the lyrics or the queue. */
@Composable
private fun BelowLine(text: String, onClick: () -> Unit) {
    Text(
        text, fontSize = 15.sp, color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = text, onClick = onClick).wrapContentHeight(Alignment.CenterVertically),
    )
}

/** Play / pause: a large outlined button with fully rounded ends (Look Studio "Outline square" at 18px corners). */
@Composable
private fun PlayButton(state: PlayerState, onClick: () -> Unit) {
    val playing = state.status == PlaybackStatus.Playing
    val loading = state.status == PlaybackStatus.Loading
    val style = app.duet.music.ui.theme.LocalStyle.current
    // Rounder styles get fully rounded ends, the rest the style's corners (Look Studio's rule).
    val shape = if (style.roundness >= 1.1f) RoundedCornerShape(50) else duetShape(12)
    val kind = style.play
    val ink = when (kind) {
        app.duet.music.ui.theme.PlayStyle.Filled -> DuetColors.OnAccent
        app.duet.music.ui.theme.PlayStyle.Icon -> if (style.accentEverywhere) DuetColors.Accent else Color.White
        else -> Color.White
    }
    val look = when (kind) {
        app.duet.music.ui.theme.PlayStyle.Filled -> Modifier.background(DuetColors.Accent)
        app.duet.music.ui.theme.PlayStyle.Outline -> Modifier.border(2.dp, if (style.accentEverywhere) DuetColors.Accent else Color.White, shape)
        app.duet.music.ui.theme.PlayStyle.Glass -> Modifier.background(Color.White.copy(alpha = 0.14f)).border(1.dp, Color.White.copy(alpha = 0.24f), shape)
        app.duet.music.ui.theme.PlayStyle.Icon -> Modifier
    }
    Box(
        Modifier.size(width = (100 * style.playSize.scale).dp, height = (74 * style.playSize.scale).dp).pressScale().clip(shape)
            .then(look)
            .clickable(enabled = !loading, role = Role.Button, onClickLabel = if (playing) "Pause" else "Play", onClick = onClick)
            .semantics { contentDescription = if (loading) "Loading" else if (playing) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        else Icon(if (playing) DuetIcons.Pause else DuetIcons.Play, contentDescription = null, tint = ink,
            modifier = Modifier.size(((if (kind == app.duet.music.ui.theme.PlayStyle.Icon) 52 else 34) * style.playSize.scale).dp))
    }
}

/** The moon: tap for a short list of sleep times; lit while a timer runs. */
@Composable
private fun SleepButton(minutes: Int, onSleep: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp).semantics { stateDescription = if (minutes == 0) "off" else "$minutes minutes" }) {
            Icon(DuetIcons.Sleep, contentDescription = "Sleep timer", tint = if (minutes > 0) DuetColors.Accent else Soft, modifier = Modifier.size(24.dp))
        }
        androidx.compose.material3.DropdownMenu(open, onDismissRequest = { open = false }, containerColor = DuetColors.SurfaceRaised, shape = duetShape(8)) {
            listOf(0, 15, 30, 45, 60, 90).forEach { m ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(if (m == 0) "Off" else "$m minutes", color = if (m == minutes) DuetColors.Accent else DuetColors.Text) },
                    onClick = { onSleep(m); open = false },
                )
            }
        }
    }
}

/** Previous / next: plain icons. */
@Composable
private fun SkipButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) DuetColors.Text else DuetColors.TextDim.copy(alpha = 0.5f)
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp).pressScale()) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(32.dp))
    }
}

/** The player's Save button: its words say the state ("Save", "Saving 40%", "Saved"). */
data class SaveUi(val label: String, val onClick: () -> Unit) {
    val icon: ImageVector get() = when (label) {
        "Saved" -> DuetIcons.Check
        "Favourite" -> DuetIcons.Heart
        "Favourited" -> DuetIcons.HeartFilled
        else -> DuetIcons.Download
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, tint = Soft, modifier = Modifier.size(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(state: PlayerState, onSeek: (Long) -> Unit, badge: String? = null) {
    val duration = state.durationMs?.takeIf { it > 0 }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: duration?.let { (state.positionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { f -> duration?.let { onSeek((f * it).toLong()) } }; dragging = null },
            enabled = duration != null,
            // A thick rounded bar, no knob.
            thumb = { Spacer(Modifier.size(width = 0.dp, height = 20.dp)) },
            track = { s ->
                val style = app.duet.music.ui.theme.LocalStyle.current
                val played = if (style.accentEverywhere) DuetColors.Accent else DuetColors.Text
                // Dotted: a row of dots, the played ones lit and a touch bigger (Look Studio "Dotted").
                if (style.seek == app.duet.music.ui.theme.SeekStyle.Dots) androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    val step = 7.dp.toPx()
                    val split = size.width * s.value.coerceIn(0f, 1f)
                    var x = step / 2
                    while (x < size.width) {
                        val on = x <= split
                        drawCircle(if (on) played else Color.White.copy(alpha = 0.2f), radius = (if (on) 1.9f else 1.6f) * density, center = androidx.compose.ui.geometry.Offset(x, size.height / 2))
                        x += step
                    }
                } else {
                    val h = if (style.seek == app.duet.music.ui.theme.SeekStyle.Line) 3.dp else 6.dp
                    Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(s.value.coerceIn(0f, 1f)).height(h).clip(RoundedCornerShape(3.dp)).background(played))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Seek" },
        )
        val shown = duration?.let { (fraction * it).toLong() } ?: state.positionMs
        // Times at the edges, the tag pinned to the true centre whatever the times' widths.
        Box(Modifier.fillMaxWidth()) {
            Text(formatTime(shown), fontSize = 12.sp, color = DuetColors.TextDim, modifier = Modifier.align(Alignment.CenterStart))
            // LIVE for radio in a small outlined tag, in the highlight colour.
            badge?.let {
                Text(it, fontSize = 9.sp, letterSpacing = 1.sp, color = DuetColors.Accent, maxLines = 1,
                    modifier = Modifier.align(Alignment.Center).border(1.dp, DuetColors.Accent.copy(alpha = 0.6f), duetShape(4)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Text(duration?.let { formatTime(it) } ?: "", fontSize = 12.sp, color = DuetColors.TextDim, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, stateText: String = if (on) "on" else "off", onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { stateDescription = stateText }) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Icon(icon, contentDescription = label, tint = if (on) DuetColors.Accent else Soft, modifier = Modifier.size(22.dp))
            // A short dash under the icon when on, so the state isn't colour alone.
            if (on) Box(Modifier.padding(top = 30.dp).size(width = 10.dp, height = 2.dp).background(DuetColors.Accent))
        }
    }
}

internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val s = total % 60
    val m = (total / 60) % 60
    val h = total / 3600
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

/** YouTube/Google image URLs carry their size; ask for a big one for the 342dp cover. */
internal fun largeArtwork(url: String) = url.replace(Regex("""=w\d+-h\d+"""), "=w1080-h1080")
