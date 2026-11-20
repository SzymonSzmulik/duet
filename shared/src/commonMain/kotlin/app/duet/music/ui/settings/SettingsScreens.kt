package app.duet.music.ui.settings

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import app.duet.music.update.AppUpdates
import app.duet.music.update.ReleaseControls
import app.duet.music.update.UpdateState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.extension.Capability
import app.duet.music.extension.ExtensionSetting
import app.duet.music.extension.SourceProvider
import app.duet.music.extension.SourceRegistry
import app.duet.music.settings.AppSettings
import app.duet.music.settings.Flag
import app.duet.music.downloads.Downloads
import app.duet.music.ui.components.Chip
import app.duet.music.ui.components.PageScaffold
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.DuetDimens
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType

/** The account button on Home: a plain icon, like Settings beside it (no filled disc). */
@Composable
fun AvatarButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
        Icon(DuetIcons.Person, contentDescription = "Profile and accounts", tint = DuetColors.Text, modifier = Modifier.size(22.dp))
    }
}

/**
 * Behind the avatar (pattern from Myspoti's profile sheet): one row per source with its account
 * state, then Settings and Sources.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(sources: SourceRegistry, onOpenSettings: () -> Unit, onOpenSource: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DuetColors.Surface,
        contentColor = DuetColors.Text,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(DuetType.titled("Accounts"), style = DuetType.SectionTitle, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            sources.providers.forEach { p ->
                NavRow(title = p.manifest.name, detail = accountStatusOf(p), dot = DuetColors.Accent) { onOpenSource(p.manifest.id) }
            }
            HorizontalDivider(color = DuetColors.Line, modifier = Modifier.padding(vertical = 8.dp))
            NavRow(title = "Settings", detail = "", icon = DuetIcons.Settings, onClick = onOpenSettings)
        }
    }
}

/** The pages the Settings menu opens, in menu order. */
enum class SettingsGroup(val title: String, val icon: ImageVector) {
    Appearance("Appearance", DuetIcons.Appearance),
    HomePage("Home page", DuetIcons.Home),
    Playback("Playback", DuetIcons.Play),
    Services("Music services", DuetIcons.Extension),
    Storage("Downloads and storage", DuetIcons.Download),
    Releases("Releases", DuetIcons.Share),
    About("About", DuetIcons.Info),
}

/**
 * Settings: a short menu of groups under headings (the owner's reference: Metrolist/SpatialFlow);
 * each group opens as its own page. [group] null shows the menu.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    sources: SourceRegistry,
    appVersion: String,
    onBack: () -> Unit,
    onOpenSource: (String) -> Unit,
    group: SettingsGroup? = null,
    onOpenGroup: (SettingsGroup) -> Unit = {},
    downloads: Downloads? = null,
    cache: app.duet.music.library.MediaCache? = null,
    /** The services' Home shelves right now, so each can be turned off. */
    shelves: List<app.duet.music.domain.Shelf> = emptyList(),
    onOpenSound: () -> Unit = {},
    onOpenCarMode: () -> Unit = {},
    updates: AppUpdates? = null,
    releases: ReleaseControls? = null,
    onShowTutorial: () -> Unit = {},
    onEditStyle: (String?) -> Unit = {},
    shop: app.duet.music.shop.ThemeShop? = null,
    onOpenShop: () -> Unit = {},
) {
    if (group == null) {
        PageScaffold("Settings", onBack) {
            Heading("Interface")
            MenuCard(SettingsGroup.Appearance, onOpenGroup)
            MenuCard(SettingsGroup.HomePage, onOpenGroup)
            Heading("Player and audio")
            MenuCard(SettingsGroup.Playback, onOpenGroup)
            MenuCard("Sound", DuetIcons.Sound, onOpenSound)
            // Car mode is for a phone in a car; a computer has no use for it.
            if (!app.duet.music.ui.desktop.LocalDesktop.current) MenuCard("Car mode", DuetIcons.Car, onOpenCarMode)
            Heading("Accounts")
            MenuCard(SettingsGroup.Services, onOpenGroup)
            if (downloads != null || cache != null) {
                Heading("Storage and data")
                MenuCard(SettingsGroup.Storage, onOpenGroup)
            }
            if (releases != null) {
                Heading("Owner")
                MenuCard(SettingsGroup.Releases, onOpenGroup)
            }
            Heading("About")
            MenuCard(SettingsGroup.About, onOpenGroup)
        }
        return
    }
    PageScaffold(group.title, onBack) {
        when (group) {
            SettingsGroup.Appearance -> AppearanceGroup(settings, onEditStyle, shop, onOpenShop)
            SettingsGroup.HomePage -> HomeGroup(settings, sources, shelves)
            SettingsGroup.Playback -> PlaybackGroup(settings)
            SettingsGroup.Services -> sources.providers.forEach { p ->
                NavRow(title = p.manifest.name, detail = accountStatusOf(p), dot = DuetColors.Accent) { onOpenSource(p.manifest.id) }
            }
            SettingsGroup.Storage -> StorageGroup(settings, downloads, cache)
            SettingsGroup.Releases -> if (releases != null) ReleasesSection(releases)
            SettingsGroup.About -> AboutGroup(appVersion, updates, onShowTutorial)
        }
    }
}

