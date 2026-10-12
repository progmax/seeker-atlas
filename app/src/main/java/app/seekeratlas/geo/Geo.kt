package app.seekeratlas.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

object Geo {
    const val MAX_SCORE = 5000
    private const val EARTH_RADIUS_KM = 6371.0
    private const val PERFECT_KM = 0.05
    private const val SCALE_KM = 1500.0

    fun distanceKm(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** GeoGuessr-style exponential falloff: ~4560 at 100 km, ~1840 at 1500 km, ~0 at the antipode. */
    fun score(distanceKm: Double): Int {
        if (distanceKm <= PERFECT_KM) return MAX_SCORE
        return (MAX_SCORE * exp(-distanceKm / SCALE_KM)).roundToInt()
    }
}

/** Equirectangular projection between lat/lon and a width×height image. */
object Projection {
    fun toPoint(p: LatLon, width: Float, height: Float): Pair<Float, Float> =
        ((p.lon + 180.0) / 360.0 * width).toFloat() to ((90.0 - p.lat) / 180.0 * height).toFloat()

    fun toLatLon(x: Float, y: Float, width: Float, height: Float): LatLon {
        val lon = (x / width * 360.0 - 180.0).coerceIn(-180.0, 180.0)
        val lat = (90.0 - y / height * 180.0).coerceIn(-90.0, 90.0)
        return LatLon(lat, lon)
    }
}

/** A lat/lon box; used for the paid continent hint. */
data class Region(val name: String, val north: Double, val south: Double, val west: Double, val east: Double) {
    fun contains(p: LatLon) = p.lat in south..north && p.lon in west..east

    companion object {
        val CONTINENTS = listOf(
            Region("Europe", 72.0, 34.0, -25.0, 45.0),
            Region("Asia", 78.0, -11.0, 25.0, 180.0),
            Region("Africa", 38.0, -35.0, -18.0, 52.0),
            Region("North America", 84.0, 7.0, -170.0, -52.0),
            Region("South America", 13.0, -56.0, -82.0, -34.0),
            Region("Oceania", 0.0, -48.0, 110.0, 180.0),
            Region("Antarctica", -60.0, -90.0, -180.0, 180.0),
        )

        /** The named continent if it really contains the point, else the first continent box that does. */
        fun hintFor(p: LatLon, continent: String?): Region? =
            CONTINENTS.firstOrNull { it.name == continent && it.contains(p) } ?: CONTINENTS.firstOrNull { it.contains(p) }
    }
}
