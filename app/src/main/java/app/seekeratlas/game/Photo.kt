package app.seekeratlas.game

import android.content.Context
import app.seekeratlas.geo.LatLon
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Photo(
    val id: String,
    val file: String,
    val lat: Double,
    val lon: Double,
    val country: String,
    val place: String,
    val author: String,
    val license: String,
    val source: String,
    val wallet: String? = null,
) {
    val location: LatLon get() = LatLon(lat, lon)
}

object Photos {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<Photo> =
        context.assets.open("photos.json").bufferedReader().use { json.decodeFromString(it.readText()) }
}
