package app.duet.music.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseControlsTest {
    private val feeds = mutableMapOf(
        "owner" to """{"versionCode":16,"versionName":"0.13.0","apkUrl":"o","testerApkUrl":"t","releaseApkUrl":"r","controlUrl":"https://script/exec"}""",
        "tester" to """{"versionCode":15,"versionName":"0.12.0","apkUrl":"x"}""",
        "release" to """{"versionCode":15,"versionName":"0.12.0","apkUrl":"y"}""",
    )
    private val calls = mutableListOf<String>()

    private fun controls(key: String = "k") = ReleaseControls(
        HttpClient(MockEngine { req ->
            val host = req.url.host
            if (host == "script") {
                calls += req.url.parameters.toString()
                // The real script copies the owner version into the tester feed.
                feeds["tester"] = """{"versionCode":16,"versionName":"0.13.0","apkUrl":"t"}"""
                respond("""{"ok":true}""")
            } else respond(feeds.getValue(host))
        }),
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
        ownerFeed = "https://owner/", testerFeed = "https://tester/", releaseFeed = "https://release/", key = key,
    )

    @Test fun showsEachFeedAndSendsToTesters() = runBlocking {
        val c = controls()
        c.refresh()
        val r = withTimeout(5_000) { c.releases.first { it != null } }!!
        assertEquals(listOf("0.13.0", "0.12.0", "0.12.0"), listOf(r.owner?.versionName, r.testers?.versionName, r.everyone?.versionName))

        c.send(ReleaseControls.Target.Testers)
        assertEquals("Sent 0.13.0 to testers", withTimeout(5_000) { c.message.first { it.isNotEmpty() } })
        assertEquals("Parameters [key=[k], action=[testers]]", calls.single())
        assertEquals("0.13.0", c.releases.value?.testers?.versionName)
    }

    @Test fun withoutKeyNothingIsSent() = runBlocking {
        val c = controls(key = "")
        c.refresh()
        withTimeout(5_000) { c.releases.first { it != null } }
        c.send(ReleaseControls.Target.Everyone)
        assertEquals("Release buttons aren't set up yet", withTimeout(5_000) { c.message.first { it.isNotEmpty() } })
        assertEquals(emptyList(), calls)
    }
}
