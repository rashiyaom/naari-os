package com.naari.companion.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Original vector icons for Naari Companion UI.
 * Conforms strictly to PRD: Original consistent vector icons; no SF fonts.
 */
object NaariIcons {
    val Watch: ImageVector by lazy {
        ImageVector.Builder(
            name = "Watch",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Watch body
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 6f)
                lineTo(17f, 6f)
                curveTo(19.2f, 6f, 21f, 7.8f, 21f, 10f)
                lineTo(21f, 14f)
                curveTo(21f, 16.2f, 19.2f, 18f, 17f, 18f)
                lineTo(7f, 18f)
                curveTo(4.8f, 18f, 3f, 16.2f, 3f, 14f)
                lineTo(3f, 10f)
                curveTo(3f, 7.8f, 4.8f, 6f, 7f, 6f)
                close()
            }
            // Top strap
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 6f)
                lineTo(9f, 2f)
                lineTo(15f, 2f)
                lineTo(15f, 6f)
            }
            // Bottom strap
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 18f)
                lineTo(9f, 22f)
                lineTo(15f, 22f)
                lineTo(15f, 18f)
            }
        }.build()
    }

    val Shield: ImageVector by lazy {
        ImageVector.Builder(
            name = "Shield",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 22f)
                curveTo(12f, 22f, 20f, 18f, 20f, 12f)
                lineTo(20f, 5f)
                lineTo(12f, 2f)
                lineTo(4f, 5f)
                lineTo(4f, 12f)
                curveTo(4f, 18f, 12f, 22f, 12f, 22f)
                close()
            }
        }.build()
    }

    val Mic: ImageVector by lazy {
        ImageVector.Builder(
            name = "Mic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f)
                curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
                lineTo(9f, 11f)
                curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
                curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
                lineTo(15f, 5f)
                curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 10f)
                lineTo(19f, 11f)
                curveTo(19f, 14.87f, 15.87f, 18f, 12f, 18f)
                curveTo(8.13f, 18f, 5f, 14.87f, 5f, 11f)
                lineTo(5f, 10f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 18f)
                lineTo(12f, 22f)
                moveTo(8f, 22f)
                lineTo(16f, 22f)
            }
        }.build()
    }

    val Sliders: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sliders",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 21f)
                lineTo(4f, 14f)
                moveTo(4f, 10f)
                lineTo(4f, 3f)
                moveTo(12f, 21f)
                lineTo(12f, 12f)
                moveTo(12f, 8f)
                lineTo(12f, 3f)
                moveTo(20f, 21f)
                lineTo(20f, 16f)
                moveTo(20f, 12f)
                lineTo(20f, 3f)
                moveTo(1f, 14f)
                lineTo(7f, 14f)
                moveTo(9f, 8f)
                lineTo(15f, 8f)
                moveTo(17f, 16f)
                lineTo(23f, 16f)
            }
        }.build()
    }

    val Alert: ImageVector by lazy {
        ImageVector.Builder(
            name = "Alert",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.29f, 3.86f)
                lineTo(1.82f, 18f)
                curveTo(1.64f, 18.3f, 1.55f, 18.65f, 1.55f, 19f)
                curveTo(1.55f, 20.1f, 2.45f, 21f, 3.55f, 21f)
                lineTo(20.45f, 21f)
                curveTo(20.8f, 21f, 21.15f, 20.91f, 21.45f, 20.73f)
                curveTo(22.38f, 20.18f, 22.69f, 18.97f, 22.14f, 18.04f)
                lineTo(13.71f, 3.86f)
                curveTo(12.98f, 2.61f, 11.02f, 2.61f, 10.29f, 3.86f)
                close()
                moveTo(12f, 9f)
                lineTo(12f, 13f)
                moveTo(12f, 17f)
                lineTo(12.01f, 17f)
            }
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 15f)
                curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
                curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f)
                curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
                curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19.4f, 15f)
                lineTo(20.4f, 16.7f)
                lineTo(18.7f, 19.7f)
                lineTo(16.8f, 19f)
                lineTo(15.2f, 20.2f)
                lineTo(14.8f, 22.2f)
                lineTo(11.2f, 22.2f)
                lineTo(10.8f, 20.2f)
                lineTo(9.2f, 19f)
                lineTo(7.3f, 19.7f)
                lineTo(5.6f, 16.7f)
                lineTo(6.6f, 15f)
                lineTo(5.8f, 13.5f)
                lineTo(3.8f, 13.1f)
                lineTo(3.8f, 9.5f)
                lineTo(5.8f, 9.1f)
                lineTo(6.6f, 7.6f)
                lineTo(5.6f, 5.9f)
                lineTo(7.3f, 2.9f)
                lineTo(9.2f, 3.6f)
                lineTo(10.8f, 2.4f)
                lineTo(11.2f, 0.4f)
                lineTo(14.8f, 0.4f)
                lineTo(15.2f, 2.4f)
                lineTo(16.8f, 3.6f)
                lineTo(18.7f, 2.9f)
                lineTo(20.4f, 5.9f)
                lineTo(19.4f, 7.6f)
                lineTo(20.2f, 9.1f)
                lineTo(22.2f, 9.5f)
                lineTo(22.2f, 13.1f)
                lineTo(20.2f, 13.5f)
                close()
            }
        }.build()
    }

    val History: ImageVector by lazy {
        ImageVector.Builder(
            name = "History",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(8.5f, 21f, 5.5f, 19f, 4f, 16f)
                moveTo(3f, 4f)
                lineTo(3f, 9f)
                lineTo(8f, 9f)
                moveTo(12f, 7f)
                lineTo(12f, 12f)
                lineTo(16f, 14f)
            }
        }.build()
    }

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 12f)
                lineTo(5f, 12f)
                moveTo(12f, 19f)
                lineTo(5f, 12f)
                lineTo(12f, 5f)
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "Play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 4f)
                lineTo(19f, 12f)
                lineTo(6f, 20f)
                close()
            }
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 4f)
                lineTo(10f, 4f)
                lineTo(10f, 20f)
                lineTo(6f, 20f)
                close()
                moveTo(14f, 4f)
                lineTo(18f, 4f)
                lineTo(18f, 20f)
                lineTo(14f, 20f)
                close()
            }
        }.build()
    }

    val Walk: ImageVector by lazy {
        ImageVector.Builder(
            name = "Walk",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Head
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1f
            ) {
                moveTo(13.5f, 5.5f)
                curveTo(13.5f, 6.88f, 12.38f, 8f, 11f, 8f)
                curveTo(9.62f, 8f, 8.5f, 6.88f, 8.5f, 5.5f)
                curveTo(8.5f, 4.12f, 9.62f, 3f, 11f, 3f)
                curveTo(12.38f, 3f, 13.5f, 4.12f, 13.5f, 5.5f)
                close()
            }
            // Torso & limbs
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 10f)
                lineTo(10.5f, 8.8f)
                lineTo(14f, 11.5f)
                lineTo(17.5f, 10.5f)
                moveTo(11f, 9.5f)
                lineTo(10f, 14.5f)
                lineTo(6f, 18f)
                moveTo(10f, 14.5f)
                lineTo(14.5f, 18.5f)
                lineTo(15.5f, 22f)
            }
        }.build()
    }

    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "CheckCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Circle
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                close()
            }
            // Checkmark
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7.5f, 12.5f)
                lineTo(10.5f, 15.5f)
                lineTo(16.5f, 9f)
            }
        }.build()
    }

    val LocationPin: ImageVector by lazy {
        ImageVector.Builder(
            name = "LocationPin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f)
                curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
                curveTo(5f, 14.25f, 12f, 22f, 12f, 22f)
                curveTo(12f, 22f, 19f, 14.25f, 19f, 9f)
                curveTo(19f, 5.13f, 15.87f, 2f, 12f, 2f)
                close()
            }
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1f
            ) {
                moveTo(12f, 7f)
                curveTo(13.1f, 7f, 14f, 7.9f, 14f, 9f)
                curveTo(14f, 10.1f, 13.1f, 11f, 12f, 11f)
                curveTo(10.9f, 11f, 10f, 10.1f, 10f, 9f)
                curveTo(10f, 7.9f, 10.9f, 7f, 12f, 7f)
                close()
            }
        }.build()
    }
}
