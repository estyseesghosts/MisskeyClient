package me.foxtails.palustris.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.MediaRequestDecision
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.MediaRequestRole

internal val MediaPageHorizontalPadding = 8.dp

internal fun mediaPageContentBounds(viewport: androidx.compose.ui.geometry.Rect, paddingPx: Float): androidx.compose.ui.geometry.Rect =
    androidx.compose.ui.geometry.Rect(
        left = viewport.left + paddingPx,
        top = viewport.top,
        right = (viewport.right - paddingPx).coerceAtLeast(viewport.left),
        bottom = viewport.bottom,
    )

@Composable
internal fun MediaPage(
    attachment: Attachment,
    index: Int,
    selected: Boolean,
    fullQuality: Boolean = selected,
    revealed: Boolean,
    accountIdentity: String,
    postIdentity: String,
    onReveal: () -> Unit,
    onImageReady: () -> Unit = {},
    onImageDimensionsReady: (Size) -> Unit = {},
    zoomState: ZoomableMediaState = rememberZoomableMediaState(attachment.url),
    modifier: Modifier = Modifier,
    edgeToEdge: Boolean = false,
) {
    if (attachment.sensitive && !revealed) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.media_sensitive), color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onReveal) { Text(stringResource(R.string.media_show)) }
        }
        return
    }
    if (attachment.kind !in setOf(MediaKind.Image, MediaKind.AnimatedImage)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.media_unavailable), color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.media_playback_unavailable, attachment.kind.name), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val context = LocalContext.current
    val mediaLoader = MediaImageLoader.get(context)
    val role = if (fullQuality) MediaRequestRole.Full else MediaRequestRole.Preview
    val decision = MediaRequestPolicy.resolve(attachment, role, revealed = true, explicitlyOpened = fullQuality)
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (decision) {
            is MediaRequestDecision.Request -> ZoomableMediaImage(
                request = MediaImageLoader.get(context).request(
                    context = context,
                    decision = decision,
                    accountIdentity = accountIdentity,
                    postIdentity = postIdentity,
                    attachment = attachment,
                    attachmentIndex = index,
                    decodeWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx().toInt() },
                    decodeHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx().toInt() },
                ),
                 imageLoader = mediaLoader.imageLoader,
                  contentDescription = attachment.description ?: stringResource(R.string.media_page_count, index + 1, 1),
                  state = zoomState,
                  onImageReady = onImageReady,
                  onImageDimensionsReady = onImageDimensionsReady,
                  modifier = Modifier.fillMaxWidth().then(if (edgeToEdge) Modifier else Modifier.padding(horizontal = MediaPageHorizontalPadding)),
            )
            is MediaRequestDecision.NoRequest -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(stringResource(R.string.media_full_size_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
