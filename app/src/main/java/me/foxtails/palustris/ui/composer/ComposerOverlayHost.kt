package me.foxtails.palustris.ui.composer

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.TrackingParameterCleaner
import me.foxtails.palustris.ui.ComposeScreen
import me.foxtails.palustris.ui.ComposerSheet
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.shell.ComposerContract

/**
 * Narrow placement slot for the composer overlay. The shell chooses placement and owns the
 * open, guarded close, and emoji-picker target. The feature owns the sheet assembly: the
 * save control, the editor bindings, publish with its confirmation message, and tracking
 * cleanup. A composer presentation change stays in `ui/composer/`.
 */
@Composable
fun ComposerOverlayHost(
    owner: ComposerOwner,
    contract: ComposerContract,
    account: Account?,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onRequestEmoji: (ComposerField) -> Unit,
    pendingEmojiInsertion: Pair<EmojiChoice, ComposerField>?,
    onEmojiInsertionApplied: () -> Unit,
) {
    val context = LocalContext.current
    val replySentMessage = stringResource(R.string.reply_sent)
    val quoteSentMessage = stringResource(R.string.quote_sent)
    ComposerSheet(
        onDismiss = onDismiss,
        onSaveDraft = { owner.save { onClose() } },
        saveEnabled = owner.editor.text.isNotBlank() || owner.isReply,
        closing = owner.closing,
    ) {
        ComposeScreen(
            text = owner.editor.text,
            onTextChange = owner::setText,
            warning = owner.editor.warning,
            onWarningChange = owner::setWarning,
            warningEnabled = owner.editor.warningEnabled,
            onWarningEnabled = owner::setWarningEnabled,
            account = account,
            audience = owner.editor.audience,
            availableAudiences = contract.availableAudiences,
            onAudienceChange = owner::setAudience,
            canPublish = owner.canPublish,
            publishing = contract.publishing || owner.submitting,
            error = contract.error ?: owner.editor.error,
            quoteTarget = owner.quoteTarget,
            isReply = owner.isReply,
            onRemoveQuote = owner::removeTargets,
            onRequestEmoji = onRequestEmoji,
            pendingEmojiInsertion = pendingEmojiInsertion,
            onEmojiInsertionApplied = onEmojiInsertionApplied,
            onCleanTrackingParameters = {
                owner.setText(TrackingParameterCleaner.cleanText(owner.editor.text))
            },
            onPublish = {
                owner.publish { replySent, quoteSent ->
                    val message = when {
                        replySent -> replySentMessage
                        quoteSent -> quoteSentMessage
                        else -> null
                    }
                    message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    onClose()
                }
            },
        )
    }
}
