package app.duet.music

import app.duet.music.settings.AppSettings
import app.duet.music.settings.CustomLook
import app.duet.music.ui.theme.HomeLayout
import app.duet.music.ui.theme.LookKnob
import app.duet.music.ui.theme.SeekStyle
import app.duet.music.ui.theme.StyleFont
import app.duet.music.ui.theme.customised
import app.duet.music.ui.theme.styleById
import app.duet.music.ui.theme.styleOf
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomLookTest {
    private val look = CustomLook("custom:abc", "Mine", "nothing", 0xFF1DB954,
        mapOf(LookKnob.Seek.name to SeekStyle.Line.name, LookKnob.Home.name to HomeLayout.Big.name, LookKnob.Ground.name to "forest"))

    @Test
    fun savedStylesSurviveRestartAndApplyOnTopOfTheirBase() {
        val store = MapSettings()
        AppSettings(store).apply { saveCustomLook(look); chooseStyle(look.id, look.accent) }
        val again = AppSettings(store)
        val style = styleById(again.style.value, again.customLooks.value)
        assertEquals("Mine", style.lookName)
        assertEquals(SeekStyle.Line, style.seek)
        assertEquals(HomeLayout.Big, style.home)
        assertEquals(StyleFont.Doto, style.titleFont) // untouched settings keep the base's own
        assertEquals("forest", LookKnob.Ground.valueIn(style))
        assertEquals(0xFF1DB954, again.accent.value)
    }

    @Test
    fun deletingTheStyleInUseFallsBackToTheDefault() {
        val s = AppSettings(MapSettings())
        s.saveCustomLook(look); s.chooseStyle(look.id, look.accent)
        s.deleteCustomLook(look.id)
        assertTrue(s.customLooks.value.isEmpty())
        assertEquals(AppSettings.DEFAULT_STYLE, s.style.value)
    }

    @Test
    fun damagedOrOddInputIsHarmless() {
        val names = CustomLook.decodeAll(CustomLook.encodeAll(listOf(look.copy(name = "a|b;c=d\nx")))).single().name
        assertTrue('|' !in names && '\n' !in names)
        assertEquals(1, CustomLook.decodeAll("garbage\n" + CustomLook.encodeAll(listOf(look)) + "\ncustom:x|y").size)
        val junk = styleById("studio").customised(mapOf(LookKnob.Seek.name to "Wobbly", "NoSuchKnob" to "x", LookKnob.Corners.name to "99"))
        assertEquals(styleById("studio").seek, junk.seek)
        assertEquals(2f, junk.roundness)
    }

    @Test
    fun everySettingRoundTripsThroughItsOptions() {
        LookKnob.entries.forEach { k ->
            k.options.forEach { (value, _) ->
                val st = styleOf(CustomLook("custom:t", "t", "studio", 0xFFFFFFFF, mapOf(k.name to value)))
                // A title font equal to the text font (Studio's Instrument Sans) is the same as "same as text".
                val expected = if (k == LookKnob.TitleFont && value == st.font.name) "same" else value
                assertEquals(expected, k.valueIn(st), "$k=$value")
            }
        }
    }
}
