package app.duet.music.extension

import kotlinx.coroutines.flow.StateFlow

/**
 * A sign-in done on the service's own web page (in a WebView on Android). The session is the
 * site's cookies; Duet never sees a password.
 */
interface WebLoginSession {
    /** True while the site's session cookies exist. */
    val signedIn: StateFlow<Boolean>
    /** The site's cookie header while signed in, for requests made as the user; null otherwise. */
    fun cookieHeader(): String?
    /** Shows the site's login page. */
    fun signIn()
    /** Forgets this site's session only. */
    fun signOut()
}
