package app.duet.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import app.duet.music.DuetApplication
import app.duet.music.MainActivity
import app.duet.music.R
import app.duet.music.player.PlaybackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.duet.music.player.playFromVoice
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * The home screen widget: what plays (cover, title, artist) and previous / play-pause / next.
 * The buttons come back here as broadcasts and go to the app's player; tapping the rest opens Duet.
 * [DuetApplication] calls [update] whenever the song or play state changes.
 */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        kotlinx.coroutines.MainScope().launch { update(context) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as DuetApplication).graph
        val player = graph.player
        when (intent.action) {
            ACTION_PREV -> player.previous()
            // Nothing queued (e.g. after a restart): start your recent songs, as "play music" by voice does.
            ACTION_PLAY -> if (player.state.value.track == null) graph.mainScope.launch { graph.playFromVoice("") } else player.togglePlayPause()
            ACTION_NEXT -> player.next()
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_PREV = "app.duet.music.widget.PREV"
        private const val ACTION_PLAY = "app.duet.music.widget.PLAY"
        private const val ACTION_NEXT = "app.duet.music.widget.NEXT"

        private var artUrl: String? = null
        private var art: Bitmap? = null

        /** Redraws every Duet widget from the player's state; the cover is fetched once per song. */
        suspend fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            if (ids.isEmpty()) return
            val state = (context.applicationContext as DuetApplication).graph.player.state.value
            val track = state.track
            if (track?.artworkUrl != artUrl) {
                artUrl = track?.artworkUrl
                art = artUrl?.let { url -> withContext(Dispatchers.IO) { runCatching { loadSmall(url) }.getOrNull() } }
            }
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
                setTextViewText(R.id.widget_title, track?.title ?: "Duet")
                setTextViewText(R.id.widget_artist, track?.artist ?: "Tap to open")
                art?.let { setImageViewBitmap(R.id.widget_art, it) } ?: setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher)
                val playing = state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Loading
                setImageViewResource(R.id.widget_play, if (playing) R.drawable.widget_pause else R.drawable.widget_play)
                setContentDescription(R.id.widget_play, if (playing) "Pause" else "Play")
                setOnClickPendingIntent(R.id.widget_prev, broadcast(context, ACTION_PREV))
                setOnClickPendingIntent(R.id.widget_play, broadcast(context, ACTION_PLAY))
                setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT))
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
                ))
            }
            manager.updateAppWidget(ids, views)
        }

        private fun broadcast(context: Context, action: String) = PendingIntent.getBroadcast(
            context, action.hashCode(), Intent(context, NowPlayingWidget::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
        )

        /** A cover small enough for a widget (widgets have a tight memory limit). */
        private fun loadSmall(url: String): Bitmap? {
            val bytes = URL(url).openStream().use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 160) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
