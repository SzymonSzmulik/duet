package app.duet.music.ui.player

import coil3.request.ImageRequest
import coil3.request.allowHardware

internal actual fun ImageRequest.Builder.readablePixels(): ImageRequest.Builder = allowHardware(false)
