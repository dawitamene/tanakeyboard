package com.addiyon.keyboard.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The classic keyboard "shift" glyph (⇧) -- a triangular arrowhead sitting
 * on a rectangular base, the same silhouette used by Gboard, iOS, and
 * physical keyboards. Built by hand rather than pulled from an icon
 * library because no bundled icon set (including material-icons-extended)
 * reliably ships this exact shape under a predictable name.
 *
 * [path] fill/stroke colors are placeholders (Color.Black) -- Icon()
 * applies its own tint on top via ColorFilter, so the baked-in color here
 * doesn't matter, only which pixels are opaque.
 */

/** Filled version -- solid arrowhead. Used for SHIFT and CAPS_LOCK states. */
val ShiftIconFilled: ImageVector
    get() = ImageVector.Builder(
        name = "ShiftIconFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            lineTo(21f, 11.5f)
            lineTo(16f, 11.5f)
            lineTo(16f, 20f)
            lineTo(8f, 20f)
            lineTo(8f, 11.5f)
            lineTo(3f, 11.5f)
            close()
        }
    }.build()

/** Outlined version -- stroked only. Used for the OFF (inactive) state. */
val ShiftIconOutlined: ImageVector
    get() = ImageVector.Builder(
        name = "ShiftIconOutlined",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 2f)
            lineTo(21f, 11.5f)
            lineTo(16f, 11.5f)
            lineTo(16f, 20f)
            lineTo(8f, 20f)
            lineTo(8f, 11.5f)
            lineTo(3f, 11.5f)
            close()
        }
    }.build()