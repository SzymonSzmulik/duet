package app.duet.music.spotify

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends the Web Player's playback control requests (Spotify Connect commands, transfers, device and
 * track-playback state, play reports) from the app instead of the WebView.
 *
 * Every WebView request carries "X-Requested-With: <package>" and current WebViews no longer let an
 * app turn it off. Spotify acknowledges Connect commands that carry it (200) but never delivers them
 * to the player, so nothing plays: exactly what Duet's logs showed. The page's `fetch` is wrapped at
 * document start; matching requests come here through [Net.send], go out with OkHttp using the page's
 * own headers (token, client token, connection id), and the answer is handed back to the page.
 *
 * Re-implemented from Myspoti/Orpheon's ControlRelay (GPL-3.0, github.com/kota-zo/myspoti), reduced to
 * the relay itself: no route fallbacks, ad handling or 402 diagnostics.
 */
internal class SpotifyControlRelay(private val view: WebView) {
    private val main = Handler(Looper.getMainLooper())

    /** The identity the page presents (user agent, languages, client hints), echoed on relayed requests. */
    @Volatile private var identity: JSONObject? = null

    /**
     * Adds the `DuetNet` bridge, visible only to open.spotify.com where the WebView supports message
     * listeners; [SCRIPT] must also be added as a document-start script for that origin.
     */
    fun install() {
        val net = Net()
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER)) {
            androidx.webkit.WebViewCompat.addWebMessageListener(view, "DuetNet", setOf("https://open.spotify.com")) { _, message, _, _, _ ->
                message.data?.let(net::postMessage) // only parses and enqueues; OkHttp does the network work
            }
        } else view.addJavascriptInterface(net, "DuetNet")
    }

    private inner class Net {
        @JavascriptInterface
        fun postMessage(json: String) {
            val m = runCatching { JSONObject(json) }.getOrNull() ?: return
            if (m.optString("type") == "identity") { identity = m.optJSONObject("value"); return }
            val id = m.optInt("id", -1).takeIf { it >= 0 } ?: return
            val url = m.optString("url")
            if (!isRelayed(url)) return reply(id, m.optString("doc"), 0, "{}", "")
            send(id, m.optString("doc"), url, m.optString("method", "GET"), m.optJSONObject("headers") ?: JSONObject(), if (m.isNull("body")) null else m.optString("body"))
        }
    }

    private fun send(id: Int, doc: String, url: String, method: String, given: JSONObject, body: String?) {
        val headers = relayHeaders(given, identity)
        val requestBody = when {
            body != null -> body.toByteArray().toRequestBody(null)
            method == "POST" || method == "PUT" -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val request = runCatching { Request.Builder().url(url).headers(headers).method(method, requestBody).build() }.getOrElse {
            return reply(id, doc, 0, "{}", "")
        }
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "relay $method ${shortPath(url)} failed: ${e.message}")
                reply(id, doc, 0, "{}", "")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    var text = runCatching { r.body?.string().orEmpty() }.getOrDefault("")
                    // Ads (free accounts): playback states are rewritten so a song's end skips the ad (D52).
                    if ("/track-playback/v1/" in url) {
                        val rw = app.duet.music.sources.spotify.SpotifyAds.withoutAds(text)
                        if (rw.hasMachine) { text = rw.body; adAhead = rw.adAhead; adNow = rw.adNow }
                        if (rw.adAhead || rw.adNow) Log.i(TAG, "ad: ahead=${rw.adAhead} now=${rw.adNow}")
                    }
                    val out = JSONObject()
                    // OkHttp already decoded the body, so its encoding and length no longer apply.
                    r.headers.forEach { (name, value) -> if (name.lowercase() !in DROPPED_ANSWER_HEADERS) out.put(name, value) }
                    if (COMMAND.containsMatchIn(url) || r.code >= 400) Log.i(TAG, "relay $method ${shortPath(url)} -> ${r.code}")
                    reply(id, doc, r.code, out.toString(), text)
                }
            }
        })
    }

    private fun reply(id: Int, doc: String, status: Int, headersJson: String, body: String) {
        // Quoted here on the network thread; only the hand-off runs on the main thread.
        val script = "window.__duetNetDone && window.__duetNetDone($id, $status, ${JSONObject.quote(headersJson)}, ${JSONObject.quote(body)}, ${JSONObject.quote(doc)})"
        main.post { view.evaluateJavascript(script, null) }
    }

    companion object {
        private const val TAG = "DuetSpotifyRelay"

        /** From the last playback state: the current song ends into an ad Duet must skip itself. */
        @Volatile var adAhead = false
        /** From the last playback state: an ad is the current item. */
        @Volatile var adNow = false

        private val http: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
        }

        private val RELAYED = Regex("^https://[a-z0-9-]+(\\.[a-z0-9-]+)*\\.spotify\\.com/(connect-state|track-playback)/v1/")
        private val REPORTS = Regex("^https://[a-z0-9-]+(\\.[a-z0-9-]+)*\\.spotify\\.com/melody/v1/msg/")
        private val NOT_RELAYED = Regex("^https://(accounts|challenge|www)\\.spotify\\.com/")
        private val COMMAND = Regex("/connect-state/v1/(player/command|connect/transfer)/")
        private val NOT_CARRIED = setOf("host", "content-length", "connection", "accept-encoding", "x-requested-with", "cookie")
        private val DROPPED_ANSWER_HEADERS = setOf("content-encoding", "content-length", "transfer-encoding")

        fun isRelayed(url: String) = (RELAYED.containsMatchIn(url) || REPORTS.containsMatchIn(url)) && !NOT_RELAYED.containsMatchIn(url)

        private fun shortPath(url: String) = url.substringBefore('?').substringAfter(".spotify.com").substringBefore("/from/")

        /** The page's own headers, plus what Chrome adds by itself (which `fetch` can't set and OkHttp doesn't add). */
        internal fun relayHeaders(given: JSONObject, page: JSONObject?): Headers {
            val headers = Headers.Builder()
            given.keys().forEach { key -> if (key.lowercase() !in NOT_CARRIED) runCatching { headers.add(key, given.getString(key)) } }
            fun fromPage(name: String, fallback: String) = page?.optString(name)?.takeIf { it.isNotEmpty() } ?: fallback
            headers["User-Agent"] = fromPage("userAgent", DESKTOP_UA)
            headers["Origin"] = "https://open.spotify.com"
            headers["Referer"] = "https://open.spotify.com/"
            if (headers["Accept"] == null) headers["Accept"] = "*/*"
            if (headers["Priority"] == null) headers["Priority"] = "u=1, i"
            headers["Sec-Fetch-Site"] = "same-site"
            headers["Sec-Fetch-Mode"] = "cors"
            headers["Sec-Fetch-Dest"] = "empty"
            headers["Accept-Language"] = fromPage("acceptLanguage", "en-GB,en;q=0.9")
            fromPage("brands", "").takeIf { it.isNotEmpty() }?.let { headers["sec-ch-ua"] = it }
            headers["sec-ch-ua-mobile"] = fromPage("mobile", "?0")
            headers["sec-ch-ua-platform"] = "\"${fromPage("platform", "Linux")}\""
            return headers.build()
        }

        private const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        /** Wraps window.fetch at document start; the same patterns as [isRelayed]. */
        val SCRIPT = """
            (() => {
              if (window.__duetNetInstalled || !window.DuetNet) return;
              window.__duetNetInstalled = true;
              try {
                const d = navigator.userAgentData || {};
                const langs = (navigator.languages && navigator.languages.length) ? navigator.languages : [navigator.language];
                window.DuetNet.postMessage(JSON.stringify({ type: 'identity', value: {
                  userAgent: navigator.userAgent,
                  brands: (d.brands || []).map(b => '"' + b.brand + '";v="' + b.version + '"').join(', '),
                  mobile: d.mobile === true ? '?1' : '?0',
                  platform: d.platform || 'Linux',
                  acceptLanguage: langs.map((l, i) => i === 0 ? l : l + ';q=0.' + Math.max(1, 10 - i)).join(','),
                }}));
              } catch (e) {}
              const relayed = /^https:\/\/([a-z0-9-]+\.)*spotify\.com\/(connect-state|track-playback)\/v1\//;
              const notRelayed = /^https:\/\/(accounts|challenge|www)\.spotify\.com\//;
              // Play reports go through the app only once this page's device is registered.
              const reports = /^https:\/\/([a-z0-9-]+\.)*spotify\.com\/melody\/v1\/msg\//;
              const registration = /^https:\/\/([a-z0-9-]+\.)*spotify\.com\/track-playback\/v1\/devices(\?|$)/;
              const command = /player\/command|connect\/transfer/;
              let registered = false;
              const pending = new Map();
              let seq = 0;
              // This document's mark: an answer for the page before a reload must not resolve a new request.
              const doc = Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
              const original = window.fetch;
              window.__duetNetDone = (id, status, headers, body, from) => {
                if (from !== doc) return;
                const p = pending.get(id);
                if (!p) return;
                pending.delete(id);
                if (!status) return p.reject(new TypeError('Failed to fetch'));
                if (status >= 200 && status < 300 && p.method === 'POST' && registration.test(p.url)) registered = true;
                const answer = () => new Response((status === 204 || status === 205 || status === 304) ? null : body, { status, headers: JSON.parse(headers) });
                // Some free accounts get relayed commands refused (402): try once from the page itself.
                if (status === 402 && command.test(p.url)) { original.call(window, p.url, p.init).then(p.resolve, () => p.resolve(answer())); return; }
                p.resolve(answer());
              };
              window.fetch = function (input, init) {
                const url = typeof input === 'string' ? input : (input instanceof URL ? input.href : null);
                const body = init && init.body;
                const method = ((init && init.method) || 'GET').toUpperCase();
                const report = registered && !!url && reports.test(url) && typeof body === 'string' && method === 'POST';
                if (!url || !(relayed.test(url) || report) || notRelayed.test(url) || (body != null && typeof body !== 'string')) return original.apply(this, arguments);
                const headers = {};
                new Headers((init && init.headers) || {}).forEach((v, k) => { headers[k] = v; });
                return new Promise((resolve, reject) => {
                  const id = ++seq;
                  pending.set(id, { resolve, reject, url, init, method });
                  window.DuetNet.postMessage(JSON.stringify({ id, doc, url, method, headers, body: body == null ? null : body }));
                });
              };
            })();
        """.trimIndent()
    }
}
