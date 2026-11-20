package app.duet.music.extension

import kotlinx.coroutines.flow.StateFlow

/** A source's sign-in, for sources that have accounts. The UI shows [status] and the buttons. */
interface SourceAccount {
    /** One line, e.g. "Not signed in" or "Signed in as Alex". */
    val status: StateFlow<String>
    val signedIn: StateFlow<Boolean>
    /** False when the source plays signed out too (YouTube Music); then signing in is optional. */
    val neededToPlay: Boolean get() = true
    fun signIn()
    fun signOut()
}
