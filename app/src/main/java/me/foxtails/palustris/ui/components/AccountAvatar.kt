package me.foxtails.palustris.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.foxtails.palustris.R
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.ui.Avatar

@Composable
fun AccountAvatar(account: Account, modifier: Modifier = Modifier, exposeSemantics: Boolean = true) {
    val context = LocalContext.current
    val mediaImageLoader = remember(context) { MediaImageLoader.get(context) }
    val avatarRequest = remember(context, account.avatarUrl) {
        ImageRequest.Builder(context)
            .data(account.avatarUrl)
            .crossfade(false)
            .build()
    }
    val avatarDescription = stringResource(R.string.post_profile_picture, account.displayName)
    Box(
        modifier
            .clip(CircleShape)
            .then(if (exposeSemantics) Modifier.semantics(mergeDescendants = true) {
                contentDescription = avatarDescription
            } else Modifier),
    ) {
        Avatar(Modifier.fillMaxSize(), description = null)
        AsyncImage(
            model = avatarRequest,
            imageLoader = mediaImageLoader.imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
