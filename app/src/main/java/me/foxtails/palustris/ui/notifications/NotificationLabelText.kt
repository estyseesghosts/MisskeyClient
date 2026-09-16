package me.foxtails.palustris.ui.notifications

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.NotificationLabel
import me.foxtails.palustris.domain.NotificationLabelCode

/** The bundled string for a label code. This is the only mapping from code to resource. */
@StringRes
fun NotificationLabelCode.stringRes(): Int = when (this) {
    NotificationLabelCode.Reaction -> R.string.notification_label_reaction
    NotificationLabelCode.ScheduledPostFailed -> R.string.notification_label_scheduled_post_failed
    NotificationLabelCode.ApplicationEvent -> R.string.notification_label_application_event
    NotificationLabelCode.AccountAchievement -> R.string.notification_label_account_achievement
    NotificationLabelCode.ModerationEvent -> R.string.notification_label_moderation_event
    NotificationLabelCode.RelationshipChanged -> R.string.notification_label_relationship_changed
    NotificationLabelCode.ChatInvitationUnavailable -> R.string.notification_label_chat_invitation_unavailable
    NotificationLabelCode.ExportCompleted -> R.string.notification_label_export_completed
    NotificationLabelCode.NewSignIn -> R.string.notification_label_new_sign_in
    NotificationLabelCode.AccessTokenCreated -> R.string.notification_label_access_token_created
    NotificationLabelCode.TestNotification -> R.string.notification_label_test_notification
    NotificationLabelCode.NewActivity -> R.string.notification_label_new_activity
    NotificationLabelCode.AccountEvent -> R.string.notification_label_account_event
}

@Composable
fun NotificationLabel.text(): String = when (this) {
    is NotificationLabel.Plain -> value
    is NotificationLabel.Coded -> stringResource(code.stringRes())
}

fun NotificationLabel.text(context: Context): String = when (this) {
    is NotificationLabel.Plain -> value
    is NotificationLabel.Coded -> context.getString(code.stringRes())
}