@Composable
private fun AppearanceGroup(settings: AppSettings, onEditStyle: (String?) -> Unit, shop: app.duet.music.shop.ThemeShop?, onOpenShop: () -> Unit) {
    val accent by settings.accent.collectAsState()
    val custom by settings.customLooks.collectAsState()
    val style = app.duet.music.ui.theme.LocalStyle.current
    Column {
        // The whole look; picking one also takes its own highlight colour.
        Text("Style", style = DuetType.RowTitle, modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp))
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (app.duet.music.ui.theme.DuetStyles + custom.map { app.duet.music.ui.theme.styleOf(it) }).forEach { st ->
                val chosen = st.id == style.id
                // The chosen style says Dark or Light; holding it switches.
                app.duet.music.ui.components.Chip(
                    if (chosen) st.lookName + if (style.isLight) " · Light" else " · Dark" else st.lookName, chosen,
                    onLongClickLabel = if (style.isLight) "Switch to dark" else "Switch to light",
                    onLongClick = { if (!chosen) settings.chooseStyle(st.id, st.accent); settings.setLightMode(!style.isLight) },
                ) { settings.chooseStyle(st.id, st.accent) }
            }
        }
        // Under the list, on the right: make a new style (and change or remove your own).
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Hold a style to switch between dark and light.", style = DuetType.RowSub, color = DuetColors.TextDim,
                modifier = Modifier.weight(1f).padding(start = 8.dp))
            val mine = custom.firstOrNull { it.id == style.id }
            var sharing by remember { mutableStateOf(false) }
            if (mine != null) {
                // Share: a username the first time, a description and tags, then it's up in Shared styles.
                if (shop != null) androidx.compose.material3.TextButton(onClick = { sharing = true }) { Text("Share", color = DuetColors.Text) }
                if (sharing && shop != null) ShareStyleDialog(shop, mine, onDismiss = { sharing = false })
                androidx.compose.material3.TextButton(onClick = { onEditStyle(mine.id) }) { Text("Edit", color = DuetColors.Text) }
                androidx.compose.material3.TextButton(onClick = { settings.deleteCustomLook(mine.id) }) { Text("Delete", color = DuetColors.TextDim) }
            }
            androidx.compose.material3.TextButton(onClick = { onEditStyle(null) }) {
                Icon(DuetIcons.Add, contentDescription = null, tint = DuetColors.Text, modifier = Modifier.size(18.dp))
                Text(" Add", color = DuetColors.Text)
            }
        }
        if (shop != null) {
            val note by shop.message.collectAsState()
            note?.let { Text(it, style = DuetType.RowSub, color = DuetColors.Accent, modifier = Modifier.padding(horizontal = 20.dp)) }
            NavRow(title = "Shared styles", detail = "Try styles other people made", icon = DuetIcons.Library, onClick = onOpenShop)
        }
        Text("Highlight color", style = DuetType.RowTitle, modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp))
        Text("Colours the glow, tabs, chosen words, seek bar and play button.", style = DuetType.RowSub, color = DuetColors.TextDim,
            modifier = Modifier.padding(horizontal = 20.dp))
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            app.duet.music.ui.theme.HighlightChoices.forEach { (argb, name) ->
                val selected = argb == accent
                Box(
                    Modifier.size(DuetDimens.MinTouchTarget.dp + 8.dp)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { settings.setAccent(argb) })
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(duetShape(6)).background(Color(argb))
                            .then(if (selected) Modifier.border(2.dp, DuetColors.Text, duetShape(6)) else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackGroup(settings: AppSettings) {
    Column {
        // Phone-only: they need Android's audio, headphone and car features, so a Mac does not show them.
        if (!app.duet.music.ui.desktop.LocalDesktop.current) {
            FlagRow(settings, Flag.SkipSilence)
            FlagRow(settings, Flag.PauseOnUnplug)
            FlagRow(settings, Flag.KeepPlayingInBackground)
            FlagRow(settings, Flag.AutoPlayRadioInCar)
        }
        // Scrobbling: ListenBrainz takes the user's own token, so Duet needs no app key.
        val token by settings.listenBrainzToken.collectAsState()
        var editingToken by remember { mutableStateOf(false) }
        NavRow(
            title = "ListenBrainz",
            detail = if (token.isBlank()) "Off. Add your token from listenbrainz.org/settings to keep a record of what you play" else "On: songs you play are sent to ListenBrainz",
            icon = DuetIcons.Repeat,
        ) { editingToken = true }
        if (editingToken) app.duet.music.ui.library.NameDialog("ListenBrainz token", token,
            onDone = { settings.setListenBrainzToken(it); editingToken = false }, onDismiss = { editingToken = false })
        val sleep by settings.sleepMinutes.collectAsState()
        Text("Sleep timer", style = DuetType.RowTitle, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
        Text(
            if (sleep == 0) "Off" else "Pauses in $sleep min",
            style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "1 hour").forEach { (m, label) ->
                Chip(label, selected = sleep == m) { settings.setSleepMinutes(m) }
            }
        }
        Section("Now playing")
        FlagRow(settings, Flag.LyricsButton)
        FlagRow(settings, Flag.SourceLine)
    }
}

@Composable
private fun HomeGroup(settings: AppSettings, sources: SourceRegistry, shelves: List<app.duet.music.domain.Shelf>) {
    Column {
        Section("Duet sections")
        val hidden by settings.hiddenSections.collectAsState()
        app.duet.music.settings.HomeSection.entries.forEach { sec ->
            SwitchRow(sec.title, "", on = sec.id !in hidden) { settings.setSectionShown(sec.id, it) }
        }
        // Every shelf the services offer (e.g. Spotify's "Recommended radios", "Made for you"), plus any
        // turned off earlier that isn't offered right now.
        val shelfIds = (shelves.map { "${it.sourceId}:${it.title}" } + hidden.filter { ':' in it && it.substringBefore(':').contains('.') }).distinct()
        if (shelfIds.isNotEmpty()) Section("From your services")
        shelfIds.forEach { id ->
            val source = sources[id.substringBeforeLast(':').substringBefore(':')]
            val title = id.substringAfter(':')
            SwitchRow(title, source?.manifest?.name ?: "", on = id !in hidden) { settings.setSectionShown(id, it) }
        }
    }
}

@Composable
private fun StorageGroup(settings: AppSettings, downloads: Downloads?, cache: app.duet.music.library.MediaCache?) {
    Column {
        if (downloads != null) {
            Section("Downloads")
            if (!app.duet.music.ui.desktop.LocalDesktop.current) FlagRow(settings, Flag.DownloadWifiOnly)
            val saved by downloads.saved.collectAsState()
            InfoRow("Saved songs", "${saved.size} ${if (saved.size == 1) "song" else "songs"} · ${formatBytes(downloads.bytesUsed())}")
            if (saved.isNotEmpty()) {
                var confirm by remember { mutableStateOf(false) }
                NavRow(
                    title = if (confirm) "Tap again to delete all" else "Delete all saved songs",
                    detail = if (confirm) "This can't be undone" else if (app.duet.music.ui.desktop.LocalDesktop.current) "Frees the space on this Mac" else "Frees the space on this phone",
                    icon = DuetIcons.Clear,
                ) { if (confirm) { downloads.deleteAll(); confirm = false } else confirm = true }
            }
        }

        if (cache != null) {
            Section("Cache")
            val keys by cache.cachedKeys.collectAsState()
            InfoRow("Cached songs", "${keys.size} · ${formatBytes(cache.bytesUsed())}")
            var confirmCache by remember { mutableStateOf(false) }
            NavRow(
                title = if (confirmCache) "Tap again to clear" else "Clear cache",
                detail = "Songs you've played play offline until you clear this",
                icon = DuetIcons.Clear,
            ) { if (confirmCache) { cache.clear(); confirmCache = false } else confirmCache = true }
        }
    }
}

@Composable
private fun AboutGroup(appVersion: String, updates: AppUpdates?, onShowTutorial: () -> Unit) {
    var showNotices by remember { mutableStateOf(false) }
    Column {
        Spacer(Modifier.height(8.dp))
        InfoRow("Version", appVersion)
        NavRow(title = "Show the tutorial", detail = "The quick tour of Duet from the first launch", icon = DuetIcons.Info, onClick = onShowTutorial)
        if (updates != null) {
            val update by updates.state.collectAsState()
            NavRow(
                title = if (update is UpdateState.Available) "Update to ${(update as UpdateState.Available).info.versionName}" else "Check for updates",
                detail = when (val u = update) {
                    UpdateState.Idle -> ""
                    UpdateState.Checking -> "Checking…"
                    UpdateState.UpToDate -> "You're on the latest version"
                    is UpdateState.Available -> "New version available"
                    is UpdateState.Downloading -> if (u.progress < 0) "Downloading…" else "Downloading ${(u.progress * 100).toInt()}%"
                    is UpdateState.Failed -> u.message
                },
                icon = DuetIcons.Download,
            ) {
                when (val u = update) {
                    is UpdateState.Available -> updates.download(u.info)
                    is UpdateState.Downloading, UpdateState.Checking -> {}
                    else -> updates.check()
                }
            }
        }
        InfoRow("License", "GPL-3.0")
        NavRow(title = "Open-source notices", detail = if (showNotices) "Tap to hide" else "The free software Duet is built with", icon = DuetIcons.Info) {
            showNotices = !showNotices
        }
        if (showNotices) {
            NOTICES.forEach { (name, license) -> InfoRow(name, license) }
        }
    }
}

/** A heading over a group of rows (every Settings page uses this one): small, bold, dim. */
@Composable
private fun Heading(title: String) {
    Text(title, style = DuetType.RowSub, fontWeight = FontWeight.Bold, color = DuetColors.TextDim,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun MenuCard(group: SettingsGroup, onOpen: (SettingsGroup) -> Unit) = MenuCard(group.title, group.icon) { onOpen(group) }

/** One menu entry: the same plain row as on the Settings pages (icon, title, chevron). */
@Composable
private fun MenuCard(title: String, icon: ImageVector, onClick: () -> Unit) = NavRow(title, "", icon = icon, onClick = onClick)

/** One source's own page: account, its settings, and what it is allowed to do (SPEC §3b). */
@Composable
fun SourceScreen(provider: SourceProvider, settings: AppSettings, onBack: () -> Unit) {
    val store = remember(provider) { settings.forExtension(provider.manifest.id) }
    var showTech by remember { mutableStateOf(false) }
    PageScaffold(provider.manifest.name, onBack) {
        Section("Account")
        InfoRow("Status", accountStatusOf(provider))
        provider.account?.let { account ->
            val signedIn by account.signedIn.collectAsState()
            NavRow(
                title = if (signedIn) "Sign out" else "Sign in",
                detail = "",
                icon = DuetIcons.Person,
                onClick = if (signedIn) account::signOut else account::signIn,
            )
        }

        if (provider.settings.isNotEmpty()) {
            Section("Options")
            provider.settings.forEach { s ->
                when (s) {
                    is ExtensionSetting.Toggle -> {
                        var on by remember(s.key) { mutableStateOf(store.getBoolean(s.key, s.default)) }
                        Row(
                            Modifier.fillMaxWidth()
                                .toggleable(value = on, role = Role.Switch) { on = it; store.putBoolean(s.key, it) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.title, style = DuetType.RowTitle)
                                Text(s.detail, style = DuetType.RowSub, color = DuetColors.TextDim)
                            }
                            Switch(
                                checked = on, onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedTrackColor = DuetColors.Accent, checkedThumbColor = DuetColors.OnAccent),
                            )
                        }
                    }
                }
            }
        }

        Section("What it does in Duet")
        val caps = provider.manifest.capabilities.map { capabilityLabel(it) }
        Text(
            caps.joinToString(", ").ifEmpty { "Sign-in only for now. Search and playback are coming." },
            style = DuetType.RowSub, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        NavRow(
            title = "Technical details",
            detail = if (showTech) "Tap to hide" else "Version, and the internet addresses it uses",
            icon = DuetIcons.Info,
        ) { showTech = !showTech }
        if (showTech) {
            InfoRow("Version", provider.manifest.version)
            InfoRow("ID", provider.manifest.id)
            Text("Connects to", style = DuetType.RowTitle, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
            provider.manifest.permissions.networkHosts.forEach {
                Text(it, style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            }
            provider.manifest.permissions.accountScopes.forEach {
                Text(it, style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun FlagRow(settings: AppSettings, flag: Flag) {
    val on by settings.flag(flag).collectAsState()
    SwitchRow(flag.title, flag.detail, on) { settings.setFlag(flag, it) }
}

@Composable
private fun SwitchRow(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DuetType.RowTitle)
            if (detail.isNotEmpty()) Text(detail, style = DuetType.RowSub, color = DuetColors.TextDim)
        }
        Switch(
            checked = on, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = DuetColors.Accent, checkedThumbColor = DuetColors.OnAccent),
        )
    }
}

internal fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "${(b * 10 / (1L shl 30)) / 10.0} GB"
    b >= 1L shl 20 -> "${(b * 10 / (1L shl 20)) / 10.0} MB"
    else -> "${b / 1024} KB"
}

/** The source's account line, live for sources with a [SourceProvider.account]. */
@Composable
private fun accountStatusOf(p: SourceProvider): String =
    p.account?.status?.collectAsState()?.value ?: p.accountStatus

/** Duet Owner: what testers and everyone have, and buttons to send the owner build on. */
@Composable
private fun ReleasesSection(releases: ReleaseControls) {
    LaunchedEffect(Unit) { releases.refresh() }
    val r by releases.releases.collectAsState()
    val busy by releases.busy.collectAsState()
    val message by releases.message.collectAsState()
    val owner = r?.owner
    fun shown(info: app.duet.music.update.UpdateInfo?) = info?.versionName?.takeIf { it.isNotBlank() } ?: "—"
    Spacer(Modifier.height(8.dp))
    InfoRow("Owner", shown(owner))
    InfoRow("Testers", shown(r?.testers))
    InfoRow("Everyone", shown(r?.everyone))
    if (owner != null && owner.versionCode > 0) {
        if (owner.versionCode > (r?.testers?.versionCode ?: 0)) {
            NavRow(title = "Send ${owner.versionName} to testers", detail = if (busy) "Sending…" else "", icon = DuetIcons.Person) {
                releases.send(ReleaseControls.Target.Testers)
            }
        }
        if (owner.versionCode > (r?.everyone?.versionCode ?: 0)) {
            var confirm by remember(owner.versionCode) { mutableStateOf(false) }
            NavRow(
                title = if (confirm) "Tap again to release" else "Release ${owner.versionName} to everyone",
                detail = if (busy) "Sending…" else "",
                icon = DuetIcons.Download,
            ) { if (confirm) { releases.send(ReleaseControls.Target.Everyone); confirm = false } else confirm = true }
        }
    }
    if (message.isNotEmpty()) {
        Text(message, style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    }
}

@Composable
private fun Section(title: String) = Heading(title)

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = DuetType.RowTitle, modifier = Modifier.weight(1f))
        Text(value, style = DuetType.RowSub, color = DuetColors.TextDim)
    }
}

@Composable
private fun NavRow(title: String, detail: String, dot: Color? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            dot != null -> Box(Modifier.size(10.dp).background(dot, duetShape(2)))
            icon != null -> Icon(icon, null, tint = DuetColors.TextDim, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = DuetType.RowTitle)
            if (detail.isNotEmpty()) Text(detail, style = DuetType.RowSub, color = DuetColors.TextDim)
        }
        Icon(DuetIcons.ChevronRight, null, tint = DuetColors.TextDim, modifier = Modifier.size(18.dp))
    }
}

private fun capabilityLabel(c: Capability) = when (c) {
    Capability.Search -> "Search and play songs"
    Capability.Library -> "Your library"
    Capability.Radio -> "Radio"
    Capability.Lyrics -> "Lyrics"
    Capability.Download -> "Save songs to play offline"
    Capability.LiveRadio -> "Live radio stations"
}


/** Keep in step with docs/THIRD_PARTY.md and gradle/libs.versions.toml. */
private val NOTICES = listOf(
    "NewPipe Extractor" to "GPL-3.0",
    "Kotlin, Coroutines, Serialization" to "Apache-2.0",
    "Compose Multiplatform" to "Apache-2.0",
    "AndroidX Media3 (ExoPlayer)" to "Apache-2.0",
    "Ktor, OkHttp" to "Apache-2.0",
    "Coil" to "Apache-2.0",
    "Multiplatform Settings" to "Apache-2.0",
    "Instrument Sans" to "OFL-1.1",
)
