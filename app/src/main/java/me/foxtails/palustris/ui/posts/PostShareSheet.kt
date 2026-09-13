package me.foxtails.palustris.ui.posts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.BubblePlacement
import me.foxtails.palustris.ui.WindowAnchorPositionProvider
import me.foxtails.palustris.ui.components.PillAction
import me.foxtails.palustris.ui.links.ExternalLinkHandler

@Composable
internal fun PostShareSheet(
    target: PostActionTarget,
    relationship: PostRelationshipState,
    report: PostReportState = PostReportState(),
    onDismiss: () -> Unit,
    onRelationshipAction: (RelationshipMutation) -> Unit,
    onSubmitReport: (String) -> Unit,
    onOpenDirectMessage: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
) {
    Popup(
        popupPositionProvider = WindowAnchorPositionProvider(target.anchorBounds, BubblePlacement.Above, edgeMargin = 16),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        ShareActionCard(
            target = target,
            relationship = relationship,
            report = report,
            onDismiss = onDismiss,
            onRelationshipAction = onRelationshipAction,
            onSubmitReport = onSubmitReport,
            onOpenDirectMessage = onOpenDirectMessage,
            onCopyLink = onCopyLink,
            onShare = onShare,
        )
    }
}

/** Compatibility entry point for previews and focused presentation tests. */
@Composable
internal fun PostShareSheet(post: Post, onDismiss: () -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    val target = remember(post.id) {
        PostActionTarget(
            ownedPost = me.foxtails.palustris.domain.OwnedPost(post.author.id, post),
            anchorBounds = Rect.Zero,
            ownerAccountId = post.author.id,
            sessionRevision = 0L,
        )
    }
    PostShareSheet(
        target = target,
        relationship = PostRelationshipState(
            target = post.author.id,
            relationship = ProfileRelationship(post.author.id),
        ),
        onDismiss = onDismiss,
        onRelationshipAction = {},
        onSubmitReport = {},
        onOpenDirectMessage = {},
        onCopyLink = { copyPostShareContent(context, post) },
        onShare = onShare,
    )
}

