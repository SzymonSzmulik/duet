package app.duet.music.sources.ytmusic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SapisidHashTest {
    @Test
    fun hashesSapisidWithTimeAndOrigin() {
        assertEquals(
            "SAPISIDHASH 1700000000_306cf93f5dc9976aad0a8db4a6360d40a7578732",
            InnerTubeYtMusicSource.sapisidHash("HSID=x; SAPISID=abc/XYZ; SID=y", "https://music.youtube.com", nowSeconds = 1700000000),
        )
    }

    @Test
    fun noSapisidNoHeader() = assertNull(InnerTubeYtMusicSource.sapisidHash("HSID=x", "https://music.youtube.com"))
}
