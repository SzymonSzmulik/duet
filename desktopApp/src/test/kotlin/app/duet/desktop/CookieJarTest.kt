package app.duet.desktop

import java.io.File
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CookieJarTest {
    private val music = URI.create("https://music.youtube.com/")

    @Test
    fun parsesACookieHeaderCopiedFromABrowser() {
        assertEquals(listOf("SAPISID" to "abc/def", "HSID" to "x=y"), CookieJar.parseCookieHeader("Cookie: SAPISID=abc/def; HSID=x=y; ; =bad"))
    }

    @Test
    fun pastedCookiesSignInAndSurviveARestart() {
        val file = File.createTempFile("duet-cookies", ".txt").also { it.delete(); it.deleteOnExit() }
        val jar = CookieJar(file)
        assertFalse(jar.has(music, "SAPISID", "HSID"))
        jar.addHeader("SAPISID=one; HSID=two; SID=three", ".youtube.com")
        assertTrue(jar.has(music, "SAPISID", "HSID"))
        val again = CookieJar(file)
        assertTrue(again.has(music, "SAPISID", "HSID"))
        assertEquals("SAPISID=one; HSID=two; SID=three".split("; ").toSet(), again.header(music)!!.split("; ").toSet())
        again.clear()
        assertFalse(CookieJar(file).has(music, "SAPISID"))
    }

    @Test
    fun signingOutOfOneSiteKeepsTheOthers() {
        val file = File.createTempFile("duet-cookies", ".txt").also { it.delete(); it.deleteOnExit() }
        val jar = CookieJar(file)
        jar.addHeader("sp_dc=abc", ".spotify.com")
        jar.addHeader("SAPISID=one; HSID=two", ".youtube.com")
        val spotify = URI.create("https://open.spotify.com/")
        assertTrue(jar.has(spotify, "sp_dc"))
        jar.removeDomain("spotify.com")
        assertFalse(jar.has(spotify, "sp_dc"))
        assertFalse(CookieJar(file).has(spotify, "sp_dc"))
        assertTrue(CookieJar(file).has(music, "SAPISID", "HSID"))
    }
}

class BrowserSignInTest {
    private val sample = """{"id":3,"result":{"cookies":[
        {"name":"SAPISID","value":"s1","domain":".youtube.com","path":"/","expires":4102444800.0,"httpOnly":false,"secure":true},
        {"name":"HSID","value":"h1","domain":".youtube.com","path":"/","expires":-1,"httpOnly":true,"secure":false},
        {"name":"old","value":"x","domain":".youtube.com","path":"/","expires":1.0,"httpOnly":false,"secure":true},
        {"value":"nameless","domain":".youtube.com"}]}}"""

    @Test
    fun readsTheCookiesABrowserReports() {
        val cookies = BrowserSignIn.parse(kotlinx.serialization.json.Json.parseToJsonElement(sample) as kotlinx.serialization.json.JsonObject)
        assertEquals(listOf("SAPISID", "HSID", "old"), cookies.map { it.name })
        assertTrue(cookies[1].httpOnly && !cookies[1].secure && cookies[1].expires < 0)
    }

    @Test
    fun aBrowsersCookiesSignInAndExpiredOnesAreLeftOut() {
        val file = File.createTempFile("duet-cookies", ".txt").also { it.delete(); it.deleteOnExit() }
        val jar = CookieJar(file)
        jar.addBrowser(BrowserSignIn.parse(kotlinx.serialization.json.Json.parseToJsonElement(sample) as kotlinx.serialization.json.JsonObject))
        val music = URI.create("https://music.youtube.com/")
        assertTrue(jar.has(music, "SAPISID", "HSID"))
        assertFalse(jar.has(music, "old"))
        assertTrue(CookieJar(file).has(music, "SAPISID", "HSID"))
    }
}
