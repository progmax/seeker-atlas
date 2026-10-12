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
    fun baseOffset() = Offset(0f, (size.height - mapHeight()) / 2f)
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

    // When the answer appears, zoom out so both pins are visible.
    LaunchedEffect(answer) {
        if (answer != null && size != IntSize.Zero) {
            scale = 1f
            offset = baseOffset()
        }
    }

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged {
                size = it
                scale = 1f
                offset = baseOffset()
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 10f)
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
