package app.duet.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.DuetDimens
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType
import coil3.compose.AsyncImage

/**
 * Source is always mark + text, never colour alone (SPEC §1). The mark takes the highlight colour, not the
 * service's own red or green, so a style's colours are the only colours on screen (D67).
 */
@Composable
fun SourceLine(text: String, textColor: Color = DuetColors.TextDim, dotSize: Int = 6, fontSize: Int = 13) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(dotSize.dp).background(DuetColors.Accent, duetShape(2)))
        Text(
            text, color = textColor, style = DuetType.RowSub.copy(fontSize = fontSize.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Square artwork: a remote image when [url] is set, else the [placeholder] gradient. */
@Composable
fun Artwork(url: String?, placeholder: Brush, size: Int, radius: Int, modifier: Modifier = Modifier) {
    val shape = duetShape(radius)
    Box(modifier.size(size.dp).clip(shape).background(placeholder)) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}

fun diagonal(vararg colors: Long): Brush = Brush.linearGradient(colors.map { Color(it) })

data class MiniPlayerUi(
    val title: String,
    val sourceName: String,
    val sourceColor: Color,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val isLoading: Boolean,
    /** 0..1, or null when the length is unknown. */
    val progress: Float?,
    val error: String? = null,
)

@Composable
fun MiniPlayer(ui: MiniPlayerUi, onTogglePlay: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier, onPrevious: (() -> Unit)? = null, onNext: (() -> Unit)? = null, liked: Boolean? = null, onToggleLike: () -> Unit = {}) {
    // A card in the song's colour: artwork, title, previous / play / next as plain icons; a line of progress along the bottom edge.
    // Its size comes from the style: a flat slim bar, a card, or a large card.
    val size = app.duet.music.ui.theme.LocalStyle.current.miniSize
    val (height, art) = when (size) { app.duet.music.ui.theme.MiniSize.Slim -> 56 to 40; app.duet.music.ui.theme.MiniSize.Card -> 64 to 48; app.duet.music.ui.theme.MiniSize.Large -> 84 to 64 }
    val shape = if (size == app.duet.music.ui.theme.MiniSize.Slim) duetShape(0) else duetShape(12)
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(shape)
            .background(miniTint(ui.artworkUrl))
            .border(1.dp, DuetColors.Line, shape)
            .pressScale()
            .clickable(role = Role.Button, onClickLabel = "Open player", onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            androidx.compose.animation.Crossfade(ui.artworkUrl, label = "miniArt") { url ->
                Artwork(url, diagonal(0xFF2A2622, 0xFF141210), size = art, radius = 7)
            }
            Column(Modifier.weight(1f)) {
                Text(ui.title, style = DuetType.RowTitle.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                SourceLine(ui.error ?: ui.sourceName, DuetColors.TextDimOnTint, fontSize = 12)
            }
            val label = when {
                ui.isLoading -> "Loading"
                ui.isPlaying -> "Pause"
                else -> "Play"
            }
            val style = app.duet.music.ui.theme.LocalStyle.current
            if (style.miniLike && liked != null) IconButton(onClick = onToggleLike, modifier = Modifier.size(40.dp)) {
                Icon(if (liked) DuetIcons.HeartFilled else DuetIcons.Heart, contentDescription = if (liked) "Unlike" else "Like", tint = DuetColors.Text, modifier = Modifier.size(22.dp))
            }
            if (onPrevious != null && style.miniPrevious) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                    Icon(DuetIcons.Previous, contentDescription = "Previous", tint = DuetColors.Text, modifier = Modifier.size(22.dp))
                }
            }
            RingButton(size = 40, onClick = onTogglePlay, label = label, enabled = !ui.isLoading) {
                Icon(if (ui.isPlaying) DuetIcons.Pause else DuetIcons.Play, contentDescription = null,
                    tint = if (ui.isLoading) DuetColors.TextDimOnTint else DuetColors.Text, modifier = Modifier.size(24.dp))
            }
            if (onNext != null && !style.miniLike) {
                IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                    Icon(DuetIcons.Next, contentDescription = "Next", tint = DuetColors.Text, modifier = Modifier.size(22.dp))
                }
            }
        }
        if (app.duet.music.ui.theme.LocalStyle.current.miniProgress) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(DuetColors.Text.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(ui.progress ?: 0f).height(2.dp).background(DuetColors.Accent))
        }
    }
}

/** A plain icon button (the player's controls, the mini player's play): no ring, no disc. */
@Composable
fun RingButton(size: Int, onClick: () -> Unit, label: String, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(duetShape(8))
            .pressScale()
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Shrinks a little while pressed and springs back: the tactile feel the whole app shares. */
fun Modifier.pressScale(): Modifier = composed {
    var pressed by remember { androidx.compose.runtime.mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.95f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 700f), label = "press",
    )
    // Watches the touch without consuming it, so the clickable underneath still works.
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    pressed = e.changes.any { it.pressed }
                }
            }
        }
}

