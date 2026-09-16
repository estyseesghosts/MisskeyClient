package me.foxtails.palustris.data

import android.content.Context
import me.foxtails.palustris.R

/**
 * Localized text for data-layer owners that have no [Context].
 *
 * Transport, storage, and authentication owners must not format user copy. This
 * seam owns the localized text so those owners stay Android-free and readable.
 * Production binds [from]. Tests and direct construction use [Default], which
 * carries no user-facing copy.
 *
 * This is the data-layer sibling of `ui.UiStrings`. Keep one seam for each layer
 * instead of importing presentation text into the data layer.
 */
interface AppMessages {
    fun preferencesLoadFailed(): String = ""
    fun preferencesSaveFailed(): String = ""
    fun notificationSyncFailed(): String = ""
    fun draftsLoadFailed(): String = ""
    fun draftDeleteFailed(): String = ""
    fun signInExpired(): String = ""
    fun signInAuthorizationMissing(): String = ""
    fun signInNotApproved(): String = ""
    fun misskeyServerIncompatible(): String = ""
    fun webfingerHandleInvalid(): String = ""
    fun instanceDomainInvalid(): String = ""
    fun responseLimitExceeded(): String = ""
    fun serverRequestFailed(status: Int, code: String?): String = ""

    companion object {
        /** Builds the Android-backed implementation. */
        fun from(context: Context): AppMessages = AndroidAppMessages(context)

        /**
         * Non-user-facing fallback for tests and direct construction. It returns no
         * localized copy. Production always uses [from].
         */
        val Default: AppMessages = object : AppMessages {}
    }
}

private class AndroidAppMessages(private val context: Context) : AppMessages {
    override fun preferencesLoadFailed(): String = context.getString(R.string.error_preferences_load)
    override fun preferencesSaveFailed(): String = context.getString(R.string.error_preferences_save)
    override fun notificationSyncFailed(): String = context.getString(R.string.error_notification_sync)
    override fun draftsLoadFailed(): String = context.getString(R.string.error_drafts_load)
    override fun draftDeleteFailed(): String = context.getString(R.string.error_draft_delete)
    override fun signInExpired(): String = context.getString(R.string.sign_in_expired)
    override fun signInAuthorizationMissing(): String = context.getString(R.string.sign_in_authorization_missing)
    override fun signInNotApproved(): String = context.getString(R.string.sign_in_not_approved)
    override fun misskeyServerIncompatible(): String = context.getString(R.string.error_misskey_incompatible)
    override fun webfingerHandleInvalid(): String = context.getString(R.string.error_webfinger_handle)
    override fun instanceDomainInvalid(): String = context.getString(R.string.error_instance_domain)
    override fun responseLimitExceeded(): String = context.getString(R.string.error_response_limit)
    override fun serverRequestFailed(status: Int, code: String?): String = context.getString(
        R.string.error_server_request,
        status,
        code?.takeIf(String::isNotBlank)?.let { ":$it" }.orEmpty(),
    )
}
