package app.duet.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.settings.AppSettings
import app.duet.music.settings.CustomLook
import app.duet.music.ui.components.Chip
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetStyles
import app.duet.music.ui.theme.DuetTheme
import app.duet.music.ui.theme.DuetType
import app.duet.music.ui.theme.HighlightChoices
import app.duet.music.ui.theme.HomeLayout
import app.duet.music.ui.theme.LocalGroundTint
import app.duet.music.ui.theme.LocalStyle
import app.duet.music.ui.theme.LookCss
import app.duet.music.ui.theme.LookKnob
import app.duet.music.ui.theme.MiniColor
import app.duet.music.ui.theme.PlayStyle
import app.duet.music.ui.theme.PlayerBackground
import app.duet.music.ui.theme.SeekStyle
import app.duet.music.ui.theme.duetGround
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.styleOf
import kotlin.random.Random

/** The editor's sections, as in the Look Studio: every setting, grouped. Highlight colour sits under Colour. */
internal val Sections: List<Pair<String, List<LookKnob>>> = listOf(
    "Colour" to listOf(LookKnob.Ground, LookKnob.Wash, LookKnob.Effect),
    "Text" to listOf(LookKnob.Font, LookKnob.TitleFont, LookKnob.Titles, LookKnob.TextSize),
    "Shape and spacing" to listOf(LookKnob.Corners, LookKnob.SpacingKnob, LookKnob.Shadow),
    "Home" to listOf(LookKnob.Header, LookKnob.Home, LookKnob.CardSize, LookKnob.Services),
    "Now-playing bar" to listOf(LookKnob.MiniSizeKnob, LookKnob.MiniBar, LookKnob.MiniButtons, LookKnob.MiniProgress),
    "Player" to listOf(
        LookKnob.Player, LookKnob.Cover, LookKnob.Play, LookKnob.PlayButtonSize, LookKnob.Seek, LookKnob.CoverPos,
        LookKnob.PlayerGap, LookKnob.SideMargins, LookKnob.TitleAlign, LookKnob.Below,
    ),
    "Tab bar" to listOf(LookKnob.TabNames, LookKnob.Tabs),
)

/** Settings that only a computer's layout has; the editor shows them on desktop. */
internal val DesktopSections: List<Pair<String, List<LookKnob>>> = listOf(
    "Desktop" to listOf(LookKnob.SidebarWidth, LookKnob.SidebarNames, LookKnob.BarHeight),
)

