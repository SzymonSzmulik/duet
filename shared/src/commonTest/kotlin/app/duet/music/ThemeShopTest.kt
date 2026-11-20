package app.duet.music

import app.duet.music.settings.AppSettings
import app.duet.music.settings.CustomLook
import app.duet.music.shop.ThemeShop
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeShopTest {
    private val look = CustomLook("custom:abc", "Red mono", "nothing", 0xFFD71921, mapOf("Seek" to "Line", "Wash" to "0.6"))

    @Test
    fun shareCodeRoundTripsWithoutIdOrName() {
        val code = CustomLook.shareCode(look)
        assertEquals("nothing|${0xFFD71921}|Seek=Line;Wash=0.6", code)
        val back = CustomLook.fromShareCode("custom:new", "Red mono", code)!!
        assertEquals(look.copy(id = "custom:new"), back)
        assertNull(CustomLook.fromShareCode("x", "y", "not a code"))
    }

    @Test
    fun listSkipsBrokenEntries() {
        val list = ThemeShop.parseList("""{"ok":true,"themes":[{"id":"a1","name":"One","author":"sam","code":"studio|1|","likes":3},{"name":"no id"}]}""")
        assertEquals(1, list.size)
        assertEquals(3, list.single().likes)
    }

    @Test
    fun sharingSendsNameKeyAndCodeButNeverAGoogleSignIn() {
        val asked = mutableListOf<Map<String, String>>()
        val http = HttpClient(MockEngine { req ->
            asked += req.url.parameters.names().associateWith { req.url.parameters[it]!! }
            respond(if (req.url.parameters["action"] == "list") """{"ok":true,"themes":[]}""" else """{"ok":true}""")
        })
        val settings = AppSettings(MapSettings())
        // runBlocking returns once every job the shop launched in it has finished.
        kotlinx.coroutines.runBlocking {
            val shop = ThemeShop(http, settings, this, canShare = { true }, url = "https://example.test/exec")
            shop.chooseName("sam") { shop.share(look) }
        }
        assertEquals("sam", settings.shopName.value)
        val publish = asked.first { it["action"] == "publish" }
        assertEquals(setOf("action", "username", "key", "name", "code", "description", "tags"), publish.keys)
        assertEquals(settings.shopKey, publish["key"])
        assertTrue(settings.shopKey.length >= 24)
    }

    @Test
    fun cantShareSignedOutAndTryInstallsTheStyle() {
        val http = HttpClient(MockEngine { respond("""{"ok":true}""") })
        val settings = AppSettings(MapSettings())
        lateinit var shop: ThemeShop
        kotlinx.coroutines.runBlocking {
            shop = ThemeShop(http, settings, this, canShare = { false }, url = "https://example.test/exec")
            shop.share(look)
        }
        assertEquals("Sign in to YouTube Music to share", shop.message.value)
        assertTrue(shop.install(app.duet.music.shop.SharedStyle("q9", "Red mono", "sam", CustomLook.shareCode(look), 0)))
        assertEquals("custom:sq9", settings.style.value)
        assertFalse(settings.customLooks.value.isEmpty())
    }
}
