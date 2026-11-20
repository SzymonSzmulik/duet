package app.duet.music

import android.app.Activity
import android.app.SearchManager
import android.os.Bundle
import app.duet.music.player.playFromVoice
import app.duet.music.service.PlaybackService
import kotlinx.coroutines.launch

/** Takes Assistant's "play X" ([android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH]) and starts it without showing anything. */
class VoicePlayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = intent?.getStringExtra(SearchManager.QUERY).orEmpty()
        val graph = (application as DuetApplication).graph
        // The service keeps playback alive once this invisible screen is gone.
        PlaybackService.start(this)
        graph.mainScope.launch { graph.playFromVoice(query) }
        finish()
    }
}
