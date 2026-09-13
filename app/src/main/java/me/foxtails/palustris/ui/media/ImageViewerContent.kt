package me.foxtails.palustris.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.ui.AppIcons

data class ImageViewerContent(
    val url: String,
    val identity: String,
    val description: String? = null,
)

/** Image-only viewer content reuses MediaPage without inventing an OwnedPost. */
@Composable
fun ImageViewerContentScreen(content: ImageViewerContent, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MediaPage(
            attachment = Attachment(
                url = content.url,
                previewUrl = content.url,
                description = content.description,
                mimeType = "image/*",
                kind = MediaKind.Image,
            ),
            index = 0,
            selected = true,
            fullQuality = true,
            revealed = true,
            accountIdentity = content.identity,
            postIdentity = content.identity,
            onReveal = {},
            modifier = Modifier.fillMaxSize(),
            edgeToEdge = true,
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart),
        ) { Icon(AppIcons.Close, stringResource(R.string.media_close_description)) }
    }
}
