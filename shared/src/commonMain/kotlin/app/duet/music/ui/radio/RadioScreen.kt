package app.duet.music.ui.radio

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.height
import app.duet.music.ui.components.trackClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.domain.Track
import app.duet.music.sources.radio.RadioProvider
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.Chip
import app.duet.music.ui.components.EmptyState
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.components.pressScale
import app.duet.music.ui.search.SearchField
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.DuetDimens
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.delay

/**
 * Live radio: the most listened stations worldwide, genre chips, and a search by station name
 * (radio-browser.info). Tapping a station plays it; the rest of the list is the queue, so next and
 * previous move between stations.
 */
@Composable
fun RadioScreen(
    radio: RadioProvider,
    favourites: app.duet.music.sources.radio.FavouriteStations,
    playingKey: String?,
    onPlay: (List<Track>, Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val favs by favourites.list.collectAsState()
    var picking by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    // Starts on the phone's own country; null means worldwide.
    var country by rememberSaveable { mutableStateOf(androidx.compose.ui.text.intl.Locale.current.region.uppercase().takeIf { it.length == 2 }) }
    var countries by androidx.compose.runtime.remember { mutableStateOf<List<app.duet.music.sources.radio.Country>>(emptyList()) }
    LaunchedEffect(Unit) { countries = runCatching { radio.countries() }.getOrDefault(emptyList()) }
    var stations by androidx.compose.runtime.remember { mutableStateOf<List<Track>?>(null) }
    var failed by androidx.compose.runtime.remember { mutableStateOf(false) }
    LaunchedEffect(query, genre, country) {
        if (query.isNotBlank()) delay(350) // wait for the user to stop typing
        stations = null; failed = false
        stations = runCatching {
            when {
                query.isNotBlank() -> radio.search(query)
                else -> radio.find(genre, country)
            }
        }.onFailure { failed = true }.getOrDefault(emptyList())
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = contentPadding) {
        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Text(DuetType.titled("Radio"), style = DuetType.ScreenTitle)
                Text("Live stations from around the world", style = DuetType.RowSub, color = DuetColors.TextDim)
                SearchField(query, { query = it }, {}, Modifier.padding(top = 14.dp), placeholder = "Find a station")
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("Top", selected = genre == null && query.isBlank()) { genre = null; query = "" } }
                items(RadioProvider.GENRES) { g -> Chip(g.replaceFirstChar { it.uppercase() }, selected = genre == g && query.isBlank()) { genre = g; query = "" } }
            }
        }
        if (query.isBlank()) item {
            // One button for the region; tapping it opens a searchable list of every country.
            val name = countries.firstOrNull { it.code == country }?.name ?: country
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).heightIn(min = DuetDimens.MinTouchTarget.dp)
                    .clickable(role = Role.Button, onClickLabel = "Choose region") { picking = true },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (country == null) "\uD83C\uDF0D Worldwide" else flag(country!!) + " " + name, style = DuetType.Chip.copy(fontSize = 15.sp), color = DuetColors.Text, maxLines = 1)
                Icon(DuetIcons.ChevronDown, contentDescription = null, tint = DuetColors.TextDim, modifier = Modifier.size(16.dp))
            }
        }
        // Favourites on top, so a starred station is one tap away.
        if (query.isBlank() && favs.isNotEmpty()) {
            item { Text(DuetType.titled("Favourites"), style = DuetType.SectionTitle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            itemsIndexed(favs, key = { _, t -> "fav-" + t.key }) { i, t ->
                StationRow(t, playing = t.key == playingKey, favourite = true, onFavourite = { favourites.toggle(t) }) { onPlay(favs, i) }
            }
        }
        item {
            // The region button above already names the country; the heading stays short.
            Text(
                when {
                    query.isNotBlank() -> "Stations"
                    else -> genre?.replaceFirstChar { it.uppercase() } ?: "Most listened"
                }.let { DuetType.titled(it) },
                style = DuetType.SectionTitle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        val list = stations
        when {
            list == null -> item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DuetColors.Accent) } }
            list.isEmpty() -> item {
                EmptyState(
                    DuetIcons.Radio,
                    if (failed) "Couldn't reach the radio directory" else "No stations found",
                    if (failed) "Check your internet and try again." else "Try another name or genre.",
                )
            }
            else -> itemsIndexed(list, key = { _, t -> t.key }) { i, t ->
                // The country is already in the heading; leave it out of each row.
                val name = countries.firstOrNull { it.code == country }?.name
                val shown = if (name != null && query.isBlank()) t.copy(artist = t.artist.removePrefix(name).removePrefix(" · ")) else t
                StationRow(shown, playing = t.key == playingKey, favourite = favs.any { it.key == t.key }, onFavourite = { favourites.toggle(t) }) { onPlay(list, i) }
            }
        }
    }
    if (picking) RegionSheet(countries, country, onPick = { country = it; picking = false }, onDismiss = { picking = false })
}

