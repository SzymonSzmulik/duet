package app.duet.music.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import app.duet.music.ui.theme.DuetColors

/**
 * The song's colours behind the player and the lyrics: the art colour and a companion colour
 * drift slowly as two soft glows over a deep gradient; [showArt] lays the artwork on the top part,
 * fading into the colours.
 */
@Composable
fun ArtBackdrop(artworkUrl: String?, art: Color, showArt: Boolean, artFraction: Float = 0.62f) {
    val second = companionColor(art)
    val deep = playerGradient(art, DuetColors.Ground)
    val drift by rememberInfiniteTransition(label = "backdrop").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
    )
    val edge = lerp(art, Color.Black, 0.25f)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(edge, deep[0], deep[1], DuetColors.Ground)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(Brush.radialGradient(
                listOf(art.copy(alpha = 0.75f), Color.Transparent),
                center = Offset(w * (0.15f + 0.5f * drift), h * (0.30f + 0.15f * drift)), radius = w * 0.95f,
            ))
            drawRect(Brush.radialGradient(
                listOf(second.copy(alpha = 0.6f), Color.Transparent),
                center = Offset(w * (0.9f - 0.45f * drift), h * (0.75f - 0.2f * drift)), radius = w * 0.9f,
            ))
        }
        if (showArt) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(artFraction)) {
                // The artwork cross-fades when the song changes.
                androidx.compose.animation.Crossfade(artworkUrl, animationSpec = androidx.compose.animation.core.tween(450), label = "art") { url ->
                    AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f), 0.2f to Color.Transparent, 0.7f to Color.Transparent, 1f to edge,
                )))
            }
        }
        // Keeps white text readable at the bottom.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
    }
}
