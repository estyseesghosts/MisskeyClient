package me.foxtails.palustris.ui

import android.content.Context
import me.foxtails.palustris.R

/**
 * Localized text for view models and non-composable owners.
 *
 * View models must not hold a [Context]. This seam owns the localized copy so the
 * owning class stays Android-free and readable. Production binds [from]; tests and
 * direct construction use [Default], which carries no user-facing copy.
 */
interface UiStrings {
    fun sourceError(error: Exception): String
    fun directMessagesUnsupported(): String
    fun profileDetailsUnsupported(): String
    fun savedPostsUnsupported(likes: Boolean): String
    fun accountSessionUnavailable(): String
    fun moderationLoadFailed(): String
    fun relationshipUnavailable(): String
    fun composerDraftSaveFailed(): String
    fun composerDraftSaveFailedOpen(): String
    fun composerAudienceUnavailable(): String
    fun composerQuotedPost(): String
    fun sessionRestoreFailed(): String
    fun sessionAccountUnavailable(): String
    fun sessionNoBrowser(): String
    fun sessionCallbackInvalid(): String

    companion object {
        /** Builds the Android-backed implementation. */
        fun from(context: Context): UiStrings = AndroidUiStrings(context)

        /**
         * Non-user-facing fallback for tests and direct construction. It returns no
         * localized copy. Production always uses [from].
         */
        val Default: UiStrings = object : UiStrings {
            override fun sourceError(error: Exception): String = error.message.orEmpty()
            override fun directMessagesUnsupported(): String = ""
            override fun profileDetailsUnsupported(): String = ""
            override fun savedPostsUnsupported(likes: Boolean): String = ""
            override fun accountSessionUnavailable(): String = ""
            override fun moderationLoadFailed(): String = ""
            override fun relationshipUnavailable(): String = ""
            override fun composerDraftSaveFailed(): String = ""
            override fun composerDraftSaveFailedOpen(): String = ""
            override fun composerAudienceUnavailable(): String = ""
            override fun composerQuotedPost(): String = ""
            override fun sessionRestoreFailed(): String = ""
            override fun sessionAccountUnavailable(): String = ""
            override fun sessionNoBrowser(): String = ""
            override fun sessionCallbackInvalid(): String = ""
        }
    }
}

private class AndroidUiStrings(private val context: Context) : UiStrings {
    override fun sourceError(error: Exception): String = sourceErrorMessage(context, error)
    override fun directMessagesUnsupported(): String = context.getString(R.string.error_feature_direct_messages)
    override fun profileDetailsUnsupported(): String = context.getString(R.string.error_feature_profile_details)
    override fun savedPostsUnsupported(likes: Boolean): String = context.getString(
        R.string.saved_posts_unsupported,
        context.getString(if (likes) R.string.collection_likes else R.string.collection_saved_posts),
    )
    override fun accountSessionUnavailable(): String = context.getString(R.string.moderation_session_unavailable)
    override fun moderationLoadFailed(): String = context.getString(R.string.moderation_load_failed)
    override fun relationshipUnavailable(): String = context.getString(R.string.post_relationship_unavailable)
    override fun composerDraftSaveFailed(): String = context.getString(R.string.composer_draft_save_failed)
    override fun composerDraftSaveFailedOpen(): String = context.getString(R.string.composer_draft_save_failed_open)
    override fun composerAudienceUnavailable(): String = context.getString(R.string.composer_audience_unavailable)
    override fun composerQuotedPost(): String = context.getString(R.string.composer_quoted_post)
    override fun sessionRestoreFailed(): String = context.getString(R.string.session_restore_failed)
    override fun sessionAccountUnavailable(): String = context.getString(R.string.settings_account_unavailable)
    override fun sessionNoBrowser(): String = context.getString(R.string.session_no_browser)
    override fun sessionCallbackInvalid(): String = context.getString(R.string.session_callback_invalid)
}