/** Every country with stations, searchable; your own country and Worldwide first. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RegionSheet(countries: List<app.duet.music.sources.radio.Country>, selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    val mine = androidx.compose.ui.text.intl.Locale.current.region.uppercase()
    val ordered = countries.filter { it.code == mine } + countries.filter { it.code != mine }
    val shown = ordered.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) || it.code.equals(filter.trim(), ignoreCase = true) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DuetColors.SurfaceRaised, // lighter than the search field, so the field shows
        contentColor = DuetColors.Text,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(DuetType.titled("Choose region"), style = DuetType.SectionTitle, modifier = Modifier.padding(horizontal = 16.dp))
        SearchField(filter, { filter = it }, {}, Modifier.padding(16.dp), placeholder = "Search countries")
        LazyColumn(Modifier.fillMaxWidth().height(480.dp)) {
            if (filter.isBlank()) item { RegionRow("\uD83C\uDF0D", "Worldwide", null, selected == null) { onPick(null) } }
            items(shown, key = { it.code }) { c -> RegionRow(flag(c.code), c.name, "${c.stations} stations", selected == c.code) { onPick(c.code) } }
            if (countries.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DuetColors.Accent) } }
        }
    }
}

@Composable
private fun RegionRow(flag: String, name: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(flag, fontSize = 20.sp)
        Column(Modifier.weight(1f)) {
            Text(name, style = DuetType.RowTitle, color = if (selected) DuetColors.Accent else DuetColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, style = DuetType.RowSub, color = DuetColors.TextDim) }
        }
        if (selected) Icon(DuetIcons.Check, contentDescription = "Selected", tint = DuetColors.Accent, modifier = Modifier.size(20.dp))
    }
}

/** A country's flag emoji from its two-letter code ("GB" gives the UK flag). */
internal fun flag(code: String): String =
    if (code.length != 2 || !code.all { it in 'A'..'Z' }) "" else code.map { "\uD83C" + (0xDDE6 + (it - 'A')).toChar() }.joinToString("") // regional indicator letters

@Composable
private fun StationRow(t: Track, playing: Boolean, favourite: Boolean, onFavourite: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressScale().trackClick(t, onClick).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(52.dp).clip(duetShape(10)).background(DuetColors.Surface), contentAlignment = Alignment.Center) {
            Icon(DuetIcons.Radio, null, tint = DuetColors.TextDim, modifier = Modifier.size(22.dp))
            Artwork(t.artworkUrl, diagonal(0x00000000, 0x00000000), size = 52, radius = 10)
        }
        Column(Modifier.weight(1f)) {
            Text(t.title, style = DuetType.RowTitle, color = if (playing) DuetColors.Accent else DuetColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.artist.isNotEmpty()) Text(t.artist, style = DuetType.RowSub, color = DuetColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Every station is live; the tag marks only the one on air now.
        if (playing) Text(
            "ON AIR", fontSize = 9.sp, letterSpacing = 1.sp, color = DuetColors.Accent,
            modifier = Modifier.border(1.dp, DuetColors.Accent.copy(alpha = 0.6f), duetShape(4)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        androidx.compose.material3.IconButton(onClick = onFavourite, modifier = Modifier.size(44.dp)) {
            Icon(
                if (favourite) DuetIcons.HeartFilled else DuetIcons.Heart,
                contentDescription = if (favourite) "Remove ${t.title} from favourites" else "Add ${t.title} to favourites",
                tint = if (favourite) DuetColors.Accent else DuetColors.TextDim, modifier = Modifier.size(20.dp),
            )
        }
    }
}
