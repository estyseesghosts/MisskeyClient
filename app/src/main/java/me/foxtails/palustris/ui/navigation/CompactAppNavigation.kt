@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.components.BeelineBubbleShape
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.Avatar
import me.foxtails.palustris.ui.Destination
import me.foxtails.palustris.ui.NotificationsPanel
import me.foxtails.palustris.ui.SearchPanel
import me.foxtails.palustris.ui.layout.CompactNavigationHeight
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.motion.rememberSelectedScale
import me.foxtails.palustris.ui.motion.springPress
import me.foxtails.palustris.ui.profile.ProfileUiState

internal data class ContextualBottomAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

internal fun Modifier.bubblePressLayer(
    pressed: Boolean,
    color: androidx.compose.ui.graphics.Color,
    shape: androidx.compose.ui.graphics.Shape,
): Modifier = clip(shape).drawWithContent {
    drawContent()
    if (pressed) {
        drawRect(color)
    }
}

private fun Modifier.roundPressLayer(
    pressed: Boolean,
    color: androidx.compose.ui.graphics.Color,
): Modifier = bubblePressLayer(pressed, color, androidx.compose.foundation.shape.CircleShape)

@Composable
internal fun contextualActionFor(
    destination: Destination,
    searchPanel: SearchPanel,
    notificationsPanel: NotificationsPanel,
    profileTarget: Account?,
    authenticatedAccountId: AccountId?,
    profileState: ProfileUiState,
    onCompose: () -> Unit,
    onSearchToggle: () -> Unit,
    onNotificationsToggle: () -> Unit,
    onEditProfile: () -> Unit,
    onFollowProfile: () -> Unit,
    onUnfollowProfile: () -> Unit,
): ContextualBottomAction? = when (destination) {
    Destination.Home -> ContextualBottomAction(AppIcons.Compose, stringResource(R.string.nav_compose), true, onCompose)
    Destination.Search -> if (searchPanel == SearchPanel.Search) {
        ContextualBottomAction(AppIcons.PhotoGrid, stringResource(R.string.nav_photo_grid), true, onSearchToggle)
    } else {
        ContextualBottomAction(AppIcons.SearchBeeline, stringResource(R.string.nav_search), true, onSearchToggle)
    }
    Destination.Notifications -> if (notificationsPanel == NotificationsPanel.Notifications) {
        ContextualBottomAction(AppIcons.DirectMessage, stringResource(R.string.nav_direct_messages), true, onNotificationsToggle)
    } else {
        ContextualBottomAction(AppIcons.Mail, stringResource(R.string.nav_notifications), true, onNotificationsToggle)
    }
    Destination.Profile -> when {
        profileTarget?.movedTo != null -> null
        profileTarget?.id == authenticatedAccountId && authenticatedAccountId != null ->
            ContextualBottomAction(AppIcons.PersonEdit, stringResource(R.string.profile_edit), profileState.editableSupported, onEditProfile)
        profileState.relationshipSupported == true && profileState.relationship != null -> {
            val relationship = profileState.relationship
            val following = relationship.following || relationship.requested
            ContextualBottomAction(
                icon = if (following) AppIcons.Unfollow else AppIcons.Follow,
                contentDescription = when {
                    relationship.following -> stringResource(R.string.profile_unfollow_action)
                    relationship.requested -> stringResource(R.string.profile_cancel_request_action)
                    else -> stringResource(R.string.profile_follow_action)
                },
                enabled = !profileState.relationshipMutation,
                onClick = if (following) onUnfollowProfile else onFollowProfile,
            )
        }
        else -> null
    }
}

@Composable
internal fun CompactContextualNavigationBar(
    destination: Destination,
    searchPanel: SearchPanel,
    action: ContextualBottomAction?,
    account: Account?,
    onOpenAccounts: () -> Unit,
    onDestinationSelected: (Destination) -> Unit,
) {
    val scheme = LocalPalustrisMotionScheme.current
    Row(
        Modifier.fillMaxWidth().height(CompactNavigationHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Surface(
            modifier = Modifier.width(212.dp).height(56.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    Destination.entries.forEach { item ->
                        val selected = destination == item
                        val photoGridSelected = destination == Destination.Search &&
                            item == Destination.Search && searchPanel == SearchPanel.PhotoGrid
                        val label = stringResource(if (photoGridSelected) R.string.nav_photo_grid else item.labelRes)
                        val icon = if (photoGridSelected) AppIcons.PhotoGrid else item.icon
                        val interactionSource = remember(item) { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val selectedTint = rememberSelectedColor(
                            selected,
                            MaterialTheme.colorScheme.onSecondaryContainer,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val selectedScale = rememberSelectedScale(selected)
                        val itemModifier = if (item == Destination.Profile) {
                            Modifier
                                .size(48.dp)
                                .springPress(interactionSource, pressedScale = scheme.compactPressedScale)
                                .roundPressLayer(pressed, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = LocalIndication.current,
                                    onClick = { onDestinationSelected(item) },
                                    onLongClick = onOpenAccounts,
                                )
                        } else Modifier
                            .size(48.dp)
                            .springPress(interactionSource, pressedScale = scheme.compactPressedScale)
                            .roundPressLayer(pressed, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                            ) { onDestinationSelected(item) }
                        Box(
                            modifier = itemModifier.semantics {
                                contentDescription = label
                                this.selected = selected
                                role = Role.Tab
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = selected,
                                enter = if (scheme.reducedMotion) EnterTransition.None else
                                    scaleIn(initialScale = 0.86f, animationSpec = scheme.expressive) + fadeIn(scheme.fastFadeIn),
                                exit = if (scheme.reducedMotion) ExitTransition.None else
                                    scaleOut(targetScale = 0.86f, animationSpec = scheme.expressive) + fadeOut(scheme.fastFadeOut),
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp).testTag("selected_navigation_indicator"),
                                    shape = BeelineBubbleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {}
                            }
                            if (item == Destination.Profile) {
                                val avatarModifier = Modifier.size(30.dp).graphicsLayer {
                                    scaleX = selectedScale
                                    scaleY = selectedScale
                                }
                                if (account != null) AccountAvatar(account, avatarModifier, exposeSemantics = false)
                                else Avatar(avatarModifier, description = null)
                            } else Icon(
                                icon,
                                null,
                                Modifier.graphicsLayer {
                                    scaleX = selectedScale
                                    scaleY = selectedScale
                                },
                                tint = selectedTint,
                            )
                        }
                }
            }
        }
        action?.let { contextualAction ->
            FilledIconButton(
                onClick = contextualAction.onClick,
                enabled = contextualAction.enabled,
                 modifier = Modifier.size(56.dp).semantics { contentDescription = contextualAction.contentDescription },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                AnimatedContent(
                    targetState = contextualAction,
                    contentKey = { it.contentDescription },
                    transitionSpec = {
                        if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                        else (fadeIn(scheme.fastFadeIn) + scaleIn(initialScale = 0.86f, animationSpec = scheme.expressive)) togetherWith
                            (fadeOut(scheme.fastFadeOut) + scaleOut(targetScale = 0.86f, animationSpec = scheme.expressive))
                    },
                    modifier = Modifier.size(24.dp),
                    label = "contextualAction",
                ) { actionState -> Icon(actionState.icon, null) }
            }
        }
    }
}
