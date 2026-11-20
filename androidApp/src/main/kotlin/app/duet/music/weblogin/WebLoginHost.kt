package app.duet.music.weblogin

import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.webkit.CookieManager
import android.webkit.WebView

/**
 * A site whose sign-in runs on its own page in a WebView ([WebLoginActivity] shows [view]).
 * Pattern from Myspoti's `WebPlayerHost` (GPL-3.0) and SpatialFlow's `GoogleSignInScreen` (Apache-2.0).
 */
abstract class WebLoginHost(protected val appContext: Context) {
    /** Key for [WebLoginActivity]'s extra. */
    abstract val site: String
    /** Shown in the screen title: "Sign in to …". */
    abstract val serviceName: String
    abstract val view: WebView
    /** True while the site's page loads; the login screen shows "Loading…" over it meanwhile. */
    val pageLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** Set by the open login screen; called once the site has a session. */
    var onSignedIn: (() -> Unit)? = null

    /** Contexts the WebView is created with; the login screen swaps its Activity in so dialogs and the keyboard work. */
    protected val webContext = MutableContextWrapper(appContext)

    fun useContext(activity: Context?) {
        webContext.baseContext = activity ?: appContext
    }

    /** The login screen closed (signed in or cancelled). */
    open fun afterLogin() = Unit

    /** Page the login screen loads once [view] is on screen (a page loaded at zero size can stay blank). */
    var pendingUrl: String? = null

    protected fun openLoginScreen(url: String) {
        pendingUrl = url
        appContext.startActivity(
            Intent(appContext, WebLoginActivity::class.java)
                .putExtra(WebLoginActivity.EXTRA_SITE, site)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        /** A desktop Chrome on Linux with the WebView's own Chrome version. */
        fun desktopUserAgent(webViewUa: String): String {
            val major = Regex("Chrome/(\\d+)").find(webViewUa)?.groupValues?.get(1) ?: "130"
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
        }

        /**
         * Android's WebView tells every site which app it runs in (the `X-Requested-With` header).
         * Google refuses sign-ins it sees coming from inside an app ("This browser or app may not be
         * secure"), so Duet's web views don't send it. Needs a WebView that supports the setting.
         */
        fun hideAppIdentity(view: android.webkit.WebView) {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                androidx.webkit.WebSettingsCompat.setRequestedWithHeaderOriginAllowList(view.settings, emptySet())
            }
        }

        /**
         * Makes [view] a consistent desktop Chrome on Linux: the user agent and the client hints
         * (`Sec-CH-UA*` headers, `navigator.userAgentData`), which otherwise still say "Android WebView,
         * mobile". Login pages' bot checks reject that mismatch. From Myspoti's `WebPlayerHost` and
         * `Fingerprint` (GPL-3.0): the brand list and its GREASE brand follow Chromium's own rules.
         */
        fun desktopIdentity(view: android.webkit.WebView) {
            val ua = android.webkit.WebSettings.getDefaultUserAgent(view.context)
            val major = Regex("Chrome/(\\d+)").find(ua)?.groupValues?.get(1)?.toIntOrNull() ?: 140
            val full = Regex("Chrome/([\\d.]+)").find(ua)?.groupValues?.get(1)?.takeIf { it.count { c -> c == '.' } == 3 } ?: "$major.0.0.0"
            view.settings.userAgentString = desktopUserAgent(ua)
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.USER_AGENT_METADATA)) {
                val brands = chromeBrands(major, full).map { (name, maj, fv) ->
                    androidx.webkit.UserAgentMetadata.BrandVersion.Builder().setBrand(name).setMajorVersion(maj).setFullVersion(fv).build()
                }
                androidx.webkit.WebSettingsCompat.setUserAgentMetadata(
                    view.settings,
                    androidx.webkit.UserAgentMetadata.Builder()
                        .setBrandVersionList(brands).setFullVersion(full)
                        .setPlatform("Linux").setPlatformVersion("6.8.0").setArchitecture("x86").setBitness(64)
                        .setMobile(false).setModel("").setWow64(false)
                        .build(),
                )
            }
        }

        /** Chromium's brand list for [major]: the GREASE brand, its version and the order follow from the major (Myspoti `Fingerprint.brandsOf`). */
        fun chromeBrands(major: Int, fullVersion: String): List<Triple<String, String, String>> {
            val chars = listOf(" ", "(", ":", "-", ".", "/", ")", ";", "=", "?", "_")
            val greaseVersions = listOf("8", "99", "24")
            val orders = listOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 1), intArrayOf(1, 0, 2), intArrayOf(1, 2, 0), intArrayOf(2, 0, 1), intArrayOf(2, 1, 0))
            val gv = greaseVersions[major % greaseVersions.size]
            val grease = Triple("Not${chars[major % chars.size]}A${chars[(major + 1) % chars.size]}Brand", gv, "$gv.0.0.0")
            val order = orders[major % orders.size]
            val out = arrayOfNulls<Triple<String, String, String>>(3)
            out[order[0]] = grease
            out[order[1]] = Triple("Chromium", "$major", fullVersion)
            out[order[2]] = Triple("Google Chrome", "$major", fullVersion)
            return out.map { it!! }
        }

        /** Names of the cookies [url] sees. */
        fun cookieNames(url: String): Set<String> =
            CookieManager.getInstance().getCookie(url)?.split(';')?.map { it.substringBefore('=').trim() }?.toSet().orEmpty()

        /**
         * Expires every cookie [urls] see, on the hosts and parent domains given, so one site's sign-out
         * leaves the other sites signed in (CookieManager has no per-domain clear).
         */
        fun clearCookies(urls: List<String>, domains: List<String>) {
            val cm = CookieManager.getInstance()
            urls.forEach { url ->
                cookieNames(url).forEach { name ->
                    cm.setCookie(url, "$name=; Max-Age=0; Path=/")
                    domains.forEach { d -> cm.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=$d") }
                }
            }
            cm.flush()
        }
    }
}
