package me.foxtails.palustris.ui.media

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Image
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

@androidx.compose.runtime.Stable
class ZoomableMediaState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var transforming by mutableStateOf(false)
        private set

    val isZoomed: Boolean
        get() = scale > 1.01f

    fun applyTransform(zoomChange: Float, panChange: Offset) {
        transforming = true
        val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = nextScale
        offset = if (nextScale > 1f) offset + panChange else Offset.Zero
    }

    fun setDoubleTapZoom() {
        scale = if (isZoomed) 1f else 2f
        if (scale == 1f) offset = Offset.Zero
        transforming = false
    }
}

@Composable
fun rememberZoomableMediaState(key: Any?): ZoomableMediaState = remember(key) { ZoomableMediaState() }

@Composable
internal fun ZoomableMediaImage(
    request: ImageRequest,
    imageLoader: ImageLoader,
    contentDescription: String,
    modifier: Modifier = Modifier,
    state: ZoomableMediaState = rememberZoomableMediaState(request.data),
    onImageReady: () -> Unit = {},
) {
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        state.applyTransform(zoomChange, panChange)
    }
    val painter = rememberAsyncImagePainter(model = request, imageLoader = imageLoader)
    LaunchedEffect(painter.state) {
        if (painter.state is AsyncImagePainter.State.Success) onImageReady()
    }
    Box(
        modifier
            .fillMaxSize()
            .transformable(transformState, canPan = { state.isZoomed })
            .pointerInput(request.data) {
                detectTapGestures(
                    onDoubleTap = {
                        state.setDoubleTapZoom()
                    },
                )
            }
            .graphicsLayer {
                scaleX = state.scale
                scaleY = state.scale
                translationX = state.offset.x.coerceIn(-size.width * (state.scale - 1f) / 2f, size.width * (state.scale - 1f) / 2f)
                translationY = state.offset.y.coerceIn(-size.height * (state.scale - 1f) / 2f, size.height * (state.scale - 1f) / 2f)
            }
            .semantics { this.contentDescription = contentDescription },
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
