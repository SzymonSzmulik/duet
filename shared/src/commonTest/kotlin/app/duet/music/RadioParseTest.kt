package app.duet.music

import app.duet.music.sources.radio.RadioProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RadioParseTest {
    @Test
    fun keepsPlainStreamsAndSkipsHlsAndBroken() {
        val body = """[
          {"stationuuid":"a","name":" Jazz FM ","url_resolved":"https://x/jazz.mp3","favicon":"https://x/j.png","tags":"jazz,smooth jazz,soul","country":"United Kingdom","bitrate":128,"hls":0},
          {"stationuuid":"b","name":"HLS One","url_resolved":"https://x/live.m3u8","hls":1},
          {"stationuuid":"c","name":"No Stream","url_resolved":"","hls":0},
          {"stationuuid":"d","name":"jazz fm","url_resolved":"https://y/dup.mp3","hls":0},
          {"stationuuid":"e","name":"Plain","url_resolved":"http://z/a.aac","favicon":"http://z/i.png","tags":"","country":"","bitrate":0,"hls":0}
        ]"""
        val s = RadioProvider.parseStations(body)
        assertEquals(listOf("Jazz FM", "Plain"), s.map { it.title })
        assertEquals("United Kingdom · jazz · smooth jazz · 128 kbps", s[0].artist)
        assertEquals("https://x/j.png", s[0].artworkUrl)
        assertNull(s[0].durationMs)
        assertNull(s[1].artworkUrl, "plain-http icons are not loaded")
        assertEquals("", s[1].artist)
    }

    @Test
    fun badJsonGivesNoStations() = assertEquals(0, RadioProvider.parseStations("<html>").size)

    @Test
    fun countriesKeepCodedOnesMostStationsFirst() {
        val body = """[
          {"name":"Tiny","iso_3166_1":"TT","stationcount":3},
          {"name":"Germany","iso_3166_1":"DE","stationcount":3000},
          {"name":"No code","iso_3166_1":"","stationcount":500},
          {"name":"The United Kingdom","iso_3166_1":"gb","stationcount":900}
        ]"""
        assertEquals(listOf("DE", "GB"), RadioProvider.parseCountries(body).map { it.code })
    }

    @Test
    fun flagFromCountryCode() {
        assertEquals("\uD83C\uDDEC\uD83C\uDDE7", app.duet.music.ui.radio.flag("GB"))
        assertEquals("", app.duet.music.ui.radio.flag("g"))
    }
}
