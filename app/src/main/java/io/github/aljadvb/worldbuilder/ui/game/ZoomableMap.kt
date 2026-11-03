package io.github.aljadvb.worldbuilder.ui.game

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aljadvb.worldbuilder.R

/**
 * Pinch to zoom and drag to move the map. [content] gets the modifier with the transform; taps
 * placed after it in the modifier chain receive positions in map (untransformed) coordinates.
 */
@Composable
fun ZoomableMap(modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                // Keep the point under the fingers in place while zooming, then follow the pan.
                val anchor = (centroid - offset) / scale
                offset = centroid - anchor * newScale + pan
                scale = newScale
            }
        },
    ) {
        content(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
                transformOrigin = TransformOrigin(0f, 0f)
            },
        )
        if (scale != 1f || offset != Offset.Zero) {
            FilledTonalButton(onClick = { scale = 1f; offset = Offset.Zero }, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                Text(stringResource(R.string.map_fit))
            }
        }
    }
}

private const val MIN_SCALE = 0.6f
private const val MAX_SCALE = 5f
