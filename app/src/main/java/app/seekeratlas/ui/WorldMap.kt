package app.seekeratlas.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.seekeratlas.geo.LatLon
import app.seekeratlas.geo.Projection
import app.seekeratlas.geo.Region
import kotlin.math.roundToInt

private val Ocean = Color(0xFF1B3554)
val Mint = Color(0xFF14F195)
val Violet = Color(0xFF9945FF)

/**
 * Pinch-zoomable equirectangular world map. Tap to place a guess; when [answer] is set,
 * draws the answer pin and a dashed line from the guess.
 */
@Composable
fun WorldMap(
    guess: LatLon?,
    answer: LatLon?,
    hint: Region?,
    onTap: (LatLon) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnTap by rememberUpdatedState(onTap)
    val image = remember {
        context.assets.open("world.png").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Unspecified) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun mapWidth() = size.width.toFloat()
    fun mapHeight() = size.width / 2f
    /** Smallest zoom: the map fills the view's height, so there's no empty ocean band. */
    fun minScale() = maxOf(1f, size.height / mapHeight())
    fun local(p: LatLon): Offset = Projection.toPoint(p, mapWidth(), mapHeight()).let { (x, y) -> Offset(x, y) }
    fun clamp(o: Offset, s: Float): Offset {
        val w = mapWidth() * s
        val h = mapHeight() * s
        val x = o.x.coerceIn(size.width - w, 0f)
        val y = if (h <= size.height) (size.height - h) / 2f else o.y.coerceIn(size.height - h, 0f)
        return Offset(x, y)
    }
    fun toScreen(p: LatLon): Offset {
        val (x, y) = Projection.toPoint(p, mapWidth(), mapHeight())
        return offset + Offset(x, y) * scale
    }

    /** Zooms so every point fits with some margin, centred on their bounding box. */
    fun focusOn(points: List<Offset>) {
        if (size == IntSize.Zero) return
        val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
        val fit = minOf(size.width / ((maxX - minX) * 1.6f + 1f), size.height / ((maxY - minY) * 1.6f + 1f))
        val s = fit.coerceIn(minScale(), 6f)
        val center = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
        scale = s
        offset = clamp(Offset(size.width / 2f, size.height / 2f) - center * s, s)
    }

    fun reset() {
        val s = minScale()
        scale = s
        offset = clamp(Offset((size.width - mapWidth() * s) / 2f, (size.height - mapHeight() * s) / 2f), s)
    }

    LaunchedEffect(answer, hint, size) {
        when {
            size == IntSize.Zero -> Unit
            answer != null -> focusOn(listOfNotNull(guess, answer).map(::local))
            hint != null -> focusOn(listOf(local(LatLon(hint.north, hint.west)), local(LatLon(hint.south, hint.east))))
            else -> reset()
        }
    }

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(minScale(), 12f)
                    val focus = (centroid - offset) / scale
                    offset = clamp(centroid - focus * newScale + pan, newScale)
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val local = (tap - offset) / scale
                    if (local.y in 0f..mapHeight() && local.x in 0f..mapWidth()) {
                        currentOnTap(Projection.toLatLon(local.x, local.y, mapWidth(), mapHeight()))
                    }
                }
            }
    ) {
        if (offset == Offset.Unspecified) return@Canvas
        drawRect(Ocean)
        drawImage(
            image,
            dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
            dstSize = IntSize((mapWidth() * scale).roundToInt(), (mapHeight() * scale).roundToInt()),
        )
        hint?.let { r ->
            val tl = toScreen(LatLon(r.north, r.west))
            val br = toScreen(LatLon(r.south, r.east))
            drawRect(Mint.copy(alpha = 0.18f), topLeft = tl, size = androidx.compose.ui.geometry.Size(br.x - tl.x, br.y - tl.y))
            drawRect(Mint, topLeft = tl, size = androidx.compose.ui.geometry.Size(br.x - tl.x, br.y - tl.y), style = Stroke(3f))
        }
        val g = guess?.let(::toScreen)
        val a = answer?.let(::toScreen)
        if (g != null && a != null) {
            drawLine(Color.White, g, a, strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
        }
        g?.let {
            drawCircle(Violet, radius = 16f, center = it)
            drawCircle(Color.White, radius = 16f, center = it, style = Stroke(4f))
        }
        a?.let {
            drawCircle(Mint, radius = 16f, center = it)
            drawCircle(Color.White, radius = 16f, center = it, style = Stroke(4f))
        }
    }
}
