package app.seekeratlas.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    private val tbilisi = LatLon(41.6938, 44.8015)
    private val amsterdam = LatLon(52.3676, 4.9041)

    @Test fun distanceTbilisiAmsterdamIsAbout3200Km() {
        val d = Geo.distanceKm(tbilisi, amsterdam)
        assertEquals(3200.0, d, 30.0)
    }

    @Test fun distanceToSelfIsZero() {
        assertEquals(0.0, Geo.distanceKm(tbilisi, tbilisi), 1e-9)
    }

    @Test fun perfectGuessGivesMaxScore() {
        assertEquals(Geo.MAX_SCORE, Geo.score(0.0))
        assertEquals(Geo.MAX_SCORE, Geo.score(0.04))
    }

    @Test fun scoreDecreasesWithDistance() {
        val near = Geo.score(10.0)
        val mid = Geo.score(500.0)
        val far = Geo.score(5000.0)
        assertTrue(near > mid && mid > far)
        assertTrue(far >= 0)
    }

    @Test fun antipodeScoresAlmostNothing() {
        assertTrue(Geo.score(20_000.0) < 10)
    }

    @Test fun projectionRoundTrips() {
        val p = Projection.toPoint(tbilisi, 2000f, 1000f)
        val back = Projection.toLatLon(p.first, p.second, 2000f, 1000f)
        assertEquals(tbilisi.lat, back.lat, 1e-3)
        assertEquals(tbilisi.lon, back.lon, 1e-3)
    }

    @Test fun projectionCorners() {
        val tl = Projection.toLatLon(0f, 0f, 360f, 180f)
        assertEquals(90.0, tl.lat, 1e-6)
        assertEquals(-180.0, tl.lon, 1e-6)
        val center = Projection.toPoint(LatLon(0.0, 0.0), 360f, 180f)
        assertEquals(180f, center.first, 1e-4f)
        assertEquals(90f, center.second, 1e-4f)
    }

    @Test fun projectionClampsOutOfRange() {
        val p = Projection.toLatLon(-50f, 500f, 360f, 180f)
        assertEquals(-90.0, p.lat, 1e-6)
        assertEquals(-180.0, p.lon, 1e-6)
    }
}
