package app.duet.music.update

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the update feed says the newest build is. */
@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String = "",
    /** Owner feed only: the same version built as Duet Tester and as Duet, for the release buttons. */
    val testerApkUrl: String = "",
    val releaseApkUrl: String = "",
    /** Owner feed only: the release script (docs/release-script.gs) behind the owner's buttons. */
    val controlUrl: String = "",
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    /** [progress] is 0..1, or negative while the size is unknown. */
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Checks this app's public update feed (a JSON document, see docs/RELEASING.md) against the running build,
 * and hands a newer APK to the platform installer.
 */
class AppUpdates(
    private val http: HttpClient,
    private val scope: CoroutineScope,
    private val currentVersionCode: Int,
    /** Downloads the APK and opens the system installer; reports progress 0..1 (negative = unknown). */
    private val install: suspend (UpdateInfo, onProgress: (Float) -> Unit) -> Unit,
    /** This app's feed: Duet, Duet Tester and Duet Owner each follow their own (docs/RELEASING.md). */
    private val feedUrl: String,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val info = parse(http.get(feedUrl).bodyAsText())
                if (info.versionCode > currentVersionCode && info.apkUrl.isNotBlank()) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                UpdateState.Failed("Couldn't check for updates")
            }
        }
    }

    fun download(info: UpdateInfo) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(info, -1f)
            try {
                install(info) { _state.value = UpdateState.Downloading(info, it) }
                // The system installer is open now; offer the update again if it was cancelled.
                _state.value = UpdateState.Available(info)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = UpdateState.Failed(if (e is UpdateRefused) e.message.orEmpty() else "Download failed")
            }
        }
    }

    /** The update can't be installed over this app; [message] tells the person what to do instead. */
    class UpdateRefused(message: String) : Exception(message)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The Doc export starts with a byte-order mark; editors may also curl the quotes. */
        fun parse(text: String): UpdateInfo = json.decodeFromString(
            UpdateInfo.serializer(),
            text.trimStart('﻿').trim().replace('“', '"').replace('”', '"'),
        )
    }
}
