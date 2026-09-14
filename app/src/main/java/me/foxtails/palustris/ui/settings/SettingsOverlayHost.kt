package me.foxtails.palustris.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState
import me.foxtails.palustris.ui.notifications.NotificationSettingsViewModel

/**
 * Owns the settings overlay lifetime: route selection, settings models, and settings commands.
 *
 * The root composition only decides whether the overlay is visible and passes the current account
 * context. All writes route through [SettingsViewModel]; the root host never writes preferences.
 */
@Composable
fun SettingsOverlayHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    accounts: List<AccountRef>,
    accountsReady: Boolean,
    postPreferences: PostPreferences,
    activeAccountId: AccountId?,
    sourceRegistry: AccountSourceRegistry,
    sessionGeneration: Long,
) {
    if (!visible) return
    val settingsModel = hiltViewModel<SettingsViewModel>()
    var route by rememberSaveable(stateSaver = SettingsRouteSaver) {
        mutableStateOf<SettingsRoute>(SettingsRoute.Main)
    }
    // An account route is valid only while its exact account exists. Before the account index
    // is restored, an empty list is not proof that the account was removed.
    val routeAccountId = when (val current = route) {
        is SettingsRoute.NotificationAccount -> current.accountId
        is SettingsRoute.Moderation -> current.accountId
        else -> null
    }
    val accountAvailable = routeAccountId == null || accounts.any { it.accountId == routeAccountId }
    LaunchedEffect(route, accounts, accountsReady) {
        if (accountsReady && !accountAvailable) route = route.safeParent()
    }
    val appPreferences by settingsModel.state.collectAsStateWithLifecycle()
    val commandError by settingsModel.commandError.collectAsStateWithLifecycle()
    val repositoryErrorDismissed by settingsModel.repositoryErrorDismissed.collectAsStateWithLifecycle()
    val displayedError = commandError ?: appPreferences.error?.takeUnless { repositoryErrorDismissed }
    val notificationAccountId = (route as? SettingsRoute.NotificationAccount)?.accountId
    val notificationModel = notificationAccountId?.let { accountId ->
        hiltViewModel<NotificationSettingsViewModel, NotificationSettingsViewModel.Factory>(
            key = "settings-notification-settings-$accountId-$sessionGeneration",
            creationCallback = { factory -> factory.create(accountId) },
        )
    }
    val moderationRoute = route as? SettingsRoute.Moderation
    val moderationModel = moderationRoute?.let { target ->
        sourceRegistry.sourceFor(target.accountId)?.let { source ->
            hiltViewModel<ModerationViewModel, ModerationViewModel.Factory>(
                key = "moderation-${target.accountId}-${target.kind}-$sessionGeneration",
                creationCallback = { factory ->
                    factory.create(target.accountId, source, target.kind.toModerationListKind())
                },
            )
        }
    }
    DisposableEffect(moderationModel) {
        onDispose { moderationModel?.stop() }
    }
    val notificationState by if (notificationModel != null) notificationModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(NotificationSettingsUiState()) }
    val moderationState by if (moderationModel != null) moderationModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(ModerationUiState()) }
    val accountLabel = accounts.firstOrNull { it.accountId == activeAccountId }?.handle
        ?: activeAccountId?.localId
        ?: "Current account"
    SettingsHost(
        state = appPreferences,
        accounts = accounts,
        route = route,
        onRoute = { route = it },
        onBack = onDismiss,
        onColorScheme = settingsModel::setColorScheme,
        onColorPalette = settingsModel::setColorPalette,
        onBackground = settingsModel::setBackground,
        onTextSize = settingsModel::setTextSize,
        onFont = settingsModel::setFont,
        onRequest60Hz = settingsModel::setRequest60Hz,
        onLanguage = settingsModel::setLanguage,
        onTrackingCleanup = settingsModel::setTrackingCleanup,
        onContentWarningRules = settingsModel::setContentWarningRules,
        onHiddenContentPresentation = settingsModel::setHiddenContentPresentation,
        postPreferences = postPreferences,
        postPreferencesAccountLabel = accountLabel,
        onPostDefaultAudience = { value -> activeAccountId?.let { settingsModel.setPostDefaultAudience(it, value) } },
        onPostRepliesUnlisted = { value -> activeAccountId?.let { settingsModel.setPostRepliesUnlisted(it, value) } },
        onPostContentWarningRules = { value ->
            activeAccountId?.let { settingsModel.setPostContentWarningRules(it, value) }
        },
        error = displayedError,
        onDismissError = settingsModel::clearError,
        onNotificationAccount = { accountId -> route = SettingsRoute.NotificationAccount(accountId) },
        onModeration = { accountId, kind -> route = SettingsRoute.Moderation(accountId, kind) },
        notificationSettingsState = notificationState,
        onNotificationAlertsEnabled = { enabled -> notificationModel?.setAlertsEnabled(enabled) },
        onNotificationShowPreviews = { enabled -> notificationModel?.setShowPreviews(enabled) },
        onNotificationPeriodicFallback = { enabled -> notificationModel?.setPeriodicFallbackEnabled(enabled) },
        onNotificationQuietHours = { enabled -> notificationModel?.setQuietHours(enabled) },
        onNotificationCategoryChanged = { category, enabled -> notificationModel?.setCategoryEnabled(category, enabled) },
        onNotificationLocalTest = { notificationModel?.runLocalPresentationTest() },
        onNotificationRetryRegistration = { notificationModel?.retryRegistration() },
        onNotificationPermissionChanged = { notificationModel?.refreshPermission() },
        onNotificationRefreshDistributors = { notificationModel?.refreshDistributors() },
        onNotificationSelectDistributor = { packageName -> notificationModel?.selectDistributor(packageName) },
        onNotificationPushConnectionTest = { notificationModel?.runPushConnectionTest() },
        moderationState = moderationState,
        onModerationRetry = { moderationModel?.load() },
        onModerationLoadMore = { moderationModel?.loadMore() },
        onModerationRemove = { moderationModel?.remove(it) },
        onModerationAddLocalHashtag = { moderationModel?.addLocalHashtag(it) },
        onModerationRemoveLocalHashtag = { moderationModel?.removeLocalHashtag(it) },
    )
}

private fun ModerationKind.toModerationListKind() = when (this) {
    ModerationKind.Blocked -> ModerationListKind.Blocked
    ModerationKind.Muted -> ModerationListKind.Muted
    ModerationKind.Hashtags -> ModerationListKind.Hashtags
}
