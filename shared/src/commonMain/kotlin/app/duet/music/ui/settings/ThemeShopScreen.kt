package app.duet.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.duet.music.settings.CustomLook
import app.duet.music.shop.SharedStyle
import app.duet.music.shop.ThemeShop
import app.duet.music.ui.components.Chip
import app.duet.music.ui.components.PageScaffold
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType
import app.duet.music.ui.theme.LookKnob
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.styleOf

/**
 * Shared styles (D66, D67): everyone's styles, filtered by tag and sorted by newest or most liked. Tapping one
 * opens [SharedStyleScreen] with a live preview and every setting it uses.
 */
@Composable
fun ThemeShopScreen(shop: ThemeShop, onBack: () -> Unit, onSignIn: () -> Unit, onOpen: (SharedStyle) -> Unit) {
    val styles by shop.styles.collectAsState()
    val message by shop.message.collectAsState()
    val busy by shop.busy.collectAsState()
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var byLikes by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { shop.refresh() }
    PageScaffold("Shared styles", onBack, subtitle = "Styles other people made. Share your own from Appearance.") {
        message?.let { Text(it, style = DuetType.RowSub, color = DuetColors.Accent, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        if (!shop.signedIn) Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sign in to YouTube Music to share your own.", style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = onSignIn) { Text("Sign in", color = DuetColors.Text) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Newest", !byLikes) { byLikes = false }
            Chip("Most liked", byLikes) { byLikes = true }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("All", tag == null) { tag = null }
            ThemeShop.TAGS.forEach { t -> Chip(t.replaceFirstChar { it.uppercase() }, tag == t) { tag = t } }
        }
        val shown = styles.filter { tag == null || tag in it.tags }.let { l -> if (byLikes) l.sortedByDescending { it.likes } else l }
        if (shown.isEmpty()) Text(if (busy) "Loading…" else "Nothing here yet.", style = DuetType.RowSub, color = DuetColors.TextDim,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        shown.forEach { s ->
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open") { onOpen(s) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Swatch(s)
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = DuetType.RowTitle, maxLines = 1)
                    Text(listOfNotNull("by ${s.author}", s.description.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = DuetType.RowSub, color = DuetColors.TextDim, maxLines = 1)
                }
                Text("♥ ${s.likes}", style = DuetType.RowSub, color = DuetColors.TextDim)
            }
        }
    }
}

/** The style's ground with its highlight glowing in the corner. */
@Composable
private fun Swatch(s: SharedStyle) {
    val look = CustomLook.fromShareCode("preview", s.name, s.code)
    Box(Modifier.size(44.dp).clip(duetShape(8)).background(look?.let(::styleOf)?.ground ?: DuetColors.Surface)
        .background(Brush.radialGradient(listOf(Color(look?.accent ?: 0xFF888888).copy(alpha = 0.8f), Color.Transparent))))
}

/** One shared style: a live preview, its description and tags, every setting it uses, and Try / Like / Delete. */
@Composable
fun SharedStyleScreen(shop: ThemeShop, id: String, onBack: () -> Unit, onTried: () -> Unit) {
    val styles by shop.styles.collectAsState()
    val me by shop.username.collectAsState()
    val s = styles.firstOrNull { it.id == id }
    PageScaffold(s?.name ?: "Shared style", onBack, subtitle = s?.let { "by ${it.author} · ♥ ${it.likes}" }) {
        val look = s?.let { CustomLook.fromShareCode("preview", it.name, it.code) }
        if (s == null || look == null) {
            Text("This style isn't available.", style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.padding(20.dp))
            return@PageScaffold
        }
        val style = styleOf(look)
        StylePreview(style, look.accent, Modifier.height(380.dp))
        if (s.description.isNotBlank()) Text(s.description, style = DuetType.RowSub, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        if (s.tags.isNotEmpty()) Text(s.tags.joinToString("  ") { "#$it" }, style = DuetType.RowSub, color = DuetColors.TextDim,
            modifier = Modifier.padding(horizontal = 20.dp))
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { if (shop.install(s)) onTried() }) { Text("Try it", color = DuetColors.Text) }
            TextButton(onClick = { shop.like(s) }) {
                Icon(DuetIcons.Heart, null, tint = DuetColors.Text, modifier = Modifier.size(16.dp)); Text(" Like", color = DuetColors.Text)
            }
            if (me.isNotBlank() && s.author.equals(me, ignoreCase = true))
                TextButton(onClick = { shop.delete(s); onBack() }) { Text("Delete", color = DuetColors.TextDim) }
        }
        // Every setting, as the editor names it.
        Text("Settings", style = DuetType.RowTitle, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))
        Row(Modifier.padding(horizontal = 20.dp, vertical = 3.dp)) {
            Text("Highlight colour", style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.weight(1f))
            Box(Modifier.size(16.dp).clip(duetShape(3)).background(Color(look.accent)))
        }
        LookKnob.entries.forEach { k ->
            val value = k.valueIn(style)
            Row(Modifier.padding(horizontal = 20.dp, vertical = 3.dp)) {
                Text(k.label, style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.weight(1f))
                Text(k.options.firstOrNull { it.first == value }?.second ?: value, style = DuetType.RowSub)
            }
        }
    }
}

/**
 * Shares one of your styles: a username the first time, then a short description and up to four tags.
 * Sharing needs YouTube Music signed in; the dialog says so instead.
 */
@Composable
fun ShareStyleDialog(shop: ThemeShop, look: CustomLook, onDismiss: () -> Unit) {
    val needsName = shop.username.value.isBlank()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(setOf<String>()) }
    val fieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedTextColor = DuetColors.Text, unfocusedTextColor = DuetColors.Text,
        focusedBorderColor = DuetColors.Accent, unfocusedBorderColor = DuetColors.Line, cursorColor = DuetColors.Accent,
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DuetColors.SurfaceRaised,
        titleContentColor = DuetColors.Text,
        title = { Text("Share \"${look.name}\"") },
        text = {
            if (!shop.signedIn) Text("Sign in to YouTube Music in Duet first (Settings > Music services).", color = DuetColors.TextDim)
            else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (needsName) androidx.compose.material3.OutlinedTextField(name, { name = it.take(20) }, singleLine = true,
                    label = { Text("Pick a username", color = DuetColors.TextDim) }, colors = fieldColors)
                androidx.compose.material3.OutlinedTextField(description, { description = it.take(140) },
                    label = { Text("Describe it (optional)", color = DuetColors.TextDim) }, colors = fieldColors)
                Text("Tags (up to 4)", style = DuetType.RowSub, color = DuetColors.TextDim)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeShop.TAGS.forEach { t ->
                        Chip(t, t in tags) { tags = if (t in tags) tags - t else if (tags.size < 4) tags + t else tags }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = shop.signedIn && (!needsName || name.isNotBlank()), onClick = {
                val send = { shop.share(look, description, tags.toList()) }
                if (needsName) shop.chooseName(name.trim()) { send() } else send()
                onDismiss()
            }) { Text("Share", color = DuetColors.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DuetColors.TextDim) } },
    )
}
