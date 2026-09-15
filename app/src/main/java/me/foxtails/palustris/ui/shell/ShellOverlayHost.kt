package me.foxtails.palustris.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.AppDialogs
import me.foxtails.palustris.ui.AppSelectionSheet
import me.foxtails.palustris.ui.Destination
import me.foxtails.palustris.ui.Overlay
import me.foxtails.palustris.ui.PostActionBubbleHost
import me.foxtails.palustris.ui.composer.ComposerOverlayHost
import me.foxtails.palustris.ui.composer.ComposerOwner
import me.foxtails.palustris.ui.emoji.EmojiPickerHost
import me.foxtails.palustris.ui.emoji.EmojiPickerTarget
import me.foxtails.palustris.ui.layout.CompactFilterDockHeight
import me.foxtails.palustris.ui.layout.CompactSearchDockHeight
import me.foxtails.palustris.ui.layout.compactHomeScrollEndClearance
import me.foxtails.palustris.ui.layout.compactScrollEndClearance
import me.foxtails.palustris.ui.media.ImageViewerContentScreen
import me.foxtails.palustris.ui.media.MediaViewerScreen
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.notifications.NotificationSettingsSheet
import me.foxtails.palustris.ui.posts.PostPopupPresentation
import me.foxtails.palustris.ui.posts.PostShareSheet
import me.foxtails.palustris.ui.posts.copyPostShareContent
import me.foxtails.palustris.ui.profile.EditProfileSheet
import me.foxtails.palustris.ui.sharePost

/**
 * Bubble and share-sheet hosting for the application shell.
 *
 * The host renders the hashtag bottom clearance, the post-action bubble,
 * and the share sheet. It owns no state. Holders, contracts, and callbacks
 * arrive as parameters. It stays separate from [ShellOverlayHost] to
 * preserve the z-order around the compact single-post surface.
 */
@Composable
internal fun ShellBubbleHost(
    navigator: ShellNavigator,
    overlay: ShellOverlayPresenter,
    postActionOwner: PostPopupPresentation?,
    emojiPresentation: EmojiPresentation,
    largePresentation: Boolean,
    account: Account?,
    sessionRevision: Long,
    directMessages: DirectMessagesContract,
) {
    val context = LocalContext.current
    val hashtagBottomClearance = if (largePresentation || navigator.page != null || navigator.notificationRoute != null) {
        0.dp
    } else {
        when (navigator.destination) {
            Destination.Home -> compactHomeScrollEndClearance()
            Destination.Search -> compactScrollEndClearance(
                controlStackHeight = CompactSearchDockHeight,
                navigationVisible = navigator.navigationVisible,
                ime = WindowInsets.ime,
            )
            Destination.Notifications, Destination.Profile -> compactScrollEndClearance(
                controlStackHeight = CompactFilterDockHeight,
                navigationVisible = navigator.navigationVisible,
            )
        }
    }
    PostActionBubbleHost(
        target = overlay.postActionBubbleTarget,
        emojiCatalog = emojiPresentation.catalog,
        emojiCapabilities = emojiPresentation.capabilities,
        onLoadEmojiCatalog = emojiPresentation.actions::loadCatalog,
        onRetryEmojiCatalog = emojiPresentation.actions::retryCatalog,
        onToggleEmojiGroupCollapsed = emojiPresentation.actions::toggleGroupCollapsed,
        onToggleEmojiGroupPinned = emojiPresentation.actions::toggleGroupPinned,
        onTogglePinnedEmoji = emojiPresentation.actions::togglePinnedEmoji,
        onDismiss = overlay::clearPostActionBubble,
        onHashtagSelected = { hashtag ->
            overlay.clearPostActionBubble()
            navigator.openHashtagSearch(hashtag)
        },
        onReactionSelected = { target, choice ->
            val owner = account
            val handler = overlay.postReactionHandler
            if (owner != null && target.fetchedBy == owner.id && target.sessionRevision == sessionRevision &&
                emojiPresentation.capabilities.reactionMutation == CapabilityStatus.Supported
            ) {
                handler?.invoke(target, choice)
            }
            overlay.clearPostActionBubble()
        },
        onReactionModeChanged = { expanded -> overlay.postActionBubbleTarget = expanded },
        hashtagBottomClearance = hashtagBottomClearance,
    )
    postActionOwner?.target?.let { target ->
        PostShareSheet(
            target = target,
            relationship = postActionOwner.relationship,
            report = postActionOwner.report,
            onDismiss = postActionOwner::dismiss,
            onRelationshipAction = postActionOwner::mutate,
            onSubmitReport = postActionOwner::submitReport,
            onOpenDirectMessage = {
                val recipient = target.author
                postActionOwner.dismiss()
                directMessages.actions.startConversation(recipient)
            },
            onCopyLink = { copyPostShareContent(context, target.post) },
            onShare = {
                sharePost(context, target.post)
                postActionOwner.dismiss()
            },
        )
    }
}

/**
 * Viewer, sheet, overlay, and dialog hosting for the application shell.
 *
 * The host renders media and image viewers, the account selection sheet,
 * the composer and edit-profile overlays, the emoji picker, the
 * notification settings sheet with its back handler, and the dialogs. It
 * owns no state. Holders, contracts, and shell-local closes arrive as
 * parameters.
 */
