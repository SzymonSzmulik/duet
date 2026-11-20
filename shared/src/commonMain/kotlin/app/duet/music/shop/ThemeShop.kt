package app.duet.music.shop

import app.duet.music.settings.AppSettings
import app.duet.music.settings.CustomLook
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A style someone shared. */
data class SharedStyle(
    val id: String, val name: String, val author: String, val code: String, val likes: Int,
    val description: String = "", val tags: List<String> = emptyList(), val created: String = "",
)

/**
 * Shared styles (D66): people share styles they made and try others'. The server is a Google Apps Script web app
 * over a Sheet (docs/themes-script.gs). Sharing needs a YouTube Music sign-in in Duet and a username; the
 * username is tied to [AppSettings.shopKey]. Nothing from the Google sign-in is sent.
 */
class ThemeShop(
    private val http: HttpClient,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
    /** Whether YouTube Music is signed in; sharing needs it. */
    private val canShare: () -> Boolean,
    private val url: String = URL,
) {
    private val _styles = MutableStateFlow<List<SharedStyle>>(emptyList())
    val styles: StateFlow<List<SharedStyle>> = _styles.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    /** The last thing to tell the person (an error or "Shared"). */
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** This phone's username for sharing; blank until chosen. */
    val username: StateFlow<String> get() = settings.shopName
    val isSetUp: Boolean get() = url.isNotBlank()
    val signedIn: Boolean get() = canShare()

    fun refresh() = run("Couldn't load shared styles.") {
        _styles.value = parseList(call("list"))
    }

    /** Claims [username] for this phone (or confirms it's already ours), then remembers it. */
    fun chooseName(username: String, then: () -> Unit = {}) = run("Couldn't save that name.") {
        val reply = call("register", "username" to username, "key" to settings.shopKey)
        when {
            ok(reply) -> { settings.setShopName(username); then() }
            "taken" in reply -> _message.value = "\"$username\" is taken. Try another."
            else -> _message.value = "Use 3 to 20 letters, numbers, _ or ."
        }
    }

    fun share(look: CustomLook, description: String = "", tags: List<String> = emptyList()) = run("Couldn't share it.") {
        check(canShare()) { "Sign in to YouTube Music to share" }
        val reply = call("publish", "username" to settings.shopName.value, "key" to settings.shopKey,
            "name" to look.name, "code" to CustomLook.shareCode(look),
            "description" to cleanDescription(description), "tags" to tags.filter { it in TAGS }.take(4).joinToString(","))
        _message.value = if (ok(reply)) "Shared \"${look.name}\"" else "Couldn't share it."
        _styles.value = parseList(call("list"))
    }

    fun delete(style: SharedStyle) = run("Couldn't delete it.") {
        call("delete", "id" to style.id, "username" to settings.shopName.value, "key" to settings.shopKey)
        _styles.value = _styles.value.filter { it.id != style.id }
    }

    fun like(style: SharedStyle) {
        _styles.value = _styles.value.map { if (it.id == style.id) it.copy(likes = it.likes + 1) else it }
        run(null) { call("like", "id" to style.id) }
    }

    /** Adds a shared style to this phone's own and switches to it. */
    fun install(style: SharedStyle): Boolean {
        val look = CustomLook.fromShareCode(CustomLook.ID_PREFIX + "s" + style.id, style.name, style.code) ?: return false
        settings.saveCustomLook(look)
        settings.chooseStyle(look.id, look.accent)
        return true
    }

    fun clearMessage() { _message.value = null }

    private fun run(failure: String?, block: suspend () -> Unit) {
        if (!isSetUp) { _message.value = "Shared styles aren't set up yet."; return }
        scope.launch {
            _busy.value = true
            try { block() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (failure != null) _message.value = e.message?.takeIf { it.startsWith("Sign in") } ?: failure
            }
            _busy.value = false
        }
    }

    private suspend fun call(action: String, vararg params: Pair<String, String>): String =
        http.get(url) {
            parameter("action", action)
            params.forEach { (k, v) -> parameter(k, v) }
        }.bodyAsText()

    companion object {
        /** The deployed web app's /exec URL (docs/themes-script.gs); blank until the owner deploys it. */
        /** Tags a shared style can carry (the server keeps the same list). */
        val TAGS = listOf("dark", "light", "minimal", "colourful", "bold", "soft", "retro", "clean")

        fun cleanDescription(raw: String): String = raw.replace(Regex("[<>\\n\\r]"), " ").trim().take(140)

        const val URL = "https://script.google.com/macros/s/AKfycbwGv7T2-z_aB2Nwf8lAZxscZgozpvfHdGEWIGbaQ10DEvad7hIVH9nWYY699XJ1klMw/exec"

        private fun ok(reply: String) = runCatching { Json.parseToJsonElement(reply).jsonObject["ok"]?.jsonPrimitive?.booleanOrNull }.getOrNull() == true

        /** The server's list; entries that don't parse are skipped. */
        fun parseList(reply: String): List<SharedStyle> {
            val root = Json.parseToJsonElement(reply).jsonObject
            check(root["ok"]?.jsonPrimitive?.booleanOrNull == true) { "server said no" }
            return root["themes"]?.jsonArray.orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                fun str(k: String) = o[k]?.jsonPrimitive?.contentOrNull
                SharedStyle(str("id") ?: return@mapNotNull null, str("name") ?: return@mapNotNull null,
                    str("author").orEmpty(), str("code") ?: return@mapNotNull null, o["likes"]?.jsonPrimitive?.intOrNull ?: 0,
                    description = str("description").orEmpty(),
                    tags = (o["tags"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                    created = str("created").orEmpty())
            }
        }
    }
}
