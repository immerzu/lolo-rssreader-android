package de.lolo.rssreader.ui.screens

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometrie des Favoriten-Sterns (Icon des virtuellen Feeds "Favoriten").
 *
 * Der Stern ist so auf die Bounding-Box 0..1 normiert, dass seine Zacken den Rand
 * der Icon-Flaeche beruehren, ohne ihn zu ueberschreiten. Das "F" wird nicht auf
 * die Mitte der Bounding-Box gesetzt, sondern auf den [centroid] - die obere
 * Spitze ist laenger als die beiden unteren Zacken, deshalb liegt der
 * Flaechenschwerpunkt rund 5,3 % der Kante unterhalb der Box-Mitte.
 */
internal object FavoriteStarGeometry {

    data class Point(val x: Double, val y: Double)

    /** Verhaeltnis Innen- zu Aussenradius der fuenf Zacken. */
    const val INNER_RADIUS_RATIO = 0.47

    /** Anteil der Buchstabenhoehe an der Icon-Kante. */
    const val LETTER_HEIGHT_RATIO = 0.44

    /** Zehn Eckpunkte (fuenf Zacken, fuenf Innenpunkte), normiert auf die Bounding-Box. */
    val points: List<Point> by lazy { normalize(rawPoints()) }

    /** Flaechenschwerpunkt des Sterns, normiert auf die Bounding-Box. */
    val centroid: Point by lazy { polygonCentroid(points) }

    private fun rawPoints(): List<Point> = (0 until 10).map { index ->
        val angle = -PI / 2 + index * PI / 5
        val radius = if (index % 2 == 0) 1.0 else INNER_RADIUS_RATIO
        Point(radius * cos(angle), radius * sin(angle))
    }

    private fun normalize(points: List<Point>): List<Point> {
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        return points.map {
            Point((it.x - minX) / (maxX - minX), (it.y - minY) / (maxY - minY))
        }
    }

    /** Flaechenschwerpunkt eines einfachen Polygons (Gauss-Shoelace). */
    internal fun polygonCentroid(points: List<Point>): Point {
        var twiceArea = 0.0
        var cx = 0.0
        var cy = 0.0
        for (index in points.indices) {
            val next = (index + 1) % points.size
            val cross = points[index].x * points[next].y - points[next].x * points[index].y
            twiceArea += cross
            cx += (points[index].x + points[next].x) * cross
            cy += (points[index].y + points[next].y) * cross
        }
        val area = twiceArea / 2.0
        return Point(cx / (6.0 * area), cy / (6.0 * area))
    }
}
