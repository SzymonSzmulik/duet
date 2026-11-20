package app.duet.music.sources.ytmusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

/**
 * Resolves YouTube audio stream URLs with NewPipe Extractor (GPL-3.0), which handles YouTube's
 * signature/throttling parameters and keeps up with YouTube's changes.
 */
class NewPipeStreamResolver(
    /** Which audio formats the player can play (by mime type); desktop's JavaFX plays AAC (mp4) but not Opus (webm). */
    private val accepts: (String?) -> Boolean = { true },
) : YtStreamResolver {

    init {
        synchronized(NewPipe::class.java) {
            if (NewPipe.getDownloader() == null) NewPipe.init(OkHttpDownloader(OkHttpClient()))
        }
    }

    override suspend fun resolve(videoId: String, quality: StreamQuality): YtStream = withContext(Dispatchers.IO) {
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        } catch (e: Exception) {
            throw YtMusicException("Couldn't get the stream: ${e.message ?: e::class.simpleName}")
        }
        val best = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl && accepts(it.format?.mimeType) }
            .let { list -> quality.pick(list) { maxOf(it.averageBitrate, it.bitrate) } }
            ?: throw YtMusicException(
                "No direct audio stream for this song (got: " +
                    info.audioStreams.joinToString { "${it.deliveryMethod}/${it.format?.mimeType}/url=${it.isUrl}" }.ifEmpty { "none" } + ")",
            )
        YtStream(
            url = best.content,
            mimeType = best.format?.mimeType ?: "audio/*",
            bitrate = maxOf(best.averageBitrate, best.bitrate),
            headers = emptyMap(),
        )
    }

    /** NewPipe's HTTP hook, on OkHttp. */
    private class OkHttpDownloader(base: OkHttpClient) : Downloader() {
        private val client = base.newBuilder().readTimeout(30, TimeUnit.SECONDS).build()

        override fun execute(request: Request): Response {
            val body = request.dataToSend()?.toRequestBody()
            val builder = okhttp3.Request.Builder().url(request.url()).method(request.httpMethod(), body)
                .header("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            client.newCall(builder.build()).execute().use { r ->
                if (r.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
                return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
            }
        }
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
