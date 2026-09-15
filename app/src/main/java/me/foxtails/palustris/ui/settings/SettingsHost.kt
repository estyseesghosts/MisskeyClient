package me.foxtails.palustris.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.notifications.NotificationSettingsScreen
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHost(
    state: AppPreferencesState,
    accounts: List<AccountRef> = emptyList(),
    route: SettingsRoute = SettingsRoute.Main,
    onRoute: (SettingsRoute) -> Unit,
    onBack: () -> Unit,
    onColorScheme: (me.foxtails.palustris.domain.AppColorScheme) -> Unit = {},
    onColorPalette: (me.foxtails.palustris.domain.AppColorPalette) -> Unit = {},
    onBackground: (me.foxtails.palustris.domain.AppBackground) -> Unit = {},
    onTextSize: (me.foxtails.palustris.domain.AppTextSize) -> Unit = {},
    onFont: (me.foxtails.palustris.domain.AppFont) -> Unit = {},
    onRequest60Hz: (Boolean) -> Unit = {},
    onLanguage: (me.foxtails.palustris.domain.AppLanguage) -> Unit = {},
    onTrackingCleanup: (Boolean) -> Unit = {},
    onContentWarningRules: (me.foxtails.palustris.domain.ContentWarningRules) -> Unit = {},
    onHiddenContentPresentation: (me.foxtails.palustris.domain.HiddenContentPresentation) -> Unit = {},
    postPreferences: PostPreferences = PostPreferences(),
    postPreferencesAccountLabel: String = "Current account",
    onPostDefaultAudience: (Audience) -> Unit = {},
    onPostRepliesUnlisted: (Boolean) -> Unit = {},
    onPostContentWarningRules: (ContentWarningRules) -> Unit = {},
    error: String? = null,
    canRetryError: Boolean = false,
    onDismissError: () -> Unit = {},
    onRetryError: () -> Unit = {},
    onNotificationAccount: (AccountId) -> Unit = {},
    onModeration: (AccountId, ModerationKind) -> Unit = { _, _ -> },
    notificationSettingsState: NotificationSettingsUiState = NotificationSettingsUiState(),
    onNotificationAlertsEnabled: (Boolean) -> Unit = {},
    onNotificationShowPreviews: (Boolean) -> Unit = {},
    onNotificationPeriodicFallback: (Boolean) -> Unit = {},
    onNotificationQuietHours: (Boolean) -> Unit = {},
    onNotificationCategoryChanged: (me.foxtails.palustris.domain.NotificationCategory, Boolean) -> Unit = { _, _ -> },
    onNotificationLocalTest: () -> Unit = {},
    onNotificationRetryRegistration: () -> Unit = {},
    onNotificationRetryStorage: () -> Unit = {},
    onNotificationPermissionChanged: () -> Unit = {},
    onNotificationRefreshDistributors: () -> Unit = {},
    onNotificationSelectDistributor: (String) -> Unit = {},
    onNotificationPushConnectionTest: () -> Unit = {},
    moderationState: ModerationUiState = ModerationUiState(),
    onModerationRetry: () -> Unit = {},
    onModerationLoadMore: () -> Unit = {},
    onModerationRemove: (me.foxtails.palustris.domain.ModerationAccount) -> Unit = {},
    onModerationAddLocalHashtag: (String) -> Unit = {},
    onModerationRemoveLocalHashtag: (String) -> Unit = {},
) {
    val navigateBack: () -> Unit = if (route == SettingsRoute.Main) onBack else { { onRoute(SettingsRoute.Main) } }
    BackHandler(onBack = navigateBack)
    val title = when (route) {
        SettingsRoute.Main -> stringResource(R.string.settings_title)
        SettingsRoute.Display -> stringResource(R.string.settings_display)
        SettingsRoute.Notifications -> stringResource(R.string.settings_notifications)
        is SettingsRoute.NotificationAccount -> stringResource(R.string.settings_notifications)
        SettingsRoute.Privacy -> stringResource(R.string.settings_privacy)
        SettingsRoute.Language -> stringResource(R.string.settings_language)
        SettingsRoute.ContentWarnings -> stringResource(R.string.settings_content_warnings)
        SettingsRoute.Posting -> stringResource(R.string.settings_posting)
        SettingsRoute.PrivacyAccounts -> stringResource(R.string.settings_choose_account)
        is SettingsRoute.Moderation -> when (route.kind) {
            ModerationKind.Blocked -> stringResource(R.string.settings_blocked_users)
            ModerationKind.Muted -> stringResource(R.string.settings_muted_users)
            ModerationKind.Hashtags -> stringResource(R.string.settings_muted_hashtags)
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(AppIcons.Back, stringResource(R.string.app_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().then(Modifier.padding(padding))) {
            if (error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        Row {
                            TextButton(onClick = onDismissError) {
                                Text(stringResource(R.string.settings_error_dismiss))
                            }
                            if (canRetryError) {
                                TextButton(onClick = onRetryError) {
                                    Text(stringResource(R.string.settings_error_retry))
                                }
                            }
                        }
                    }
                }
            }
            when (route) {
                SettingsRoute.Main -> SettingsScreen(
                    state.preferences,
                    onDisplay = { onRoute(SettingsRoute.Display) },
                    onNotifications = { onRoute(SettingsRoute.Notifications) },
                    onPrivacy = { onRoute(SettingsRoute.Privacy) },
                    onLanguage = { onRoute(SettingsRoute.Language) },
                )
                SettingsRoute.Display -> DisplaySettingsScreen(state.preferences, onColorScheme, onColorPalette, onBackground, onTextSize, onFont, onRequest60Hz)
                SettingsRoute.Language -> LanguageSettingsScreen(state.preferences.language, onLanguage)
                SettingsRoute.Notifications -> NotificationAccountsScreen(accounts, onNotificationAccount)
                is SettingsRoute.NotificationAccount -> NotificationSettingsScreen(
                    state = notificationSettingsState,
                    onAlertsEnabled = onNotificationAlertsEnabled,
                    onShowPreviews = onNotificationShowPreviews,
                    onPeriodicFallback = onNotificationPeriodicFallback,
                    onQuietHours = onNotificationQuietHours,
                    onCategoryChanged = onNotificationCategoryChanged,
                    onRunLocalTest = onNotificationLocalTest,
                    onRetryRegistration = onNotificationRetryRegistration,
                    onRetryStorage = onNotificationRetryStorage,
                    onRefreshDistributors = onNotificationRefreshDistributors,
                    onSelectDistributor = onNotificationSelectDistributor,
                    onRunPushConnectionTest = onNotificationPushConnectionTest,
                    onPermissionChanged = onNotificationPermissionChanged,
                )
                SettingsRoute.Privacy -> PrivacySettingsScreen(
                    state.preferences.cleanTrackingParameters,
                    onTrackingCleanup,
                    { onRoute(SettingsRoute.PrivacyAccounts) },
                    { onRoute(SettingsRoute.ContentWarnings) },
                    { onRoute(SettingsRoute.Posting) },
                )
                SettingsRoute.ContentWarnings -> ContentWarningSettingsScreen(
                    rules = state.preferences.contentWarningRules,
                    onChanged = onContentWarningRules,
                    localRules = postPreferences.contentWarningRules,
                    onLocalChanged = onPostContentWarningRules,
                    hiddenPresentation = state.preferences.hiddenContentPresentation,
                    onHiddenPresentation = onHiddenContentPresentation,
                    localAccountLabel = postPreferencesAccountLabel,
                )
                SettingsRoute.Posting -> PostingSettingsScreen(
                    preferences = postPreferences,
                    onDefaultAudience = onPostDefaultAudience,
                    onRepliesUnlisted = onPostRepliesUnlisted,
                )
                SettingsRoute.PrivacyAccounts -> PrivacyAccountsScreen(accounts) { accountId, kind ->
                    onModeration(accountId, kind)
                }
                is SettingsRoute.Moderation -> if (route.kind == ModerationKind.Hashtags) {
                     MutedHashtagsScreen(
                         moderationState,
                         onModerationRetry,
                         onModerationLoadMore,
                         onModerationAddLocalHashtag,
                         onModerationRemoveLocalHashtag,
                     )
                } else {
                    ModerationListScreen(moderationState, onModerationRetry, onModerationLoadMore, onModerationRemove)
                }
            }
        }
    }
}
