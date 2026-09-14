package me.foxtails.palustris.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.AppPreferencesRepository
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState
import me.foxtails.palustris.ui.notifications.NotificationSettingsViewModel

/**
 * Owns the settings overlay lifetime: route selection, settings models, and settings commands.
 *
 * The root composition only decides whether the overlay is visible and passes the current account
 * context. Settings writes stay here so no root host writes application preferences.
 */
@Composable
fun SettingsOverlayHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    appPreferences: AppPreferencesState,
    accounts: List<AccountRef>,
    appPreferencesRepository: AppPreferencesRepository,
    postPreferencesRepository: PostPreferencesRepository,
    postPreferences: PostPreferences,
    activeAccountId: AccountId?,
    sourceRegistry: AccountSourceRegistry,
    sessionGeneration: Long,
) {
    if (!visible) return
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Main) }
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
        onColorScheme = { value -> settingsViewModelUpdate(scope, appPreferencesRepository) { it.copy(colorScheme = value) } },
        onColorPalette = { value -> settingsViewModelUpdate(scope, appPreferencesRepository) { it.copy(colorScheme = AppColorScheme.Palette, colorPalette = value) } },
        onBackground = { value -> settingsViewModelUpdate(scope, appPreferencesRepository) { it.copy(background = value) } },
        onTextSize = { value -> settingsViewModelUpdate(scope, appPreferencesRepository) { it.copy(textSize = value) } },
        onFont = { value -> settingsViewModelUpdate(scope, appPreferencesRepository) { it.copy(font = value) } },
        onRequest60Hz = { value -> settingsViewModelUpdate(scope, appPreferencesRepository) { it.copy(request60Hz = value) } },
        onLanguage = { value -> settingsViewModelUpdate(scope, appPreferencesRepository) { it.copy(language = value) } },
        onTrackingCleanup = { value -> settingsViewModelUpdate(scope, appPreferencesRepository) { it.copy(cleanTrackingParameters = value) } },
        onContentWarningRules = { value -> settingsViewModelUpdate(scope, appPreferencesRepository) { it.copy(contentWarningRules = value) } },
        onHiddenContentPresentation = { value -> settingsViewModelUpdate(scope, appPreferencesRepository) { it.copy(hiddenContentPresentation = value) } },
        postPreferences = postPreferences,
        postPreferencesAccountLabel = accountLabel,
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
        onPostPreferences = { value ->
            scope.launch {
                runCatching {
                    activeAccountId?.let { postPreferencesRepository.update(it) { value } }
                }
            }
        },
    )
}

private fun settingsViewModelUpdate(
    scope: CoroutineScope,
    repository: AppPreferencesRepository,
    transform: (AppPreferences) -> AppPreferences,
) {
    scope.launch { runCatching { repository.update(transform) } }
}

private fun ModerationKind.toModerationListKind() = when (this) {
    ModerationKind.Blocked -> ModerationListKind.Blocked
    ModerationKind.Muted -> ModerationListKind.Muted
    ModerationKind.Hashtags -> ModerationListKind.Hashtags
}
