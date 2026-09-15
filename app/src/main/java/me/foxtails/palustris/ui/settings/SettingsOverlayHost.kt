package me.foxtails.palustris.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.R
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState
import me.foxtails.palustris.ui.notifications.NotificationSettingsViewModel
import me.foxtails.palustris.ui.session.ConnectedEntryStore

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
    entryStore: ConnectedEntryStore,
) {
    if (!visible) return
    val settingsModel = hiltViewModel<SettingsViewModel>()
    var route by rememberSaveable(stateSaver = SettingsRouteSaver) {
        mutableStateOf<SettingsRoute>(SettingsRoute.Main)
    }
    // An account route is valid only while its exact account exists. Before the account index
    // is restored, an empty list is not proof that the account was removed.
    val resolvedRoute = resolveAccountRoute(route, accounts, accountsReady)
    LaunchedEffect(route, accounts, accountsReady) {
        if (resolvedRoute != route) route = resolvedRoute
    }
    val appPreferences by settingsModel.state.collectAsStateWithLifecycle()
    val commandError by settingsModel.commandError.collectAsStateWithLifecycle()
    val canRetryError by settingsModel.canRetry.collectAsStateWithLifecycle()
    val repositoryErrorDismissed by settingsModel.repositoryErrorDismissed.collectAsStateWithLifecycle()
    val displayedError = when (val failure = commandError) {
        null -> appPreferences.error?.takeUnless { repositoryErrorDismissed }
        is SettingsCommandError.SaveFailed ->
            failure.message ?: stringResource(R.string.settings_error_save_failed)
        SettingsCommandError.AccountUnavailable ->
            stringResource(R.string.settings_account_unavailable)
    }
    // The shell publishes the restored account index. Unknown validity allows commands, so
    // account models wait for the validated index instead of constructing eagerly.
    LaunchedEffect(accounts, accountsReady) {
        settingsModel.setValidAccountIds(
            if (accountsReady) accounts.map { it.accountId }.toSet() else null,
        )
    }
    // Account models wait for the validated index. Before restore, an empty account list is
    // not proof that the target was removed.
    val notificationAccountId = notificationAccountFor(route, accounts, accountsReady)
    val notificationModel = notificationAccountId?.let { accountId ->
        hiltViewModel<NotificationSettingsViewModel, NotificationSettingsViewModel.Factory>(
            key = "settings-notification-settings-$accountId-$sessionGeneration",
            creationCallback = { factory -> factory.create(accountId) },
        )
    }
    // Notification settings are owned by the connected entry, not by application settings. The
    // model retires with the connected entry so its account observers are released.
    LaunchedEffect(entryStore, notificationModel, notificationAccountId, sessionGeneration) {
        val model = notificationModel ?: return@LaunchedEffect
        val accountId = notificationAccountId ?: return@LaunchedEffect
        entryStore.register(
            sessionGeneration,
            "settings-notification-settings-$accountId-$sessionGeneration",
        ) { model.stop() }
    }
    val moderationRoute = moderationRouteFor(route, accounts, accountsReady)
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
    // Moderation is bound to the connected entry, not to one composition. A composition can
    // leave and return with the same route. The model retires only with the connected entry.
    LaunchedEffect(entryStore, moderationModel, moderationRoute, sessionGeneration) {
        val model = moderationModel ?: return@LaunchedEffect
        val target = moderationRoute ?: return@LaunchedEffect
        entryStore.register(
            sessionGeneration,
            "moderation-${target.accountId}-${target.kind}-$sessionGeneration",
        ) { model.stop() }
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
        canRetryError = canRetryError,
        onDismissError = settingsModel::clearError,
        onRetryError = settingsModel::retryFailedCommand,
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
