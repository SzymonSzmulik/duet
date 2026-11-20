package app.duet.music.spotify

import android.os.Handler
import android.os.Looper
import android.util.Log
import app.duet.music.extension.Playable
import app.duet.music.player.PlaybackStatus
import app.duet.music.player.SourcePlayer
import app.duet.music.player.SourcePlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Plays Spotify songs in Spotify's own Web Player, the page [AndroidSpotifyWebSession] keeps loaded
 * (Myspoti's approach). The audio stays inside Spotify's player with its DRM; Duet only does what a
 * person would do on that page: open the song, press play or pause, move the position, and read what
 * is playing. Main thread only.
 *
 * Spotify moves on to another song by itself when one ends; when the page shows a different song
 * than the one asked for, Duet pauses it and reports [PlaybackStatus.Ended], so Duet's own queue
 * decides what plays next. While an ad plays (free accounts), the song shown is not a track and
 * nothing is ended.
 */
class SpotifyWebPlayer(
    private val session: AndroidSpotifyWebSession,
    /** Settings > Sound > speed; applied to the page's audio element. */
    private val speed: () -> Float = { 1f },
) : SourcePlayer {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(SourcePlayerState())
    override val state: StateFlow<SourcePlayerState> = _state.asStateFlow()

    private var trackId: String? = null
    private var started = false
    private var startedAt = 0L

    private val poll = object : Runnable {
        override fun run() {
            session.evaluate(STATE_JS) { onState(it) }
            session.evaluate("(function(){var m=window.__duetMedia;if(m&&m.playbackRate!==${speed()})m.playbackRate=${speed()};})()")
            if (trackId != null) main.postDelayed(this, 500)
        }
    }

    override fun canPlay(playable: Playable) = playable is Playable.Remote && playable.uri.startsWith("spotify:track:")

    override fun play(playable: Playable) {
        val id = (playable as Playable.Remote).uri.removePrefix("spotify:track:")
        trackId = id
        started = false
        startedAt = System.currentTimeMillis()
        _state.value = SourcePlayerState(status = PlaybackStatus.Loading)
        main.removeCallbacks(poll)
        // Fast skipping: only the song you stop on is sent to Spotify, not a second of each one on the way.
        main.removeCallbacks(sendPlay)
        main.postDelayed(sendPlay, SKIP_SETTLE_MS)
    }

    private val sendPlay = Runnable {
        val id = trackId ?: return@Runnable
        startedAt = System.currentTimeMillis()
        // The last song's ad flags don't apply to this play; the next state reply sets them again.
        SpotifyControlRelay.adNow = false
        SpotifyControlRelay.adAhead = false
        session.evaluate(playJs(id))
        main.post(poll)
    }

    override fun pause() = session.evaluate(TOGGLE_JS.replace("WANT", "false"))
    override fun resume() = session.evaluate(TOGGLE_JS.replace("WANT", "true"))
    // Spotify's own player seeks the song; the page's media element can be a short helper clip, not the song.
    override fun seekTo(positionMs: Long) = session.evaluate("(function(){var a=window.__duetApi;if(a&&a.seekTo)a.seekTo($positionMs);})()")

    override fun stop() {
        trackId = null
        main.removeCallbacks(sendPlay)
        main.removeCallbacks(poll)
        session.evaluate(TOGGLE_JS.replace("WANT", "false"))
        _state.value = SourcePlayerState()
    }

    private fun onState(json: String?) {
        val wanted = trackId ?: return
        val o = runCatching { JSONObject(org.json.JSONTokener(json).nextValue() as? String ?: json ?: "") }.getOrNull() ?: return
        // optString turns JSON null into the text "null", which looked like "another song" and the start never counted.
        val now = if (o.isNull("track")) null else o.optString("track").ifEmpty { null }
        val playing = o.optBoolean("playing")
        val position = (o.optDouble("pos", 0.0) * 1000).toLong()
        val duration = (o.optDouble("dur", 0.0) * 1000).toLong().takeIf { it > 0 }
        val loginWall = o.optBoolean("login")

        // Started: Spotify plays the asked song, or audio plays and the bar doesn't name another song.
        if (playing && (now == wanted || now == null)) started = true
        Log.d(TAG, "state: track=$now wanted=$wanted playing=$playing pos=$position started=$started")
        when {
            loginWall && !started -> _state.value = SourcePlayerState(PlaybackStatus.Error, error = "Spotify asks you to sign in. Open Settings > Spotify > Sign in.")
            // Ads: one is playing, or this song is about to end into one Spotify won't route around.
            // Ending here lets Duet start the next song; an ad at the start of a play moves on by itself.
            started && (SpotifyControlRelay.adNow ||
                (SpotifyControlRelay.adAhead && duration != null && position > 0 && position >= duration - AD_LEAD_MS)) -> {
                Log.i(TAG, "ad: skipping to Duet's next song")
                session.evaluate(TOGGLE_JS.replace("WANT", "false"))
                trackId = null
                _state.value = _state.value.copy(status = PlaybackStatus.Ended)
            }
            started && now != null && now != wanted -> {
                // Spotify went on to its own next song: stop it there and let Duet's queue continue.
                session.evaluate(TOGGLE_JS.replace("WANT", "false"))
                trackId = null
                _state.value = _state.value.copy(status = PlaybackStatus.Ended)
            }
            !started && System.currentTimeMillis() - startedAt > START_TIMEOUT_MS ->
                _state.value = SourcePlayerState(PlaybackStatus.Error, error = "Spotify didn't start this song. Try again, or check that you're signed in.")
            else -> _state.value = SourcePlayerState(
                status = when {
                    !started -> PlaybackStatus.Loading
                    playing -> PlaybackStatus.Playing
                    else -> PlaybackStatus.Paused
                },
                positionMs = if (now == wanted || now == null) position else 0,
                durationMs = duration,
            )
        }
    }

    private companion object {
        const val TAG = "DuetSpotifyPlayer"
        const val START_TIMEOUT_MS = 25_000L
        const val SKIP_SETTLE_MS = 350L
        const val AD_LEAD_MS = 1_500L

        /**
         * Opens the song's page inside the running Web Player (its own router, no reload) and presses
         * the song's play button once it appears.
         */
        fun playJs(id: String) = """
            (function () {
              // The Web Player's own PlayerAPI, found through its React tree (Myspoti's player-bridge.js,
              // GPL-3.0): play() starts the song on this page's device. Pressing the song page's play
              // button sent the command but the page never started the audio.
              var log = function (s) { try { console.warn('[duet] ' + s); } catch (e) {} };
              var findPlayer = function () {
                var host = document.getElementById('main') || document.body;
                var roots = [host].concat(Array.prototype.slice.call(host.querySelectorAll('*')));
                for (var i = 0; i < roots.length; i++) {
                  var el = roots[i];
                  var key = Object.keys(el).find(function (k) { return k.indexOf('__reactContainer$') === 0; });
                  if (!key) continue;
                  var stack = [el[key]];
                  while (stack.length) {
                    var f = stack.pop();
                    var v = f.memoizedProps && f.memoizedProps.value;
                    if (v && typeof v === 'object' && typeof v.getRegistry === 'function') {
                      var reg = v.getRegistry();
                      var it = reg._map.keys(), k2;
                      var byName = {};
                      while (!(k2 = it.next()).done) if (k2.value && k2.value.description) byName[k2.value.description] = k2.value;
                      return function (name) { return byName[name] ? reg.resolve(byName[name]) : null; };
                    }
                    if (f.sibling) stack.push(f.sibling);
                    if (f.child) stack.push(f.child);
                  }
                }
                return null;
              };
              try {
                var get = findPlayer();
                var api = get && get('PlayerAPI');
                if (api && typeof api.play === 'function') {
                  window.__duetApi = api;
                  // Another device of the account may hold the session (paused): take playback to this
                  // page first, as Myspoti does (ConnectTransferAPI.pullToLocalDevice), then play.
                  var transfer = get('ConnectTransferAPI');
                  var pull = transfer && typeof transfer.pullToLocalDevice === 'function'
                    ? Promise.resolve(transfer.pullToLocalDevice({})).catch(function (e) { log('play: transfer failed ' + (e && e.message)); })
                    : Promise.resolve();
                  pull.then(function () { return api.play({ uri: 'spotify:track:$id' }, {}, {}); })
                    .then(function () { log('play: PlayerAPI ok'); }, function (e) { log('play: PlayerAPI failed ' + (e && e.message)); });
                  return;
                }
                log('play: no PlayerAPI, pressing the button');
              } catch (e) { log('play: ' + e.message); }
              var path = '/track/$id';
              if (location.pathname !== path) { history.pushState({}, '', path); dispatchEvent(new PopStateEvent('popstate', { state: {} })); }
              var tries = 0;
              (function press() {
                var b = document.querySelector('[data-testid="action-bar-row"] [data-testid="play-button"]');
                if (b && location.pathname === path) { b.click(); return; }
                if (tries++ < 60) setTimeout(press, 250);
              })();
            })();
        """.trimIndent()

        /** Presses the page's play/pause button when its state is not WANT (true: playing). */
        const val TOGGLE_JS = "(function(){var b=document.querySelector('[data-testid=\"control-button-playpause\"]');" +
            "if(!b||b.disabled)return;var playing=/pause/i.test(b.getAttribute('aria-label')||'');if(playing!==WANT)b.click();})()"

        /** What plays now: the song id from the now-playing link, play state, position and length. */
        const val STATE_JS = "(function(){var m=window.__duetMedia;" +
            // The player bar links to the song as /track/ID or as an album link with highlight=spotify:track:ID.
            "var w=document.querySelector('[data-testid=\"now-playing-widget\"]');var t=null;" +
            "if(w){var as=w.querySelectorAll('a[href]');for(var i=0;i<as.length&&!t;i++){var h=decodeURIComponent(as[i].getAttribute('href')||'');" +
            "var x=h.match(/spotify:track:([A-Za-z0-9]+)/)||h.match(/\\/track\\/([A-Za-z0-9]+)/);if(x)t=x[1];}}" +
            "var b=document.querySelector('[data-testid=\"control-button-playpause\"]');" +
            // The audio element knows best; the button only counts when enabled (it says "Pause" while idle).
            // The bar's button is the truth; the media element may be a short helper clip, not the song.
            "var playing=b&&!b.disabled?/pause/i.test(b.getAttribute('aria-label')||''):(m?!m.paused:false);" +
            "var login=!!document.querySelector('[data-testid=\"login-button\"]');" +
            // The player bar's own clock ("1:23") is the song's; the media element can be a short helper clip.
            "var sec=function(id){var e=document.querySelector('[data-testid=\"'+id+'\"]');if(!e)return 0;" +
            "return (e.textContent||'').trim().split(':').reduce(function(a,x){return a*60+(parseInt(x,10)||0);},0);};" +
            "var bd=sec('playback-duration');" +
            "return JSON.stringify({track:t,playing:playing,pos:bd>0?sec('playback-position'):(m?m.currentTime:0)," +
            "dur:bd>0?bd:(m&&isFinite(m.duration)?m.duration:0),login:login});})()"
    }
}
