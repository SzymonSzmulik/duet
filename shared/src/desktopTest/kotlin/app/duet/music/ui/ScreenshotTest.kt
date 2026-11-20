package app.duet.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.duet.music.DuetGraph
import app.duet.music.FakePlayer
import app.duet.music.FakeSource
import app.duet.music.extension.SourceRegistry
import app.duet.music.settings.AppSettings
import app.duet.music.track
import app.duet.music.ui.components.Tab
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders screens at the design artboard size (390x844 dp, density 2) to
 * shared/build/screenshots/ so they can be compared with design/screens without a phone.
 * Uses fake sources: no network.
 */
class ScreenshotTest {
    private val out = File("build/screenshots").apply { mkdirs() }

    private fun render(name: String, width: Int = 780, height: Int = 1688, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(2f), content = content)
        try {
            // Let fonts and first-frame effects settle.
            repeat(5) { scene.render(it * 100_000_000L) }
            val png = scene.render(600_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "$name.png").writeBytes(png)
        } finally {
            scene.close()
        }
        assertTrue(File(out, "$name.png").length() > 0)
    }

    private val ytId = "app.duet.source.ytmusic"
    private val yt = FakeSource(
        ytId, shortName = "YT Music", color = 0xFFFF6B6B,
        results = listOf(
            track(ytId, "1", "Teeth", "Marlowe Finch"),
            track(ytId, "2", "teeth · slowed", "Marlowe Finch"),
            track(ytId, "3", "The Moon, Again", "Paper Lanterns"),
        ),
    )

    @Test
    fun screens() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            val graph = DuetGraph(SourceRegistry(listOf(yt)), listOf(FakePlayer()), scope, AppSettings(MapSettings()), "0.2.0")
            graph.settings.setFlag(app.duet.music.settings.Flag.TutorialSeen, true)
            Tab.entries.forEach { tab -> render("tab-${tab.name.lowercase()}") { App(graph, initialTab = tab) } }
            render("settings") { App(graph, initialPages = listOf(Page.Settings)) }
            render("source-ytmusic") { App(graph, initialPages = listOf(Page.Settings, Page.Source(ytId))) }

            graph.search.onQueryChange("teeth")
            graph.search.submit()
            graph.player.playAll(kotlinx.coroutines.runBlocking { yt.search("") }, 0)
            render("search-results-playing") { App(graph, initialTab = Tab.Search) }
            render("now-playing") { App(graph, initialPages = listOf(Page.NowPlaying)) }

            graph.settings.setAccent(AppSettings.ACCENTS[1])
            render("now-playing-violet") { App(graph, initialPages = listOf(Page.NowPlaying)) }

            // Every style, Home and Now Playing.
            app.duet.music.ui.theme.DuetStyles.forEach { st ->
                graph.settings.chooseStyle(st.id, st.accent)
                render("style-${st.id}-home") { App(graph, initialTab = Tab.Home) }
                render("style-${st.id}-player") { App(graph, initialPages = listOf(Page.NowPlaying)) }
            }
            graph.settings.setLightMode(true)
            graph.settings.chooseStyle("nothing", app.duet.music.ui.theme.styleById("nothing").accent)
            render("style-nothing-light-home") { App(graph, initialTab = Tab.Home) }
            graph.settings.setLightMode(false)
            // A highlight colour of your own reaches the glow, tabs and play button.
            graph.settings.setAccent(0xFF38BDF8)
            render("style-nothing-blue-home") { App(graph, initialTab = Tab.Home) }
            render("style-nothing-blue-player") { App(graph, initialPages = listOf(Page.NowPlaying)) }
            render("appearance") { App(graph, initialPages = listOf(Page.Settings, Page.SettingsGroup(app.duet.music.ui.settings.SettingsGroup.Appearance))) }
            // The desktop layout: sidebar and player bar around the same screens, in a 1180x760 window.
            render("desktop-home", width = 2360, height = 1520) { App(graph, initialTab = Tab.Home, desktop = true) }
            render("desktop-style-editor", width = 2360, height = 1520) { App(graph, initialPages = listOf(Page.StyleEditor(null)), desktop = true) }
            render("desktop-search", width = 2360, height = 1520) { App(graph, initialTab = Tab.Search, desktop = true) }
            render("desktop-library", width = 2360, height = 1520) { App(graph, initialTab = Tab.Library, desktop = true) }
            render("desktop-radio", width = 2360, height = 1520) { App(graph, initialTab = Tab.Radio, desktop = true) }
            render("desktop-settings", width = 2360, height = 1520) { App(graph, initialPages = listOf(Page.Settings), desktop = true) }
            render("desktop-player", width = 2360, height = 1520) { App(graph, initialTab = Tab.Home, initialPages = listOf(Page.NowPlaying), desktop = true) }
            // An ultrawide monitor (3440x1440 points).
            render("desktop-player-ultrawide", width = 3440, height = 1440) { App(graph, initialTab = Tab.Home, initialPages = listOf(Page.NowPlaying), desktop = true) }
            render("desktop-home-ultrawide", width = 3440, height = 1440) { App(graph, initialTab = Tab.Home, desktop = true) }
            render("style-editor") { App(graph, initialPages = listOf(Page.StyleEditor(null))) }
            // Templates and a shared style's page (fake list, no network).
            listOf("shelf", "night", "mono", "glass").forEach { id ->
                graph.settings.chooseStyle(id, app.duet.music.ui.theme.styleById(id).accent)
                render("template-$id-home") { App(graph, initialTab = Tab.Home) }
            }
        } finally {
            scope.cancel()
        }
    }
}
