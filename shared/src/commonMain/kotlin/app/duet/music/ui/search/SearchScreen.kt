package app.duet.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.domain.Track
import app.duet.music.extension.SourceRegistry
import app.duet.music.search.SearchModel
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.trackClick
import app.duet.music.ui.components.SourceLine
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.DuetDimens
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType
import app.duet.music.ui.components.EmptyState
import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.ui.components.Chip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import app.duet.music.extension.Capability
import app.duet.music.extension.SourceProvider
import app.duet.music.ui.components.HintCard
import app.duet.music.ui.components.ScreenHeader

@Composable
fun SearchScreen(
    model: SearchModel,
    sources: SourceRegistry,
    playingKey: String?,
    onPlay: (results: List<Track>, index: Int) -> Unit,
    onAddToQueue: (Track) -> Unit,
    contentPadding: PaddingValues,
    onOpenCollection: (Collection) -> Unit = {},
    /** Songs played before, shown on the Search tab before searching. */
    recent: List<Track> = emptyList(),
    /** Past searches, shown in the search screen before typing. */
    history: List<String> = emptyList(),
    onHistoryRemove: (String) -> Unit = {},
    onHistoryClear: () -> Unit = {},
    /** A search was run (keyboard search or a past search tapped): remember it. */
    onSearched: (String) -> Unit = {},
    /** Search screen open (typing) or the Search tab's start page. */
    active: Boolean = true,
    onActive: (Boolean) -> Unit = {},
) {
    val state by model.state.collectAsState()
    val focus = LocalFocusManager.current
    if (!active) {
        SearchStart(recent, playingKey, contentPadding, onOpen = { onActive(true) }, onPlay = onPlay)
        return
    }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { if (state.query.isBlank()) runCatching { focusRequester.requestFocus() } }

    // The source switch only makes sense with two or more sources that can search and play (signed in, or needing no account).
    val searchable = sources.providers.filter { Capability.Search in it.manifest.capabilities }
        .filter { it.account?.signedIn?.collectAsState()?.value /* recompose on sign-in */; it.playableNow }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = DuetDimens.ScreenPaddingX.dp, end = DuetDimens.ScreenPaddingX.dp, top = 16.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { focus.clearFocus(); onActive(false) }, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
                    Icon(DuetIcons.Back, contentDescription = "Close search", tint = DuetColors.Text)
                }
                SearchField(
                    query = state.query,
                    onChange = model::onQueryChange,
                    onSubmit = { onSearched(state.query); model.submit(); focus.clearFocus() },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
            }
        }
        if (searchable.size > 1 && state.query.isNotBlank()) {
            item { SourceSwitch(searchable, state.only, model::onSourceFilter, Modifier.padding(top = 14.dp)) }
        }
        // What to look for: songs, albums, artists or playlists (or a bit of each).
        if (state.query.isNotBlank()) item {
            Row(
                Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("All", selected = state.kind == null) { model.onKind(null) }
                SearchKind.entries.forEach { k -> Chip(k.label, selected = state.kind == k) { model.onKind(k) } }
            }
        }
        if (state.query.isNotBlank() && state.suggestions.isNotEmpty()) item {
            Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.suggestions.forEach { s ->
                    Row(
                        Modifier.clip(duetShape(16)).border(1.dp, DuetColors.Line, duetShape(16))
                            .clickable(role = Role.Button, onClickLabel = "Search for $s") { model.searchFor(s); onSearched(s) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(DuetIcons.Search, null, tint = DuetColors.TextDim, modifier = Modifier.size(14.dp))
                        Text(s, style = DuetType.Chip, color = DuetColors.Text, maxLines = 1)
                    }
                }
            }
        }
        item { Box(Modifier.height(8.dp)) }

        if (state.loading && state.results.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DuetColors.Accent, modifier = Modifier.semantics { contentDescription = "Searching" })
                }
            }
        }
        state.errors.forEach { (sourceId, message) ->
            item(key = "error-$sourceId") {
                val p = sources[sourceId]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(duetShape(DuetDimens.RadiusCard))
                        .background(DuetColors.Surface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${p?.manifest?.name ?: sourceId} didn't answer. Check your internet, then try again. ($message)", style = DuetType.RowSub,
                        color = DuetColors.Text, modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Try again", style = DuetType.Chip, color = DuetColors.Accent,
                        modifier = Modifier.clip(duetShape(DuetDimens.RadiusChip))
                            .clickable(role = Role.Button, onClick = model::retry).padding(12.dp),
                    )
                }
            }
        }
        if (!state.loading && state.query.isNotBlank() && state.results.isEmpty() && state.videos.isEmpty() && state.collections.isEmpty() && state.errors.isEmpty()) {
            item {
                EmptyState(
                    icon = DuetIcons.Search,
                    title = "No results for \"${state.query.trim()}\"",
                    body = "Check the spelling or try another search.",
                )
            }
        }
        if (state.kind == null && state.results.isNotEmpty() && state.collections.isNotEmpty()) {
            item { Text(DuetType.titled("Songs"), style = DuetType.SectionTitle, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
        }
        if (state.query.isBlank()) {
            if (history.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(DuetType.titled("Recent searches"), style = DuetType.SectionTitle, modifier = Modifier.weight(1f))
                    Text("Clear", style = DuetType.Chip, color = DuetColors.TextDim,
                        modifier = Modifier.clip(duetShape(8)).clickable(role = Role.Button, onClick = onHistoryClear).padding(8.dp))
                }
            }
            items(history, key = { "q-$it" }) { q ->
                Row(
                    Modifier.fillMaxWidth().clip(duetShape(8))
                        .clickable(role = Role.Button, onClickLabel = "Search") { onSearched(q); model.searchFor(q); focus.clearFocus() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DuetIcons.Search, contentDescription = null, tint = DuetColors.TextDim, modifier = Modifier.padding(end = 14.dp).size(18.dp))
                    Text(q, style = DuetType.RowTitle.copy(fontWeight = FontWeight.Normal), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onHistoryRemove(q) }, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
                        Icon(DuetIcons.Clear, contentDescription = "Remove $q", tint = DuetColors.TextDim, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        itemsIndexed(state.results, key = { _, t -> t.key }) { i, track ->
            val p = sources[track.sourceId]
            ResultRow(
                track = track,
                sourceName = p?.manifest?.name ?: track.sourceId,
                sourceColor = Color(p?.color ?: 0xFF9AA0B4),
                playing = track.key == playingKey,
                onClick = { focus.clearFocus(); onPlay(state.results, i) },
                onAdd = { onAddToQueue(track) },
            )
        }
        if (state.videos.isNotEmpty()) {
            if (state.kind == null) item(key = "h-videos") {
                Text(DuetType.titled("Videos"), style = DuetType.SectionTitle, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
            itemsIndexed(state.videos, key = { _, t -> "v-" + t.key }) { i, track ->
                val p = sources[track.sourceId]
                ResultRow(
                    track = track, sourceName = p?.manifest?.name ?: track.sourceId, sourceColor = Color(p?.color ?: 0xFF9AA0B4),
                    playing = track.key == playingKey, label = "Video",
                    onClick = { focus.clearFocus(); onPlay(state.videos, i) }, onAdd = { onAddToQueue(track) },
                )
            }
        }
        state.collections.forEach { (kind, list) ->
            if (state.kind == null) item(key = "h-$kind") {
                Text(DuetType.titled(kind.label), style = DuetType.SectionTitle, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
            items(list, key = { "c-" + it.key }) { c ->
                val p = sources[c.sourceId]
                CollectionRow(c, p?.manifest?.name ?: c.sourceId, Color(p?.color ?: 0xFF9AA0B4)) { focus.clearFocus(); onOpenCollection(c) }
            }
        }
    }
}

@Composable
private fun CollectionRow(c: Collection, sourceName: String, sourceColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(duetShape(DuetDimens.RadiusTile))
            .clickable(role = Role.Button, onClickLabel = "Open", onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Artists are round, albums and playlists square (the usual music-app cue).
        val radius = if (c.kind == SearchKind.Artists) DuetDimens.RowArt / 2 else DuetDimens.RadiusTile
        Artwork(c.artworkUrl, diagonal(0xFF3A3A3A, 0xFF111111), size = DuetDimens.RowArt, radius = radius)
        Column(Modifier.weight(1f)) {
            Text(c.title, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SourceLine(listOf(c.subtitle, sourceName).filter { it.isNotBlank() }.joinToString(" · "))
        }
        Icon(DuetIcons.ChevronRight, contentDescription = null, tint = DuetColors.TextDim, modifier = Modifier.size(18.dp))
    }
}

/** The Search tab before searching: a search bar that opens the search screen, then songs played before. */
@Composable
private fun SearchStart(recent: List<Track>, playingKey: String?, contentPadding: PaddingValues, onOpen: () -> Unit, onPlay: (List<Track>, Int) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = contentPadding.calculateBottomPadding()),
    ) {
        item { ScreenHeader("Search") }
        item {
            Row(
                Modifier.padding(top = 14.dp, bottom = 8.dp).fillMaxWidth().height(48.dp).clip(duetShape(10))
                    .background(DuetColors.Surface).clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(DuetIcons.Search, null, tint = DuetColors.TextDim, modifier = Modifier.size(20.dp))
                Text("Songs, artists or albums", style = DuetType.RowTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = DuetColors.TextDim)
            }
        }
        if (recent.isNotEmpty()) item { Text(DuetType.titled("Recently played"), style = DuetType.SectionTitle, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
        itemsIndexed(recent, key = { _, t -> "r-" + t.key }) { i, t ->
            Row(
                Modifier.fillMaxWidth().clip(duetShape(DuetDimens.RadiusTile))
                    .trackClick(t) { onPlay(recent, i) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(t.artworkUrl, diagonal(0xFF3A3A3A, 0xFF111111), size = DuetDimens.RowArt, radius = DuetDimens.RadiusTile)
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (t.key == playingKey) DuetColors.Accent else DuetColors.Text)
                    Text(t.artist, style = DuetType.RowSub, color = DuetColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (recent.isEmpty()) item {
            Text("Songs you play show up here.", style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun Tip(title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(6.dp).background(DuetColors.Accent, CircleShape))
        Column {
            Text(title, style = DuetType.RowTitle.copy(fontSize = 14.sp))
            Text(detail, style = DuetType.RowSub, color = DuetColors.TextDim)
        }
    }
}

@Composable
internal fun SearchField(query: String, onChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier, placeholder: String = "Songs, artists or albums") {
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(duetShape(10)).background(DuetColors.Surface).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(DuetIcons.Search, null, tint = DuetColors.TextDim, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, style = DuetType.RowTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = DuetColors.TextDim)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = DuetType.RowTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DuetColors.Text),
                cursorBrush = SolidColor(DuetColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search" },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
                Icon(DuetIcons.Clear, contentDescription = "Clear search", tint = DuetColors.TextDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** The same quiet Both / YT Music / Spotify switch as Home and Library. */
@Composable
private fun SourceSwitch(sources: List<SourceProvider>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) { app.duet.music.ui.components.ServiceSwitch(sources.map { Triple(it.manifest.id, it.shortName, it.color) }, selected, onSelect) }
}

@Composable
private fun ResultRow(track: Track, sourceName: String, sourceColor: Color, playing: Boolean, onClick: () -> Unit, onAdd: () -> Unit, label: String = "Song") {
    Row(
        Modifier.fillMaxWidth().clip(duetShape(DuetDimens.RadiusTile))
            .trackClick(track, onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(track.artworkUrl, diagonal(0xFF3A3A3A, 0xFF111111), size = DuetDimens.RowArt, radius = DuetDimens.RadiusTile)
        Column(Modifier.weight(1f)) {
            Text(
                track.title, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) DuetColors.Accent else DuetColors.Text,
            )
            SourceLine(listOf(label, track.artist, sourceName).filter { it.isNotBlank() }.joinToString(" · "))
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
            Icon(DuetIcons.Add, contentDescription = "Add ${track.title} to Up next", tint = DuetColors.TextDim, modifier = Modifier.size(20.dp))
        }
    }
}
