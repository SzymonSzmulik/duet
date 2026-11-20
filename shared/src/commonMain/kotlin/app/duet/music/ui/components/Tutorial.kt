package app.duet.music.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType
import app.duet.music.ui.theme.duetShape
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private class Slide(val icon: ImageVector, val title: String, val body: String)

private val slides = listOf(
    Slide(DuetIcons.Home, "Welcome to Duet", "Spotify and YouTube Music in one app. One search, one queue, one player."),
    Slide(DuetIcons.Search, "Search once", "Type a song, artist or album. Duet looks on both services and shows each song once. Tap it to play."),
    Slide(DuetIcons.More, "Hold any song", "Press and hold a song (or tap ⋯) to like it, add it to a playlist, play it next, share it or open the artist."),
    Slide(DuetIcons.Library, "Your playlists", "In Library, make playlists that mix Spotify and YouTube Music songs. Tap Add songs to fill them."),
    Slide(DuetIcons.Radio, "Live radio", "The Radio tab has stations from around the world. Tap the heart to keep your favourites."),
    Slide(DuetIcons.Person, "Sign in to play more", "YouTube Music works without an account. Sign in to Spotify to play Spotify songs and see your library."),
)

/**
 * The first-launch tour: six swipeable slides, Skip on every one, and on the last one a button to
 * connect accounts. It can be shown again from Settings > About.
 */
@Composable
fun Tutorial(onDone: () -> Unit, onConnect: () -> Unit) {
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == slides.lastIndex
    Box(Modifier.fillMaxSize().background(DuetColors.Ground).statusBarsPadding().navigationBarsPadding()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            val s = slides[i]
            // Each slide drifts and fades with the swipe.
            val offset = ((pager.currentPage - i) + pager.currentPageOffsetFraction)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp).graphicsLayer {
                    alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f) * 0.8f
                    translationX = offset * size.width * 0.25f
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(132.dp).clip(app.duet.music.ui.theme.duetShape(16)).background(DuetColors.Surface), contentAlignment = Alignment.Center) {
                    Icon(s.icon, contentDescription = null, tint = DuetColors.Accent, modifier = Modifier.size(56.dp))
                }
                Spacer(Modifier.height(36.dp))
                Text(DuetType.titled(s.title), style = DuetType.Display(30), color = DuetColors.Text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text(s.body, fontSize = 16.sp, lineHeight = 23.sp, color = DuetColors.TextDim, textAlign = TextAlign.Center)
            }
        }
        // Drawn after the pager so it sits on top and gets the tap.
        Text(
            "Skip", style = DuetType.Chip, color = DuetColors.TextDim,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clip(duetShape(16))
                .clickable(role = Role.Button, onClickLabel = "Skip the tutorial", onClick = onDone).padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slides.indices.forEach { i ->
                    val w by animateDpAsState(if (i == pager.currentPage) 22.dp else 8.dp, spring(dampingRatio = 0.7f), label = "dot")
                    Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(if (i == pager.currentPage) DuetColors.Accent else DuetColors.Line))
                }
            }
            val press by animateFloatAsState(if (last) 1f else 0.98f, label = "next")
            PrimaryButton(
                if (last) "Start listening" else "Next",
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = press; scaleY = press },
            )
            if (last) Text(
                "Connect Spotify or YouTube Music", style = DuetType.Chip, color = DuetColors.Text,
                modifier = Modifier.clip(duetShape(16)).clickable(role = Role.Button) { onDone(); onConnect() }.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
