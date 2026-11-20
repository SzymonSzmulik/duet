package app.duet.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons drawn from the SVG paths in design/screens (24x24 viewBox). Tint is applied by Icon(). */
object DuetIcons {
    val Home = filled("Home", "M12 3 3 11h2v9h5v-6h4v6h5v-9h2z")
    val Search = stroked("Search", 2.2f, "M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0", "m20 20-4-4")
    val Library = stroked("Library", 2.2f, "M4 4v16M9 4v16", "m14 5 5 14")
    val Settings = stroked(
        "Settings", 2f,
        "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0",
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    )
    val Add = stroked("Add", 2.2f, "M12 5v14M5 12h14")
    val Clear = stroked("Clear", 2.4f, "M6 6l12 12M18 6 6 18")
    val Pause = filled("Pause", "M7 5h2a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM15 5h2a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z")
    val Play = filled("Play", "M8 5.5v13a1 1 0 0 0 1.5.86l10.5-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z")

    // From design/screens/NowPlaying.dc.html.
    val Previous = filled("Previous", "M6.3 5h0a1.3 1.3 0 0 1 1.3 1.3v11.4a1.3 1.3 0 0 1-2.6 0V6.3A1.3 1.3 0 0 1 6.3 5zM19 5.5v13a1 1 0 0 1-1.6.8l-9-6.5a1 1 0 0 1 0-1.6l9-6.5A1 1 0 0 1 19 5.5z")
    val Next = filled("Next", "M17.7 5h0a1.3 1.3 0 0 1 1.3 1.3v11.4a1.3 1.3 0 0 1-2.6 0V6.3A1.3 1.3 0 0 1 17.7 5zM5 5.5v13a1 1 0 0 0 1.6.8l9-6.5a1 1 0 0 0 0-1.6l-9-6.5A1 1 0 0 0 5 5.5z")
    val Shuffle = stroked("Shuffle", 2.2f, "M3 6h3.5a5 5 0 0 1 4 2l3 4a5 5 0 0 0 4 2H21M3 18h3.5a5 5 0 0 0 4-2M14 8a5 5 0 0 1 3.5-2H21M18 3l3 3-3 3M18 15l3 3-3 3")
    val Repeat = stroked("Repeat", 2.2f, "M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3")
    val RepeatOne = stroked("Repeat one", 2.2f, "M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3", "M11 10.5l1.5-1v5")
    val Queue = stroked("Queue", 2.2f, "M3 6h12M3 11h12M3 16h7M18 14v6M18 14l3-1", "M18 20a2 2 0 1 1-4 0a2 2 0 1 1 4 0")
    // Simple strokes in the same style.
    val ChevronDown = stroked("Close", 2.4f, "m6 9 6 6 6-6")
    val Back = stroked("Back", 2.4f, "M15 5l-7 7 7 7")
    val ChevronRight = stroked("Open", 2.2f, "m9 5 7 7-7 7")
    val Share = stroked("Share", 2f, "M12 15V3 M8 7l4-4 4 4 M5 11v9h14v-9")
    val Person = stroked("Profile", 2f, "M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0", "M4 21a8 8 0 0 1 16 0")
    val Extension = stroked("Sources", 2f, "M10 3h4v3a2 2 0 1 0 4 0V3h3v7h-3a2 2 0 1 0 0 4h3v7h-7v-3a2 2 0 1 0-4 0v3H3v-7h3a2 2 0 1 0 0-4H3V3z")
    val Sound = stroked("Sound", 2f, "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3", "M1 14h6M9 8h6M17 16h6")
    val Appearance = stroked("Appearance", 2f, "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0", "M12 3v18", "M12 7h6M12 11h8.6M12 15h8M12 19h4")
    val History = stroked("History", 2f, "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0", "M12 7v5l3.5 2")
    val Stats = stroked("Stats", 2.2f, "M5 20v-8M12 20V5M19 20v-5")
    val Sleep = stroked("Sleep timer", 2f, "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Car = stroked("Car mode", 2f, "M5 16V11l2-5h10l2 5v5", "M3 16h18v3H3z", "M7 19v2M17 19v2", "M5 11h14")
    val Radio = stroked("Radio", 2f, "M4 9h16v11H4z", "M8 4l9 5", "M9 15a1.5 1.5 0 1 1-3 0a1.5 1.5 0 1 1 3 0", "M12 13h5M12 16h5")
    val Lyrics = stroked("Lyrics", 2f, "M4 5h16v11H9l-5 4z", "M8 9h8M8 12h5")
    val Download = stroked("Save", 2.2f, "M12 4v11M7 10l5 5 5-5", "M5 20h14")
    val Check = stroked("Saved", 2.4f, "m5 12 5 5 9-10")
    val Heart = stroked("Favourite", 2f, "M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7a4.3 4.3 0 0 1 7.5 2.8C19.5 15.4 12 20 12 20z")
    val HeartFilled = filled("Favourited", "M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7a4.3 4.3 0 0 1 7.5 2.8C19.5 15.4 12 20 12 20z")
    val More = filled("More options", "M4 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0M10 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0M16 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0")
    val Info = stroked("About", 2f, "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0", "M12 11v6M12 7.5v.5")

    private fun builder(name: String) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    )

    private fun filled(name: String, d: String): ImageVector =
        builder(name).addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black)).build()

    private fun stroked(name: String, width: Float, vararg ds: String): ImageVector =
        builder(name).apply {
            ds.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