@Composable
internal fun ShellOverlayHost(
    navigator: ShellNavigator,
    overlay: ShellOverlayPresenter,
    emojiPresentation: EmojiPresentation,
    account: Account?,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    composerOwner: ComposerOwner,
    composer: ComposerContract,
    profile: ProfileContract,
    notificationSettings: NotificationSettingsContract,
    accountSwitcher: AccountSwitcher,
    onCloseComposer: () -> Unit,
    onCloseProfile: () -> Unit,
    onCloseNotificationSettings: () -> Unit,
    onDiscardProfileEditor: () -> Unit,
) {
    overlay.mediaRequest?.let { request ->
        MediaViewerScreen(
            request = request,
            onClose = { overlay.mediaRequest = null },
            onReact = onReact,
            onReply = onReply,
            onReshare = onReshare,
        )
    }

    overlay.profileImageRequest?.let { request ->
        ImageViewerContentScreen(request, onClose = { overlay.profileImageRequest = null })
    }

    if (navigator.sheet != null) AppSelectionSheet(
        account = account,
        accounts = accountSwitcher.accounts,
        onDismiss = { navigator.sheet = null },
        onSwitchAccount = accountSwitcher.actions::switchTo,
        onAddAccount = accountSwitcher.actions::addAccount,
        onOpenSettings = accountSwitcher.actions::openSettings,
        onSignOut = { overlay.signOutDialog = true },
    )

    if (navigator.overlay == Overlay.Composer) ComposerOverlayHost(
        owner = composerOwner,
        contract = composer,
        account = account,
        onDismiss = onCloseComposer,
        onClose = { navigator.closeOverlay() },
        onRequestEmoji = { field -> overlay.emojiPickerTarget = EmojiPickerTarget.Composer(field) },
        pendingEmojiInsertion = overlay.pendingEmojiInsertion,
        onEmojiInsertionApplied = { overlay.pendingEmojiInsertion = null },
    )

    if (navigator.overlay == Overlay.EditProfile && account != null) EditProfileSheet(
        account = account,
        editor = profile.state.editorDraft,
        editorBase = profile.state.editorBase,
        capabilities = profile.state.editorCapabilities,
        emoji = profile.state.account?.emoji ?: emptyMap(),
        loading = profile.state.editableLoading,
        saving = profile.state.savingProfile,
        error = profile.state.editError ?: profile.state.editableError,
        onEditorChange = profile.actions::updateEditor,
        onSave = { patch ->
            profile.actions.saveEditor(patch) {
                navigator.closeOverlay()
            }
        },
        onClose = onCloseProfile,
    )

    if (overlay.emojiPickerTarget != null) {
        EmojiPickerHost(
            target = overlay.emojiPickerTarget,
            catalog = emojiPresentation.catalog,
            selectionMode = emojiPresentation.capabilities.selectionMode,
            mutationSupported = emojiPresentation.capabilities.reactionMutation == CapabilityStatus.Supported,
            onLoadCatalog = emojiPresentation.actions::loadCatalog,
            onRetryCatalog = emojiPresentation.actions::retryCatalog,
            onToggleGroupCollapsed = emojiPresentation.actions::toggleGroupCollapsed,
            onToggleGroupPinned = emojiPresentation.actions::toggleGroupPinned,
            onTogglePinnedEmoji = emojiPresentation.actions::togglePinnedEmoji,
            onDismiss = {
                overlay.emojiPickerTarget = null
            },
            onEmojiSelected = { choice ->
                val target = overlay.emojiPickerTarget
                when (target) {
                    is EmojiPickerTarget.Reaction -> Unit
                    is EmojiPickerTarget.Composer -> overlay.pendingEmojiInsertion = choice to target.field
                    null -> Unit
                }
                overlay.emojiPickerTarget = null
            },
        )
    }

    if (navigator.overlay == Overlay.NotificationSettings && account != null) NotificationSettingsSheet(
        state = notificationSettings.state,
        onDismiss = onCloseNotificationSettings,
        onAlertsEnabled = notificationSettings.actions::setAlertsEnabled,
        onShowPreviews = notificationSettings.actions::setShowPreviews,
        onPeriodicFallback = notificationSettings.actions::setPeriodicFallback,
        onQuietHours = notificationSettings.actions::setQuietHours,
        onCategoryChanged = notificationSettings.actions::setCategoryEnabled,
        onRunLocalTest = notificationSettings.actions::runLocalTest,
        onRetryRegistration = notificationSettings.actions::retryRegistration,
        onPermissionChanged = notificationSettings.actions::refreshPermission,
        onRetryStorage = notificationSettings.actions::retryStorage,
        onResetStorage = notificationSettings.actions::resetStorage,
        onRefreshDistributors = notificationSettings.actions::refreshDistributors,
        onSelectDistributor = notificationSettings.actions::selectDistributor,
        onRunPushConnectionTest = notificationSettings.actions::runPushConnectionTest,
    )

    BackHandler(enabled = navigator.overlay == Overlay.NotificationSettings) {
        onCloseNotificationSettings()
    }

    AppDialogs(
        profileDialog = overlay.profileDialog,
        onProfileDialogDismiss = { overlay.profileDialog = false },
        onDiscardProfile = onDiscardProfileEditor,
        signOutDialog = overlay.signOutDialog,
        onSignOutDialogDismiss = { overlay.signOutDialog = false },
        onSignOut = accountSwitcher.actions::signOut,
    )
}
