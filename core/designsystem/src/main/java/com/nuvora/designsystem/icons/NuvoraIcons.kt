package com.nuvora.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Ícones próprios, desenhados em grade de 24. Evitam a biblioteca de ícones do Material
 * (vários MB) e mantêm o traço consistente com a marca.
 */
object NuvoraIcons {
    val Play by lazy { icon("play", filled = true, "M7 4v16l13-8z") }
    val Heart by lazy { icon("heart", filled = false, HEART) }
    val HeartFilled by lazy { icon("heart_filled", filled = true, HEART) }
    val Search by lazy { icon("search", filled = false, "M10 10m-7 0a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M21 21l-6 -6") }
    val Home by lazy {
        icon(
            "home", filled = false,
            "M5 12H3l9 -9l9 9h-2",
            "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-7",
            "M9 21v-6a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v6",
        )
    }
    val History by lazy { icon("history", filled = false, "M12 8v4l2 2", "M3.05 11a9 9 0 1 1 0.5 4m-0.5 5v-5h5") }
    val Settings by lazy {
        icon(
            "settings", filled = false,
            "M4 6h8", "M16 6h4", "M14 6m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            "M4 12h2", "M10 12h10", "M8 12m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            "M4 18h11", "M19 18h1", "M17 18m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        )
    }
    val Categories by lazy { icon("categories", filled = false, "M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h6v6h-6z") }
    val Plus by lazy { icon("plus", filled = false, "M12 5v14", "M5 12h14") }
    val User by lazy {
        icon(
            "user", filled = false,
            "M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
            "M12 10m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M6.17 18.85A4 4 0 0 1 10 16h4a4 4 0 0 1 3.83 2.85",
        )
    }
    val Refresh by lazy { icon("refresh", filled = false, "M20 11A8.1 8.1 0 0 0 4.5 9M4 5v4h4", "M4 13a8.1 8.1 0 0 0 15.5 2m0.5 4v-4h-4") }
    val NoConnection by lazy {
        icon(
            "no_connection", filled = false,
            "M3 3l18 18",
            "M18 18H7a4 4 0 0 1 -0.88 -7.9A5 5 0 0 1 9.3 6.3",
            "M12.7 6.1A5 5 0 0 1 17 10h1a4 4 0 0 1 2.4 7.2",
        )
    }
    val Source by lazy {
        icon(
            "source", filled = false,
            "M9.8 6l8.2 8.2l-2 2.1a5.8 5.8 0 1 1 -8.2 -8.2z",
            "M4 20l3.5 -3.5", "M15 4l-3.5 3.5", "M20 9l-3.5 3.5",
        )
    }
    val ChevronRight by lazy { icon("chevron_right", filled = false, "M9 6l6 6l-6 6") }
    val ChevronLeft by lazy { icon("chevron_left", filled = false, "M15 6l-6 6l6 6") }
    val Pause by lazy { icon("pause", filled = true, "M6 5h4v14H6z", "M14 5h4v14h-4z") }
    val Rewind by lazy { icon("rewind", filled = false, "M11 7l-5 5l5 5", "M18 7l-5 5l5 5") }
    val Forward by lazy { icon("forward", filled = false, "M13 7l5 5l-5 5", "M6 7l5 5l-5 5") }
    val Subtitles by lazy {
        icon(
            "subtitles", filled = false,
            "M3 7a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2z",
            "M7 15h4", "M15 15h2", "M7 11h2", "M13 11h4",
        )
    }
    val Audio by lazy {
        icon(
            "audio", filled = false,
            "M6 17m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M16 17m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M9 17V4h10v13", "M9 8h10",
        )
    }
    val VolumeDown by lazy { icon("volume_down", filled = false, SPEAKER, "M16 12h5") }
    val VolumeUp by lazy { icon("volume_up", filled = false, SPEAKER, "M16 12h5", "M18.5 9.5v5") }

    private const val SPEAKER = "M6 15H4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1h2l3.5 -4.5A0.8 0.8 0 0 1 11 5v14a0.8 0.8 0 0 1 -1.5 0.5L6 15"

    private const val HEART = "M19.5 12.57L12 20l-7.5 -7.43A5 5 0 1 1 12 6.01a5 5 0 1 1 7.5 6.57z"

    private fun icon(name: String, filled: Boolean, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (filled) SolidColor(Color.White) else null,
                    stroke = if (filled) null else SolidColor(Color.White),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
