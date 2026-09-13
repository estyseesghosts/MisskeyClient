package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.foxtails.palustris.R
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.motion.PopEffect

@Composable
internal fun ProfileHeader(
    account: Account,
    state: ProfileUiState,
    isSelf: Boolean,
    onRefresh: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onMessage: () -> Unit = {},
    onOpenProfile: (Account) -> Unit = {},
    onOpenProfileImage: (String) -> Unit = {},
    onEditProfile: (() -> Unit)? = null,
    largeSummary: Boolean = false,
) {
    val context = LocalContext.current
    val mediaImageLoader = remember(context) { MediaImageLoader.get(context) }
    val statusBarHeight = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }.coerceAtLeast(1.dp)
    Column(Modifier.fillMaxWidth().testTag("profile_header")) {
        val movedTo = account.movedTo?.takeIf { it.hasUsableProfileIdentity() }
        movedTo?.let { destination ->
            ProfileRedirectBanner(
                account = account,
                destination = destination,
                onOpenProfile = { onOpenProfile(destination) },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .testTag("profile_banner"),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(152.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                AsyncImage(
                    model = account.bannerUrl,
                    imageLoader = mediaImageLoader.imageLoader,
                    contentDescription = stringResource(R.string.profile_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(152.dp),
                )
            }
            if (statusBarHeight > 0.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusBarHeight)
                        .clip(RectangleShape)
                        .testTag("profile_banner_status_bar_blur"),
                ) {
                    AsyncImage(
                        model = account.bannerUrl,
                        imageLoader = mediaImageLoader.imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(statusBarHeight).blur(24.dp),
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .then(
                        if (largeSummary) {
                            Modifier.align(Alignment.BottomCenter).size(96.dp).testTag("profile_large_avatar")
                        } else {
                            Modifier.padding(start = 16.dp, top = 104.dp).size(112.dp)
                        },
                    )
                    .clip(CircleShape)
                    .then(account.avatarUrl?.let { Modifier.clickable { onOpenProfileImage(it) } } ?: Modifier),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                PopEffect(account.id) { AccountAvatar(account, Modifier.padding(4.dp)) }
            }
            account.bannerUrl?.let { bannerUrl ->
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable { onOpenProfileImage(bannerUrl) },
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    InlineEmojiText(
                        text = account.displayName.ifBlank { account.handle },
                        emoji = account.emoji,
                        style = if (largeSummary) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = account.handle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = if (largeSummary) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isSelf && !largeSummary && onEditProfile != null && state.editableSupported) {
                    OutlinedButton(onClick = onEditProfile, modifier = Modifier.testTag("profile_edit_action")) {
                        Text(stringResource(R.string.profile_edit))
                    }
                } else if (!isSelf && movedTo == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onMessage, modifier = Modifier.testTag("profile_message_action")) {
                            Text(stringResource(R.string.profile_message))
                        }
                        if (state.relationshipSupported == true && state.relationship != null) {
                            val relationship = state.relationship
                            val following = relationship.following || relationship.requested
                            Button(
                                onClick = if (following) onUnfollow else onFollow,
                                enabled = !state.relationshipMutation,
                                modifier = Modifier.testTag("profile_follow_action"),
                            ) {
                                if (state.relationshipMutation) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        when {
                                            relationship.following -> "Following"
                                            relationship.requested -> "Requested"
                                            else -> "Follow"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (account.locked || account.bot) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (account.locked) ProfileBadge("Locked")
                    if (account.bot) ProfileBadge("Bot")
                }
            }
            InlineEmojiText(
                text = account.biography.ifBlank { "No biography yet." },
                emoji = account.emoji,
                modifier = Modifier.padding(top = 16.dp).testTag("profile_biography"),
                style = if (largeSummary) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            )
            ProfileStats(account)
            when {
                state.detailLoading -> LinearProfileProgress("Loading profile details")
                state.staleDetails && state.detailError != null -> ProfileStatus("Showing saved profile details. ${state.detailError}", "Retry", onRefresh)
                state.detailError != null -> ProfileStatus(state.detailError, "Retry", onRefresh)
            }
            if (!isSelf && state.relationshipError != null) {
                ProfileStatus(state.relationshipError, "Retry", onRefresh)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