/** The mini player's colour: the song's artwork colour, mixed into the ground so the style's text stays readable. */
@Composable
internal fun miniTint(art: String?): Color {
    val c = app.duet.music.ui.player.rememberArtColor(art, fallback = DuetColors.MiniPlayerTint)
    when (app.duet.music.ui.theme.LocalStyle.current.miniColor) {
        app.duet.music.ui.theme.MiniColor.Glass, app.duet.music.ui.theme.MiniColor.Plain -> return DuetColors.MiniPlayerTint
        app.duet.music.ui.theme.MiniColor.Song -> {}
    }
    return androidx.compose.ui.graphics.lerp(DuetColors.Ground, c, if (app.duet.music.ui.theme.LocalStyle.current.isLight) 0.3f else 0.55f)
}

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", DuetIcons.Home),
    Search("Search", DuetIcons.Search),
    Library("Library", DuetIcons.Library),
    Radio("Radio", DuetIcons.Radio),
}

@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val style = app.duet.music.ui.theme.LocalStyle.current
    // Floating: a rounded see-through bar inset from the edges (Look Studio "Floating bar").
    val bar = if (style.floatingTabs) Modifier.padding(start = 32.dp, end = 32.dp, bottom = 10.dp).fillMaxWidth().height(50.dp)
        .clip(duetShape(12)).background(if (style.isLight) Color(0xE6FFFFFF) else Color(0xD91C1C20)).border(1.dp, DuetColors.Line, duetShape(12)).padding(horizontal = 8.dp)
        else Modifier.fillMaxWidth().height(DuetDimens.TabBarHeight.dp).padding(horizontal = 12.dp, vertical = 6.dp)
    Row(
        modifier.then(bar),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val active = tab == selected
            // Icons only; the open tab is bright, the rest dim.
            val tint by androidx.compose.animation.animateColorAsState(if (!active) DuetColors.TextDim else if (style.accentEverywhere) DuetColors.Accent else DuetColors.Text, label = "tab")
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(duetShape(12))
                    .clickable(role = Role.Tab, onClickLabel = tab.label) { onSelect(tab) }
                    .semantics { contentDescription = tab.label + if (active) ", selected" else "" },
                contentAlignment = Alignment.Center,
            ) {
                if (style.tabLabels) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(tab.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(tab.label, fontSize = 10.sp, color = tint, maxLines = 1)
                } else Icon(tab.icon, null, tint = tint, modifier = Modifier.size(25.dp))
            }
        }
    }
}

/**
 * A choice in a row of choices (genres, filters, sound presets), as a plain word: the chosen one bright, bold and
 * underlined, the rest dim; the same look as the service switch.
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Chip(text: String, selected: Boolean, onLongClickLabel: String? = null, onLongClick: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val color by androidx.compose.animation.animateColorAsState(if (selected) DuetColors.Text else DuetColors.TextDim, label = "chip")
    // The end padding plus the rows' 8dp spacing leaves ~20dp between words, with the first word on the margin.
    Column(
        Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).height(DuetDimens.MinTouchTarget.dp)
            .then(when {
                onClick != null && onLongClick != null -> Modifier.semantics { this.selected = selected }
                    .combinedClickable(role = Role.Tab, onLongClickLabel = onLongClickLabel, onLongClick = onLongClick, onClick = onClick)
                onClick != null -> Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
                else -> Modifier
            })
            .padding(end = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = DuetType.Chip.copy(fontSize = 15.sp), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = color, maxLines = 1)
        val mark = if (app.duet.music.ui.theme.LocalStyle.current.accentEverywhere) DuetColors.Accent else DuetColors.Text
        Box(Modifier.padding(top = 4.dp).height(2.dp).fillMaxWidth().background(if (selected) mark else Color.Transparent))
    }
}

/** Asks before a saved song's file is deleted from the phone. */
@Composable
fun RemoveDownloadDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) =
    ConfirmDialog("Remove download?", "\"$title\" will be deleted from this phone. You can still stream it or save it again.", "Remove", onConfirm, onDismiss)

/** Asks before something is deleted or removed; [onConfirm] runs, then the dialog closes. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DuetColors.SurfaceRaised,
        titleContentColor = DuetColors.Text,
        textContentColor = DuetColors.TextDim,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = DuetColors.Accent) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = DuetColors.TextDim) }
        },
    )
}

/** Opens the song menu (Play next, Add to queue, …) for a track; provided by the app. */
val LocalTrackMenu = androidx.compose.runtime.staticCompositionLocalOf<(app.duet.music.domain.Track) -> Unit> { {} }

/** Tap plays, long press opens the song menu. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.trackClick(track: app.duet.music.domain.Track, onClick: () -> Unit): Modifier = composed {
    val menu = LocalTrackMenu.current
    combinedClickable(role = Role.Button, onClickLabel = "Play", onLongClickLabel = "More options", onLongClick = { menu(track) }, onClick = onClick)
}
