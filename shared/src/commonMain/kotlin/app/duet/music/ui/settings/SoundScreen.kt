package app.duet.music.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.settings.SoundSettings
import app.duet.music.ui.components.Chip
import app.duet.music.ui.components.PageScaffold
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.DuetType
import kotlin.math.roundToInt

/** Settings > Sound: equalizer, effects, speed, fades, spatial audio and streaming quality. */
@Composable
fun SoundScreen(sound: SoundSettings, onBack: () -> Unit) {
    // Mac: reverb, crossfade and spatial audio are not built for it (its audio engine has none), so they are not shown at all.
    val mac = app.duet.music.ui.desktop.LocalDesktop.current
    PageScaffold("Sound", onBack) {
        val viaYt by sound.spotifyViaYt.value.collectAsState()
        val ytAndRadio = if (viaYt) "Applies to all songs and radio" else "Applies to YouTube Music and radio, not Spotify"
        Header("Equalizer", ytAndRadio)
        Toggle("Equalizer", sound.eqOn.value.collectAsState().value) { sound.eqOn.set(it) }
        val bands = sound.eqBands.map { it.value.collectAsState().value }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoundSettings.EQ_PRESETS.forEach { (name, levels) ->
                Chip(name, selected = bands == levels) { sound.eqOn.set(true); levels.forEachIndexed { i, l -> sound.eqBands[i].set(l) } }
            }
        }
        SoundSettings.EQ_LABELS.forEachIndexed { i, label ->
            Level(label, bands[i] / 100f, -12f..12f, "dB") { sound.eqOn.set(true); sound.eqBands[i].set((it * 100).roundToInt()) }
        }

        Header("Effects", ytAndRadio)
        Level("Bass boost", sound.bass.value.collectAsState().value / 10f, 0f..100f, "%") { sound.bass.set((it * 10).roundToInt()) }
        Level("Loudness", sound.loudness.value.collectAsState().value / 100f, 0f..10f, "dB") { sound.loudness.set((it * 100).roundToInt()) }
        if (!mac) {
            Text("Reverb", style = DuetType.RowTitle, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
            val reverb by sound.reverb.value.collectAsState()
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundSettings.Reverb.entries.forEach { r -> Chip(r.label, selected = reverb == r.ordinal) { sound.reverb.set(r.ordinal) } }
            }
        }
        val bal by sound.balance.value.collectAsState()
        Level("Balance", bal, -1f..1f, "", format = { b -> when { b < -0.02f -> "L ${(-b * 100).roundToInt()}"; b > 0.02f -> "R ${(b * 100).roundToInt()}"; else -> "Centre" } }) {
            sound.balance.set(if (kotlin.math.abs(it) < 0.04f) 0f else it)
        }

        Header("Playback")
        val speed by sound.speed.value.collectAsState()
        Text("Speed", style = DuetType.RowTitle, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
        Text(ytAndRadio, style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.padding(start = 20.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v -> Chip("${v}×".replace(".0×", "×"), selected = speed == v) { sound.speed.set(v) } }
        }
        if (!mac) Level("Crossfade", sound.fadeSeconds.value.collectAsState().value.toFloat(), 0f..12f, "s", steps = 11, detail = "Between YouTube Music songs",
            format = { if (it < 0.5f) "Off" else "${it.roundToInt()} s" }) { sound.fadeSeconds.set(it.roundToInt()) }
        if (!mac) Toggle("Spatial audio", sound.spatial.value.collectAsState().value, "On headphones, with phones that support it (Android 12L+). $ytAndRadio") { sound.spatial.set(it) }

        Header("Streaming quality", if (viaYt) "Applies to YouTube Music and Spotify songs" else "Applies to YouTube Music only. Spotify picks its own quality")
        // A computer has no mobile data: one setting, the Wi-Fi one that it already uses.
        Quality(if (mac) "Quality" else "On Wi-Fi", sound.wifiQuality)
        if (!mac) Quality("On mobile data", sound.mobileQuality)

        Header("Spotify songs")
        Toggle(
            "Play from YouTube Music", viaYt,
            "Plays the same song from YouTube Music, so quality, equalizer and effects work on it. Now and then it's a different version; with no match it plays from Spotify.",
        ) { sound.spotifyViaYt.set(it) }
        Spacer(Modifier.height(8.dp))
        Text("Reset effects", style = DuetType.Chip, color = DuetColors.Accent,
            modifier = Modifier.padding(horizontal = 20.dp).androidxClick { sound.resetEffects() })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Header(t: String, appliesTo: String = "") {
    Text(t.uppercase(), fontSize = 12.sp, color = DuetColors.TextDim, letterSpacing = 1.sp, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp))
    if (appliesTo.isNotEmpty()) Text(appliesTo, style = DuetType.RowSub, color = DuetColors.Accent, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp))
}

@Composable
private fun Toggle(title: String, on: Boolean, detail: String = "", onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch, onValueChange = onChange).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DuetType.RowTitle)
            if (detail.isNotEmpty()) Text(detail, style = DuetType.RowSub, color = DuetColors.TextDim)
        }
        Switch(checked = on, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = DuetColors.Accent, checkedThumbColor = DuetColors.OnAccent))
    }
}

@Composable
private fun Level(
    title: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, steps: Int = 0, detail: String = "",
    format: (Float) -> String = { v -> (if (v > 0 && range.start < 0) "+" else "") + (if (unit == "dB") "${(v * 10).roundToInt() / 10f}" else "${v.roundToInt()}") + " $unit" },
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = DuetType.RowTitle, modifier = Modifier.weight(1f))
            Text(format(value), style = DuetType.RowSub, color = DuetColors.TextDim)
        }
        if (detail.isNotEmpty()) Text(detail, style = DuetType.RowSub, color = DuetColors.TextDim)
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = DuetColors.Text, activeTrackColor = DuetColors.Accent, inactiveTrackColor = DuetColors.SurfaceRaised),
        )
    }
}

@Composable
private fun Quality(title: String, setting: SoundSettings.Setting<Int>) {
    val q by setting.value.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = DuetType.RowTitle, modifier = Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoundSettings.Quality.shown.forEach { opt -> Chip(opt.label, selected = q == opt.ordinal) { setting.set(opt.ordinal) } }
        }
    }
}

private fun Modifier.androidxClick(onClick: () -> Unit) = this.then(Modifier.clickable(role = Role.Button, onClick = onClick))
