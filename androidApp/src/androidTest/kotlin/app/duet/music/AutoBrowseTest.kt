package app.duet.music

import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Browses Duet the way Android Auto does (framework MediaBrowser) and checks the tabs and hints. */
@RunWith(AndroidJUnit4::class)
class AutoBrowseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rootHasFourTabsAndSearchAndHomeHasItems() {
        val connected = CountDownLatch(1)
        lateinit var browser: MediaBrowser
        Handler(Looper.getMainLooper()).post {
            browser = MediaBrowser(context, ComponentName(context, "app.duet.music.service.PlaybackService"),
                object : MediaBrowser.ConnectionCallback() { override fun onConnected() = connected.countDown() }, null)
            browser.connect()
        }
        assertTrue("not connected", connected.await(20, TimeUnit.SECONDS))
        val root = browser.root
        val extras = browser.extras
        println("AUTO root=$root search=${extras?.getBoolean("android.media.browse.SEARCH_SUPPORTED")} browsable=${extras?.getInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT")}")
        assertEquals(true, extras?.getBoolean("android.media.browse.SEARCH_SUPPORTED"))

        val tabs = children(browser, root)
        println("AUTO tabs=" + tabs.map { it.description.title })
        assertEquals(listOf("Home", "Recent", "Library", "Up next"), tabs.map { it.description.title.toString() })

        Thread.sleep(8000) // let Home's shelves load
        val home = children(browser, "home")
        println("AUTO home=" + home.map { "${it.description.title} art=${it.description.iconUri != null}" })
        assertTrue("home empty", home.isNotEmpty())
        val first = children(browser, home.first().mediaId!!)
        println("AUTO first shelf items=" + first.take(5).map { "${it.description.title} browsable=${it.isBrowsable} art=${it.description.iconUri}" })
        assertTrue(first.isNotEmpty())
        val lib = children(browser, "library")
        println("AUTO library=" + lib.map { it.description.title })
        browser.disconnect()
    }

    private fun children(browser: MediaBrowser, id: String): List<MediaBrowser.MediaItem> {
        val done = CountDownLatch(1)
        var out: List<MediaBrowser.MediaItem> = emptyList()
        Handler(Looper.getMainLooper()).post {
            browser.subscribe(id, object : MediaBrowser.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowser.MediaItem>) {
                    out = children; done.countDown(); browser.unsubscribe(id)
                }
            })
        }
        done.await(30, TimeUnit.SECONDS)
        return out
    }
}
