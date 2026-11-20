package app.duet.music.update

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which version each update feed offers right now. */
data class Releases(val owner: UpdateInfo?, val testers: UpdateInfo?, val everyone: UpdateInfo?)

/**
 * Duet Owner only: shows which version testers and everyone have, and sends the owner's build to
 * testers or to everyone through the release script (docs/release-script.gs).
 */
class ReleaseControls(
    private val http: HttpClient,
    private val scope: CoroutineScope,
    private val ownerFeed: String,
    private val testerFeed: String,
    private val releaseFeed: String,
    private val key: String,
) {
    enum class Target(val action: String) { Testers("testers"), Everyone("everyone") }

    private val _releases = MutableStateFlow<Releases?>(null)
    val releases: StateFlow<Releases?> = _releases.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    fun refresh() {
        scope.launch { load() }
    }

    fun send(target: Target) {
        val owner = _releases.value?.owner ?: return
        if (_busy.value) return
        scope.launch {
            _busy.value = true
            _message.value = try {
                check(owner.controlUrl.isNotBlank() && key.isNotBlank()) { "not set up" }
                val reply = http.get(owner.controlUrl) {
                    parameter("key", key)
                    parameter("action", target.action)
                }.bodyAsText()
                check("\"ok\":true" in reply.replace(" ", "")) { reply }
                load()
                if (target == Target.Testers) "Sent ${owner.versionName} to testers" else "Released ${owner.versionName} to everyone"
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e.message == "not set up") "Release buttons aren't set up yet" else "Couldn't send. Try again."
            }
            _busy.value = false
        }
    }

    private suspend fun load() {
        suspend fun read(url: String) = runCatching { AppUpdates.parse(http.get(url).bodyAsText()) }.getOrNull()
        val o = scope.async { read(ownerFeed) }
        val t = scope.async { read(testerFeed) }
        val e = scope.async { read(releaseFeed) }
        _releases.value = Releases(o.await(), t.await(), e.await())
    }
}
