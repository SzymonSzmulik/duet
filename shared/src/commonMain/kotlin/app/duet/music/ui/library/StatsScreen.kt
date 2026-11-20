package app.duet.music.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.domain.Track
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.EmptyState
import app.duet.music.ui.components.PageScaffold
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.components.trackClick
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType
import app.duet.music.ui.theme.duetShape

/** Your recap: most played songs and artists on this phone (from the play history). */
@Composable
fun StatsScreen(songs: List<Pair<Track, Int>>, artists: List<Pair<String, Int>>, playingKey: String?, onPlay: (List<Track>, Int) -> Unit, onBack: () -> Unit) {
    PageScaffold("Your stats", onBack, subtitle = "Kept on this phone") {
        if (songs.isEmpty()) {
            EmptyState(DuetIcons.Radio, "Nothing yet", "Play some songs and your favourites show up here.")
            return@PageScaffold
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(DuetType.titled("Top artists"), style = DuetType.SectionTitle, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            artists.forEachIndexed { i, (name, plays) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(DuetType.titled("${i + 1}"), style = DuetType.Display(18), color = DuetColors.Accent, modifier = Modifier.width(36.dp))
                    Text(name, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(plays(plays), fontSize = 12.sp, color = DuetColors.TextDim, textAlign = TextAlign.End)
                }
            }
            Text(DuetType.titled("Top songs"), style = DuetType.SectionTitle, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
            val list = songs.map { it.first }
            songs.forEachIndexed { i, (t, n) ->
                Row(
                    Modifier.fillMaxWidth().clip(duetShape(8)).trackClick(t) { onPlay(list, i) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(DuetType.titled("${i + 1}"), style = DuetType.Display(16), color = DuetColors.TextDim, modifier = Modifier.width(24.dp))
                    Artwork(t.artworkUrl, diagonal(0xFF2A2622, 0xFF141210), size = 44, radius = 6)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = DuetType.RowTitle.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (t.key == playingKey) DuetColors.Accent else DuetColors.Text)
                        Text(t.artist, style = DuetType.RowSub.copy(fontSize = 12.sp), color = DuetColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(plays(n), fontSize = 12.sp, color = DuetColors.TextDim)
                }
            }
        }
    }
}

private fun plays(n: Int) = if (n == 1) "1 play" else "$n plays"
