package de.lolo.rssreader.ui.screens

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

private val FAVORITE_ICON_SIZE = 22.dp
private val FAVORITE_ICON_SHAPE = RoundedCornerShape(6.dp)

/**
 * Icon des virtuellen Feeds "Favoriten": der Favoritenstern fuellt die Icon-Flaeche
 * (Zacken beruehren den Rand), in seinem Flaechenschwerpunkt steht das "F".
 */
@Composable
internal fun FavoriteFeedIcon(
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val tileColor = MaterialTheme.colorScheme.surfaceVariant
    // Feste Grauwerte statt Theme-Farben: Der Stern ist das Markenzeichen des
    // Favoriten-Eintrags und soll in hellem und dunklem Modus gleich aussehen
    // (heller Kern, dunkle Zacken, dunkles "F").
    val starCoreColor = Color(0xFFFAFAFA)
    val starEdgeColor = Color(0xFF787878)
    val outlineColor = Color(0xFFFFFFFF)
    val letterColor = Color(0xFF101010)

    Surface(
        modifier = modifier
            .size(FAVORITE_ICON_SIZE)
            .alpha(alpha),
        shape = FAVORITE_ICON_SHAPE,
        color = tileColor
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val side = size.minDimension
            val star = Path().apply {
                FavoriteStarGeometry.points.forEachIndexed { index, point ->
                    val x = point.x.toFloat() * side
                    val y = point.y.toFloat() * side
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(
                path = star,
                brush = Brush.radialGradient(
                    colors = listOf(starCoreColor, starEdgeColor),
                    center = Offset(side * 0.5f, side * 0.38f),
                    radius = side
                )
            )
            drawPath(
                path = star,
                color = outlineColor,
                style = Stroke(width = (side * 0.009f).coerceAtLeast(0.8f))
            )

            val letterCenter = FavoriteStarGeometry.centroid
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    isAntiAlias = true
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = side * FavoriteStarGeometry.LETTER_HEIGHT_RATIO.toFloat()
                    color = letterColor.toArgb()
                    textAlign = Paint.Align.CENTER
                }
                val textBounds = Rect()
                paint.getTextBounds("F", 0, 1, textBounds)
                val baseline = side * letterCenter.y.toFloat() - (textBounds.top + textBounds.bottom) / 2f
                canvas.nativeCanvas.drawText("F", side * letterCenter.x.toFloat(), baseline, paint)
            }
        }
    }
}
