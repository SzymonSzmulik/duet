package app.duet.music.ui.login

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.duet.music.ui.components.PageScaffold
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetTheme
import app.duet.music.ui.theme.DuetType

/**
 * The sign-in screen in Duet's own look: the same back-arrow header as Settings, one sentence that
 * says what is happening, a reassurance about the password, then the service's own page ([web]).
 */
fun webLoginView(context: Context, serviceName: String, accent: Long, web: View, loading: StateFlow<Boolean>, onCancel: () -> Unit): View =
    ComposeView(context).apply {
        setContent {
            DuetTheme(accent = Color(accent)) {
                PageScaffold(
                    title = "Sign in to $serviceName",
                    onBack = onCancel,

                    backLabel = "Cancel sign-in",
                    scroll = false,
                ) {
                    val isLoading by loading.collectAsState()
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = {
                                (web.parent as? ViewGroup)?.removeView(web)
                                // Match the parent: with the default "wrap content" height, the page's
                                // 100vh/100dvh are 0 and Spotify's login draws nothing (D30).
                                web.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                web
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isLoading) {
                            Column(
                                Modifier.fillMaxSize().background(DuetColors.Ground),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                            ) {
                                CircularProgressIndicator(color = DuetColors.Accent)
                                Text("Loading $serviceName's sign-in page…", style = DuetType.RowSub, color = DuetColors.TextDim)
                            }
                        }
                    }
                }
            }
        }
    }
