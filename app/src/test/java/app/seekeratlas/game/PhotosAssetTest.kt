package app.seekeratlas.game

import app.seekeratlas.geo.Region
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the bundled photo set: every photo must load and must have a paid hint that actually shows its continent. */
class PhotosAssetTest {
    private val assets = File("src/main/assets")
    private val photos: List<Photo> = Json { ignoreUnknownKeys = true }
        .decodeFromString(File(assets, "photos.json").readText())

    @Test fun everyPhotoFileExistsAndIdsAreUnique() {
        assertTrue(photos.size >= 50)
        assertEquals(photos.size, photos.map { it.id }.toSet().size)
        photos.forEach { assertTrue(it.file, File(assets, it.file).isFile) }
    }

    @Test fun everyPhotoGetsAHintMatchingItsContinent() {
        photos.forEach { p ->
            val hint = Region.hintFor(p.location, p.continent)
            assertEquals("${p.id} ${p.country}", p.continent, hint?.name)
            assertTrue("${p.id} outside its hint box", hint!!.contains(p.location))
        }
    }

    @Test fun licensesAreFreeToRedistribute() {
        val allowed = Regex("""^(Public domain|CC0( 1\.0)?|CC BY(-SA)? \d\.\d.*)$""", RegexOption.IGNORE_CASE)
        photos.forEach { assertTrue("${it.id}: ${it.license}", allowed.matches(it.license)) }
    }
}
