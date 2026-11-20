package app.duet.music.ui.theme

import app.duet.music.settings.CustomLook

/**
 * A style as CSS custom properties: the text people copy out of the style editor, hand-edit or give to an AI,
 * and paste back in. Written from [LookKnob], so every setting of the editor is in it and it can't drift.
 * Values are the option's name in lower case ("round", "icons-words"); the stored option key also parses.
 */
object LookCss {
    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    private fun nameOf(k: LookKnob) = "--" + slug(k.label)
    private fun hex(argb: Long) = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')

    /** The style as CSS, every setting with its choices in a comment (so it doubles as the template). */
    fun export(look: CustomLook): String {
        val style = styleOf(look)
        return buildString {
            appendLine("/* Duet style. Change the values; keep the names. */")
            appendLine(":root {")
            appendLine("  --name: ${look.name};")
            appendLine("  --base: ${look.base}; /* ${StyleTemplates.joinToString(" | ") { it.id }} */")
            appendLine("  --highlight: ${hex(look.accent)}; /* any #rrggbb colour */")
            LookKnob.entries.forEach { k ->
                val now = k.options.firstOrNull { it.first == k.valueIn(style) }?.second ?: k.options.first().second
                appendLine("  ${nameOf(k)}: ${slug(now)}; /* ${k.options.joinToString(" | ") { slug(it.second) }} */")
            }
            append("}")
        }
    }

    /** The blank template: the default style, written out. */
    fun template(): String {
        val base = StyleTemplates.first()
        return export(CustomLook("", "My style", base.id, base.accent, emptyMap()))
    }

    /** What to paste into any AI chat; [template] shows it the exact format. */
    fun aiPrompt(): String =
        "Design a look for my music app Duet. Reply with only the CSS below, with the values changed to fit the look. " +
            "Use only the choices listed in each comment, and any #rrggbb colour for --highlight. " +
            "The look I want: <describe it here>\n\n" + template()

    /** A style from pasted CSS (id left for the caller to set); null when nothing in it is a Duet setting. */
    fun parse(text: String): CustomLook? {
        val props = Regex("--([a-zA-Z0-9-]+)\\s*:\\s*([^;}]*)")
            .findAll(text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), ""))
            .associate { it.groupValues[1].lowercase() to it.groupValues[2].trim().trim('"', '\'') }
        val base = StyleTemplates.firstOrNull { it.id == props["base"]?.lowercase() } ?: StyleTemplates.first()
        val changes = LookKnob.entries.mapNotNull { k ->
            val v = props[nameOf(k).removePrefix("--")]?.lowercase() ?: return@mapNotNull null
            k.options.firstOrNull { it.first.lowercase() == v || slug(it.second) == v }?.let { k.name to it.first }
        }.toMap()
        val accent = props["highlight"]?.let(::colour) ?: base.accent
        if (changes.isEmpty() && props["highlight"] == null && props["base"] == null) return null
        return CustomLook("", CustomLook.cleanName(props["name"].orEmpty()), base.id, accent, changes)
    }

    private fun colour(raw: String): Long? {
        val h = raw.removePrefix("#")
        if (!h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val full = when (h.length) { 3 -> h.map { "$it$it" }.joinToString(""); 6 -> h; 8 -> h.takeLast(6); else -> return null }
        return 0xFF000000L or full.toLong(16)
    }
}
