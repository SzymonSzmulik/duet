package app.duet.music.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class AppUpdatesTest {
    private val feed = "﻿{“versionCode”:15,“versionName”:“0.12.0”,“apkUrl”:“https://x/a.apk”,“notes”:“New”}\r\n"

    @Test fun parsesGoogleDocExport() {
        assertEquals(UpdateInfo(15, "0.12.0", "https://x/a.apk", "New"), AppUpdates.parse(feed))
    }

    /** The state once a check against a feed serving [body] has finished. */
    private fun stateFor(current: Int, body: String = feed) = runBlocking {
        val updates = AppUpdates(HttpClient(MockEngine { respond(body) }), this, current, install = { _, _ -> }, feedUrl = "https://feed")
        updates.check()
        result = withTimeout(5_000) { updates.state.first { it != UpdateState.Idle && it != UpdateState.Checking } }
    }
    private var result: UpdateState? = null

    @Test fun newerBuildIsOffered() {
        stateFor(14)
        assertEquals(UpdateState.Available(AppUpdates.parse(feed)), result)
    }

    @Test fun sameBuildIsUpToDate() {
        stateFor(15)
        assertEquals(UpdateState.UpToDate, result)
    }

    @Test fun badFeedFails() {
        stateFor(14, "<html>")
        assertEquals(UpdateState.Failed("Couldn't check for updates"), result)
    }
}
