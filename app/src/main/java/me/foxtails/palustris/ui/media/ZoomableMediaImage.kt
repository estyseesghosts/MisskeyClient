package me.foxtails.palustris.ui.media

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
internal fun ZoomableMediaImage(
    request: ImageRequest,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var scale by rememberSaveable(request.data) { mutableFloatStateOf(1f) }
    var offset by remember(request.data) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = nextScale
        if (nextScale > 1f) {
            offset += panChange
        } else {
            offset = Offset.Zero
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .transformable(transformState, canPan = { scale > 1f })
            .pointerInput(request.data) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2f
                        if (scale == 1f) offset = Offset.Zero
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x.coerceIn(-size.width * (scale - 1f) / 2f, size.width * (scale - 1f) / 2f)
                translationY = offset.y.coerceIn(-size.height * (scale - 1f) / 2f, size.height * (scale - 1f) / 2f)
            }
            .semantics { this.contentDescription = contentDescription },
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
