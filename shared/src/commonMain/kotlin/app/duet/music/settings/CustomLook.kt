package app.duet.music.settings

/**
 * A style someone made in the style editor: a built-in style ([base]) with their [changes] (setting name to
 * option, see ui.theme.LookKnob), their highlight colour and a name. Stored on the phone in [AppSettings].
 */
data class CustomLook(
    val id: String,
    val name: String,
    val base: String,
    val accent: Long,
    val changes: Map<String, String>,
) {
    companion object {
        const val ID_PREFIX = "custom:"

        /** Names are what people typed: trimmed, short, and free of the characters the storage uses. */
        fun cleanName(raw: String): String = raw.replace(Regex("[|;=<>\\n\\r]"), " ").trim().take(24).ifBlank { "My style" }

        fun encodeAll(list: List<CustomLook>): String = list.joinToString("\n") { l ->
            listOf(l.id, cleanName(l.name), l.base, l.accent.toString(), l.changes.entries.joinToString(";") { "${it.key}=${it.value}" }).joinToString("|")
        }

        /** The style as a share code: base|accent|Knob=Option;... (no id or name; the server keeps the name). */
        fun shareCode(look: CustomLook): String =
            "${look.base}|${look.accent}|" + look.changes.entries.joinToString(";") { "${it.key}=${it.value}" }

        /** A shared style as a new style of this phone's own; null if the code doesn't parse. */
        fun fromShareCode(id: String, name: String, code: String): CustomLook? {
            val p = code.split('|')
            if (p.size != 3 || p[0].isBlank()) return null
            val accent = p[1].toLongOrNull() ?: return null
            val changes = p[2].split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0] to it[1] } }.toMap()
            return CustomLook(id, cleanName(name), p[0], accent, changes)
        }

        /** Lines that don't parse are skipped, so a damaged save loses one style, not all. */
        fun decodeAll(raw: String): List<CustomLook> = raw.lines().mapNotNull { line ->
            val p = line.split('|')
            if (p.size != 5 || !p[0].startsWith(ID_PREFIX)) return@mapNotNull null
            val accent = p[3].toLongOrNull() ?: return@mapNotNull null
            val changes = p[4].split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0] to it[1] } }.toMap()
            CustomLook(p[0], p[1], p[2], accent, changes)
        }
    }
}