/**
 * The style editor: the Home and Now Playing preview stays on top and every setting is on one screen below it
 * (like the Look Studio page), so a change shows at once. The CSS section copies the style as CSS, copies a prompt
 * for an AI, and pastes CSS back in. [editing] is a custom style to change, or null for a new one built from the
 * style in use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleEditor(settings: AppSettings, editing: CustomLook?, onClose: () -> Unit) {
    val current = LocalStyle.current
    val startBase = editing?.base ?: app.duet.music.ui.theme.StyleTemplates.firstOrNull { it.id == current.id }?.id
        ?: settings.customLooks.value.firstOrNull { it.id == current.id }?.base ?: DuetStyles.first().id
    var base by rememberSaveable { mutableStateOf(startBase) }
    var accent by rememberSaveable { mutableStateOf(editing?.accent ?: settings.accent.value) }
    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var changes by remember { mutableStateOf(editing?.changes ?: settings.customLooks.value.firstOrNull { it.id == current.id }?.changes.orEmpty()) }
    var pasting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val now = CustomLook(editing?.id ?: "draft", name, base, accent, changes)
    val nowStyle = styleOf(now)
    val draft = nowStyle.let { if (current.isLight) it.light() else it }

    fun save() {
        val look = CustomLook(editing?.id ?: (CustomLook.ID_PREFIX + Random.nextLong(1L shl 40).toString(36)), CustomLook.cleanName(name), base, accent,
            LookKnob.entries.associate { it.name to it.valueIn(nowStyle) })
        settings.saveCustomLook(look)
        settings.chooseStyle(look.id, look.accent)
        onClose()
    }
    fun copy(text: String, what: String) { clipboard.setText(AnnotatedString(text)); status = "$what copied." }

    Column(Modifier.fillMaxSize().duetGround().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(DuetIcons.Back, contentDescription = "Close without saving", tint = DuetColors.Text) }
            Text(DuetType.titled(if (editing == null) "New style" else "Edit style"), style = DuetType.ScreenTitle.copy(fontSize = 22.sp), modifier = Modifier.weight(1f))
            StepButton("Save", filled = true) { save() }
        }
        // On a computer the preview and the settings sit side by side, each scrolling on its own.
        val wide = app.duet.music.ui.desktop.LocalDesktop.current
        val options: @Composable () -> Unit = {
            Spacer(Modifier.height(4.dp))
            EditorSection("Start from") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.duet.music.ui.theme.StyleTemplates.forEach { st ->
                        Chip(st.lookName, st.id == base) { base = st.id; changes = emptyMap(); accent = st.accent }
                    }
                }
                OutlinedTextField(
                    name, { name = it.take(24) }, singleLine = true, label = { Text("Name", color = DuetColors.TextDim) }, placeholder = { Text("My style", color = DuetColors.TextDim) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DuetColors.Text, unfocusedTextColor = DuetColors.Text,
                        focusedBorderColor = DuetColors.Accent, unfocusedBorderColor = DuetColors.Line, cursorColor = DuetColors.Accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            (if (wide) Sections + DesktopSections else Sections).forEach { (title, knobs) ->
                EditorSection(title) {
                    knobs.forEach { k ->
                        val value = k.valueIn(nowStyle)
                        Text(k.label, style = DuetType.RowSub, color = DuetColors.TextDim)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            k.options.forEach { (v, label) -> Chip(label, v == value) { changes = changes + (k.name to v) } }
                        }
                    }
                    if (title == "Colour") {
                        Text("Highlight colour", style = DuetType.RowSub, color = DuetColors.TextDim)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            HighlightChoices.forEach { (argb, label) ->
                                Box(
                                    Modifier.size(44.dp).selectable(argb == accent, role = Role.RadioButton) { accent = argb }.semantics { contentDescription = label },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.size(30.dp).clip(duetShape(6)).background(Color(argb))
                                        .then(if (argb == accent) Modifier.border(2.dp, DuetColors.Text, duetShape(6)) else Modifier))
                                }
                            }
                        }
                    }
                }
            }
            EditorSection("CSS") {
                Text("Your style as CSS. Edit it by hand, or copy the AI prompt, tell any AI the look you want, and paste its answer back in.",
                    style = DuetType.RowSub, color = DuetColors.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Copy CSS", false) { copy(LookCss.export(now.copy(changes = LookKnob.entries.associate { it.name to it.valueIn(nowStyle) })), "CSS") }
                    Chip("Copy AI prompt", false) { copy(LookCss.aiPrompt(), "Prompt") }
                    Chip("Paste CSS", false) { status = ""; pasting = true }
                }
                if (status.isNotEmpty()) Text(status, style = DuetType.RowSub, color = DuetColors.Accent)
            }
            Spacer(Modifier.height(24.dp))
        }
        if (wide) Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(0.52f).verticalScroll(rememberScrollState())) { StylePreview(draft, accent, Modifier) }
            Column(Modifier.weight(0.48f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) { options() }
        } else {
            StylePreview(draft, accent, Modifier)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { options() }
        }
    }

    if (pasting) PasteCssDialog(
        onApply = { look -> base = look.base; accent = look.accent; changes = look.changes; if (look.name != "My style") name = look.name; pasting = false; status = "CSS applied." },
        onDismiss = { pasting = false },
    )
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = DuetType.RowTitle.copy(fontSize = 18.sp))
        content()
    }
}

/** Asks for CSS text; says so when none of it is a Duet setting. */
@Composable
private fun PasteCssDialog(onApply: (CustomLook) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DuetColors.Surface,
        title = { Text("Paste CSS", color = DuetColors.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    text, { text = it; bad = false }, modifier = Modifier.fillMaxWidth().height(220.dp),
                    placeholder = { Text(":root { --highlight: #ff4d6d; ... }", color = DuetColors.TextDim) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DuetColors.Text, unfocusedTextColor = DuetColors.Text,
                        focusedBorderColor = DuetColors.Accent, unfocusedBorderColor = DuetColors.Line, cursorColor = DuetColors.Accent,
                    ),
                )
                if (bad) Text("None of that is a Duet setting. Start from Copy CSS.", style = DuetType.RowSub, color = DuetColors.Accent)
            }
        },
        confirmButton = { TextButton({ LookCss.parse(text)?.let(onApply) ?: run { bad = true } }) { Text("Apply", color = DuetColors.Accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = DuetColors.TextDim) } },
    )
}

@Composable
private fun StepButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val shape = duetShape(10)
    Box(
        Modifier.height(44.dp).clip(shape)
            .then(if (filled) Modifier.background(DuetColors.Accent) else Modifier.border(1.dp, DuetColors.Line, shape))
            .selectable(false, role = Role.Button, onClick = onClick).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = DuetType.Chip, color = if (filled) DuetColors.OnAccent else DuetColors.Text) }
}