@Composable
private fun ShareActionCard(
    target: PostActionTarget,
    relationship: PostRelationshipState,
    report: PostReportState,
    onDismiss: () -> Unit,
    onRelationshipAction: (RelationshipMutation) -> Unit,
    onSubmitReport: (String) -> Unit,
    onOpenDirectMessage: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
) {
    var reportOpen by remember(target.post.id) { mutableStateOf(false) }
    var comment by remember(target.post.id) { mutableStateOf("") }
    var blockConfirmation by remember(target.post.id) { mutableStateOf(false) }
    val handle = target.author.handle
    val self = target.author.id == target.ownerAccountId
    val current = relationship.relationship
    val relationshipLoading = relationship.loading || relationship.mutation != null
    val cardDescription = stringResource(R.string.post_share_card_description, handle)

    Box(Modifier.testTag("post_share_card")) {
        Surface(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .testTag("post_share_sheet")
                .semantics { contentDescription = cardDescription },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .widthIn(max = 344.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (reportOpen) {
                ReportForm(
                    handle = handle,
                    comment = comment,
                    report = report,
                    onCommentChanged = { comment = it },
                    onCancel = { reportOpen = false },
                    onSubmit = { onSubmitReport(comment) },
                )
            } else {
                RelationshipAction(
                    tag = "post_share_follow",
                    label = when {
                        current?.requested == true -> stringResource(R.string.post_share_requested)
                        current?.following == true -> stringResource(R.string.post_share_unfollow)
                        else -> stringResource(R.string.post_share_follow)
                    },
                    handle = handle,
                    enabled = !self && !relationshipLoading && current?.requested != true && current != null,
                    loading = relationship.mutation == RelationshipMutation.Follow || relationship.mutation == RelationshipMutation.Unfollow,
                    onClick = {
                        onRelationshipAction(
                            if (current?.following == true) RelationshipMutation.Unfollow else RelationshipMutation.Follow,
                        )
                    },
                )
                RelationshipAction(
                    tag = "post_share_block",
                    label = when {
                        current?.blocking == true -> stringResource(R.string.post_share_unblock)
                        blockConfirmation -> stringResource(R.string.post_share_confirm_block)
                        else -> stringResource(R.string.post_share_block)
                    },
                    handle = handle,
                    enabled = !self && !relationshipLoading && current != null,
                    loading = relationship.mutation == RelationshipMutation.Block || relationship.mutation == RelationshipMutation.Unblock,
                    onClick = {
                        if (current?.blocking == true) {
                            onRelationshipAction(RelationshipMutation.Unblock)
                        } else if (blockConfirmation) {
                            blockConfirmation = false
                            onRelationshipAction(RelationshipMutation.Block)
                        } else {
                            blockConfirmation = true
                        }
                    },
                )
                RelationshipAction(
                    tag = "post_share_mute",
                    label = if (current?.muting == true) stringResource(R.string.post_share_unmute) else stringResource(R.string.post_share_mute),
                    handle = handle,
                    enabled = !self && !relationshipLoading && current != null,
                    loading = relationship.mutation == RelationshipMutation.Mute || relationship.mutation == RelationshipMutation.Unmute,
                    onClick = {
                        onRelationshipAction(if (current?.muting == true) RelationshipMutation.Unmute else RelationshipMutation.Mute)
                    },
                )
                RelationshipAction(
                    tag = "post_share_report",
                    label = stringResource(R.string.post_share_report),
                    handle = handle,
                    enabled = !self && !relationshipLoading,
                    onClick = { reportOpen = true },
                )
            }
            if (!reportOpen) {
                Spacer(Modifier.height(6.dp))
                BottomShareActions(
                    hasLink = target.post.url?.isNotBlank() == true,
                    enabled = true,
                    onOpenDirectMessage = onOpenDirectMessage,
                    onCopyLink = onCopyLink,
                    onShare = onShare,
                )
            }
            relationship.error?.let {
                Text(it, modifier = Modifier.testTag("post_share_relationship_error"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

}

@Composable
private fun RelationshipAction(
    tag: String,
    label: String,
    handle: String,
    enabled: Boolean,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    PillAction(
        label = label,
        onClick = onClick,
        enabled = enabled,
        loading = loading,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        contentDescription = "$label $handle",
        fillContent = true,
    )
}

@Composable
private fun BottomShareActions(
    hasLink: Boolean,
    enabled: Boolean,
    onOpenDirectMessage: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("post_share_bottom"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            ShareCell(AppIcons.Chat, stringResource(R.string.post_share_pm), enabled, onOpenDirectMessage, Modifier.weight(1f).testTag("post_share_pm"))
            ShareCell(AppIcons.Link, stringResource(R.string.post_share_copy_link), enabled && hasLink, onCopyLink, Modifier.weight(1f).testTag("post_share_copy"))
            ShareCell(AppIcons.Share, stringResource(R.string.post_share_system), enabled, onShare, Modifier.weight(1f).testTag("post_share_system"))
        }
    }
}

@Composable
private fun ShareCell(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(icon, null, Modifier.padding(12.dp))
        }
        Text(label, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ReportForm(
    handle: String,
    comment: String,
    report: PostReportState,
    onCommentChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    Text(stringResource(R.string.post_share_report_title, handle), style = MaterialTheme.typography.titleMedium)
    BasicTextField(
        value = comment,
        onValueChange = onCommentChanged,
        enabled = !report.submitting && !report.submitted,
        modifier = Modifier.fillMaxWidth().testTag("post_share_report_comment"),
        decorationBox = { field ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                androidx.compose.foundation.layout.Box(Modifier.padding(14.dp)) { field() }
            }
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillAction(stringResource(R.string.action_cancel), onCancel, Modifier.weight(1f), enabled = !report.submitting, fillContent = true)
        PillAction(
            label = if (report.submitting) stringResource(R.string.post_share_submitting) else stringResource(R.string.action_submit),
            onClick = onSubmit,
            modifier = Modifier.weight(1f).testTag("post_share_report_submit"),
            enabled = comment.isNotBlank() && !report.submitting && !report.submitted,
            loading = report.submitting,
            fillContent = true,
        )
    }
    report.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("post_share_report_error")) }
    if (report.submitted) Text(stringResource(R.string.post_share_report_submitted), modifier = Modifier.testTag("post_share_report_success"))
}

internal fun copyPostShareContent(context: Context, post: Post) {
    val value = post.url?.takeIf(String::isNotBlank)?.let(ExternalLinkHandler::prepare) ?: return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        Toast.makeText(context, context.getString(R.string.post_share_copy_failed), Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.post_share_clip_label), value))
    Toast.makeText(context, context.getString(R.string.post_share_copied), Toast.LENGTH_SHORT).show()
}
