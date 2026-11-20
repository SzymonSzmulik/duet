package app.duet.music.downloads

import app.duet.music.domain.Track
import app.duet.music.lyrics.LyricsService
import kotlin.math.abs

/**
 * Finds the same song in another service's search results, for saving a song whose own service
 * can't be downloaded (Spotify) from one that can (YouTube Music). Title and first artist must match
 * after cleanup ("(feat. …)", "- Remastered"); the length must be within [TOLERANCE_MS] when both are
 * known; the closest length wins.
 */
object SongMatch {
    const val TOLERANCE_MS = 7_000L

    fun best(target: Track, candidates: List<Track>): Track? {
        val title = norm(LyricsService.cleanTitle(target.title))
        val artist = norm(LyricsService.firstArtist(target.artist))
        return candidates.filter { c ->
            val ct = norm(LyricsService.cleanTitle(c.title))
            val ca = norm(c.artist)
            ct.isNotEmpty() && (ct == title || ct.contains(title) || title.contains(ct)) &&
                artist.isNotEmpty() && ca.contains(artist) &&
                (target.durationMs == null || c.durationMs == null || abs(target.durationMs - c.durationMs) <= TOLERANCE_MS)
        }.minByOrNull { c ->
            val lengthGap = if (target.durationMs != null && c.durationMs != null) abs(target.durationMs - c.durationMs) else TOLERANCE_MS
            // An exact title beats a longer one ("Song" over "Song (Live)").
            lengthGap + if (norm(LyricsService.cleanTitle(c.title)) == title) 0 else 10_000
        }
    }

    /** What to type into the other service's search. */
    fun query(target: Track): String = "${LyricsService.cleanTitle(target.title)} ${LyricsService.firstArtist(target.artist)}"

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
