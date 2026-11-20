package app.duet.music.downloads

import app.duet.music.domain.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Songs saved on the device to play without internet. Only sources whose audio is a plain stream
 * ([app.duet.music.extension.Capability.Download], YouTube Music) can be saved; DRM-protected audio
 * (Spotify) is never downloaded. The platform stores the files.
 */
interface Downloads {
    /** Saved songs, newest first. */
    val saved: StateFlow<List<Track>>
    /** Songs being saved, by [Track.key], with progress 0..1 (or -1 while the size is unknown). */
    val inProgress: StateFlow<Map<String, Float>>
    /** The last problem, in plain words, for a message; null when fine. */
    val lastError: StateFlow<String?>

    fun download(track: Track)
    fun delete(trackKey: String)
    fun deleteAll()
    /** A local URI the player can open, or null when the song is not saved. */
    fun localUri(trackKey: String): String?
    /** Bytes used by saved songs. */
    fun bytesUsed(): Long
}
