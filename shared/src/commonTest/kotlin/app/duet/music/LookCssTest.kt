package app.duet.music

import app.duet.music.settings.CustomLook
import app.duet.music.ui.theme.HomeLayout
import app.duet.music.ui.theme.LookCss
import app.duet.music.ui.theme.LookKnob
import app.duet.music.ui.theme.SeekStyle
import app.duet.music.ui.theme.styleOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LookCssTest {
    private val look = CustomLook("custom:abc", "Mine", "nothing", 0xFF1DB954,
        mapOf(LookKnob.Seek.name to SeekStyle.Line.name, LookKnob.Home.name to HomeLayout.Big.name, LookKnob.Ground.name to "forest"))

    @Test
    fun exportedCssParsesBackToTheSameStyle() {
        val back = assertNotNull(LookCss.parse(LookCss.export(look)))
        val a = styleOf(look); val b = styleOf(back)
        assertEquals("Mine", back.name)
        assertEquals(look.accent, back.accent)
        LookKnob.entries.forEach { assertEquals(it.valueIn(a), it.valueIn(b), it.label) }
    }

    @Test
    fun templateListsEverySettingWithItsChoices() {
        val css = LookCss.template()
        LookKnob.entries.forEach { k -> assertTrue("--" + k.label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') + ":" in css, k.label) }
        assertTrue("round" in css && "icons-words" in css)
    }

    @Test
    fun handWrittenCssTakesWhatItUnderstandsAndIgnoresTheRest() {
        val look = assertNotNull(LookCss.parse(":root{ --highlight:#f0a; --corners: square; --seek-bar: nonsense; --unknown: 1; --name: Pink }"))
        assertEquals(0xFFFF00AAL, look.accent)
        assertEquals("Pink", look.name)
        assertEquals(mapOf(LookKnob.Corners.name to "0"), look.changes)
    }

    @Test
    fun theEditorShowsEverySettingOnce() {
        val shown = (app.duet.music.ui.settings.Sections + app.duet.music.ui.settings.DesktopSections).flatMap { it.second }
        assertEquals(LookKnob.entries.toSet(), shown.toSet())
        assertEquals(shown.size, shown.toSet().size)
    }

    @Test
    fun textWithNoDuetSettingIsRejected() {
        assertNull(LookCss.parse("body { color: red; }"))
    }
}
