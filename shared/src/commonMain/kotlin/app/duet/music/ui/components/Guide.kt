package app.duet.music.ui.components

import app.duet.music.ui.theme.duetGround
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.DuetDimens
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType

/*
 * The building blocks every screen shares, so the whole app reads the same way:
 * - a tab screen opens with [ScreenHeader] (title + one plain sentence saying what the screen is for);
 * - a page on top of the tabs uses [PageScaffold] (back arrow + title + the same sentence);
 * - help is a [HintCard]; an empty screen is an [EmptyState] that says what to do next.
 */

/** Title of a tab screen, an optional line saying what it is for, and optional buttons on the right. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(DuetType.titled(title), style = DuetType.ScreenTitle, modifier = Modifier.weight(1f).semantics { heading() })
            actions()
        }
        if (subtitle != null) Text(subtitle, style = DuetType.RowSub, color = DuetColors.TextDim)
    }
}

/** A full-screen page over the tabs: back arrow, title, what the page is for, then scrolling [content]. */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    backLabel: String = "Back",
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().duetGround().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp + 4.dp)) {
                Icon(DuetIcons.Back, contentDescription = backLabel, tint = DuetColors.Text)
            }
            Text(DuetType.titled(title), style = DuetType.ScreenTitle.copy(fontSize = 24.sp), modifier = Modifier.semantics { heading() })
        }
        if (subtitle != null) {
            Text(
                subtitle, style = DuetType.RowSub, color = DuetColors.TextDim,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
        }
        val body = Modifier.fillMaxWidth().weight(1f)
        Column(if (scroll) body.verticalScroll(rememberScrollState()).padding(bottom = 32.dp) else body) { content() }
    }
}

/** One step of help: a short title, one or two plain sentences, and an optional button that does it. */
@Composable
fun HintCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().clip(duetShape(16)).background(DuetColors.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(DuetType.titled(title), style = DuetType.SectionTitle.copy(fontSize = 17.sp), modifier = Modifier.weight(1f).semantics { heading() })
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
                    Icon(DuetIcons.Clear, contentDescription = "Hide this tip", tint = DuetColors.TextDim, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(body, style = DuetType.RowSub, color = DuetColors.TextDim)
        content()
        if (actionLabel != null && onAction != null) PrimaryButton(actionLabel, onAction)
    }
}

/** A numbered step inside a [HintCard]: the number, what to do, and the whole row is the button. */
@Composable
fun GuideStep(number: Int, title: String, detail: String, done: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = DuetDimens.MinTouchTarget.dp).clip(duetShape(12))
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(30.dp).background(if (done) DuetColors.Accent else DuetColors.SurfaceRaised, duetShape(6)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (done) "✓" else "$number", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = if (done) DuetColors.OnAccent else DuetColors.Text)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = DuetType.RowTitle)
            Text(detail, style = DuetType.RowSub, color = DuetColors.TextDim)
        }
        Icon(DuetIcons.ChevronRight, contentDescription = null, tint = DuetColors.TextDim, modifier = Modifier.size(18.dp))
    }
}

/** What an empty screen shows: an icon, what will appear here, and the one thing to do next. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(64.dp).background(DuetColors.Surface, duetShape(12)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = DuetColors.Accent, modifier = Modifier.size(28.dp))
        }
        Text(DuetType.titled(title), style = DuetType.SectionTitle.copy(fontSize = 18.sp), textAlign = TextAlign.Center)
        Text(body, style = DuetType.RowSub, color = DuetColors.TextDim, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) PrimaryButton(actionLabel, onAction)
    }
}

/** The main action on a card or empty screen: an accent pill with a plain verb. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = DuetDimens.MinTouchTarget.dp).clip(duetShape(DuetDimens.RadiusChip))
            .background(DuetColors.Accent).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = DuetType.Chip, color = DuetColors.OnAccent) }
}

/** All / YT Music / Spotify: which service's music a screen shows, as plain words like every row of choices. */
@Composable
fun ServiceSwitch(services: List<Triple<String, String, Long>>, only: String?, onSelect: (String?) -> Unit) {
    if (app.duet.music.ui.theme.LocalStyle.current.serviceChips) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ServicePill("All", null, only == null) { onSelect(null) }
        services.forEach { (id, name, colour) -> ServicePill(name, Color(colour), only == id) { onSelect(id) } }
    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("All", only == null) { onSelect(null) }
        services.forEach { (id, name, _) -> Chip(name, only == id) { onSelect(id) } }
    }
}

/** A service choice as a pill with a dot in the service's colour; the chosen one is outlined in the highlight. */
@Composable
private fun ServicePill(label: String, dot: Color?, selected: Boolean, onClick: () -> Unit) {
    val shape = duetShape(20)
    Row(
        Modifier.heightIn(min = 36.dp).clip(shape).background(if (selected) DuetColors.Accent.copy(alpha = 0.16f) else DuetColors.Surface)
            .border(1.dp, if (selected) DuetColors.Accent else DuetColors.Line, shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dot))
        Text(label, style = DuetType.Chip, color = if (selected) DuetColors.Text else DuetColors.TextDim)
    }
}
