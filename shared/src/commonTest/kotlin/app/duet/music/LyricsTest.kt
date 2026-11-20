package app.duet.music

import app.duet.music.lyrics.LyricsService
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsTest {
    @Test
    fun parsesLrcWithSeveralTimesAndInstrumentals() {
        val lines = LyricsService.parseLrc("[00:01.50]One\n[00:03.2][00:10.00]Two\n[00:05.00]\n[00:06.00]\n[00:08.123]Three")
        assertEquals(listOf(1500L, 3200L, 5000L, 8123L, 10000L), lines.map { it.timeMs })
        assertEquals(listOf("One", "Two", "♪", "Three", "Two"), lines.map { it.text })
    }

    @Test
    fun findsCurrentLine() {
        val lines = LyricsService.parseLrc("[00:01.00]a\n[00:02.00]b\n[00:03.00]c")
        assertEquals(-1, LyricsService.currentLine(lines, 500))
        assertEquals(1, LyricsService.currentLine(lines, 2500))
        assertEquals(2, LyricsService.currentLine(lines, 99_000))
    }

    @Test
    fun cleansTitlesAndArtists() {
        assertEquals("Get Lucky", LyricsService.cleanTitle("Get Lucky (feat. Pharrell Williams and Nile Rodgers)"))
        assertEquals("Instant Crush", LyricsService.cleanTitle("Instant Crush [Official Video]"))
        assertEquals("Daft Punk", LyricsService.firstArtist("Daft Punk & Julian Casablancas"))
    }
}

class FormatBytesTest {
    @Test
    fun formatsSizes() {
        assertEquals("1.9 MB", app.duet.music.ui.settings.formatBytes(2_061_125))
        assertEquals("512 KB", app.duet.music.ui.settings.formatBytes(524_288))
    }
}
