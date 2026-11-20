package app.duet.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import kotlin.math.max
import kotlin.math.min

/** Android draws Coil's hardware bitmaps only on screen; ask for one we can read pixels from. */
internal expect fun ImageRequest.Builder.readablePixels(): ImageRequest.Builder

/**
 * The dominant color of a cover (SPEC §1: "art-derived gradient"), or [fallback] until it is
 * known. Platform-neutral replacement for Android's Palette: samples a 24x24 copy of the image.
 */
@Composable
fun rememberArtColor(url: String?, fallback: Color): Color {
    var color by remember(url) { mutableStateOf<Color?>(null) }
    val context = LocalPlatformContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).size(CoilSize(64, 64)).readablePixels().build() }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsState()
    LaunchedEffect(state) {
        val s = state
        if (s is AsyncImagePainter.State.Success) color = dominantColor(s.painter)
    }
    return if (url == null) fallback else color ?: fallback
}

internal fun dominantColor(painter: Painter): Color? {
    val side = 24
    val bitmap = ImageBitmap(side, side)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
        with(painter) { draw(size) }
    }
    val pixels = bitmap.toPixelMap()
    var r = 0f; var g = 0f; var b = 0f; var weight = 0f
    for (x in 0 until side) for (y in 0 until side) {
        val c = pixels[x, y]
        val hi = max(c.red, max(c.green, c.blue))
        val lo = min(c.red, min(c.green, c.blue))
        // Favor colorful, mid-bright pixels over greys, near-black and near-white.
        val w = (hi - lo) + 0.05f * (if (hi in 0.12f..0.95f) 1f else 0f)
        r += c.red * w; g += c.green * w; b += c.blue * w; weight += w
    }
    if (weight <= 0f) return null
    return Color(r / weight, g / weight, b / weight)
}

/** Background stops for Now Playing: dark enough that white text keeps >= 4.5:1 contrast. */
fun playerGradient(art: Color, ground: Color): List<Color> {
    var top = lerp(art, Color.Black, 0.35f)
    while (top.luminance() > 0.12f) top = lerp(top, Color.Black, 0.15f)
    return listOf(top, lerp(top, Color.Black, 0.35f), lerp(top, ground, 0.8f))
}

/** A second colour for two-tone gradients: the art colour with its hue turned by [degrees], kept as saturated. */
fun companionColor(art: Color, degrees: Float = 40f): Color {
    val r = art.red; val g = art.green; val b = art.blue
    val hi = max(r, max(g, b)); val lo = min(r, min(g, b)); val d = hi - lo
    var h = when {
        d == 0f -> 0f
        hi == r -> 60f * (((g - b) / d) % 6f)
        hi == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    h = ((h + degrees) % 360f + 360f) % 360f
    val s = if (hi == 0f) 0f else d / hi
    return Color.hsv(h, s.coerceAtLeast(0.35f), hi.coerceIn(0.35f, 0.85f))
}
