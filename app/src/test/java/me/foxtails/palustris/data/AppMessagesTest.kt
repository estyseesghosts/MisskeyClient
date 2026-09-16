package me.foxtails.palustris.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.foxtails.palustris.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The data-layer text seam resolves the string catalog. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppMessagesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val messages = AppMessages.from(context)

    @Test
    fun everyMessageComesFromTheCatalog() {
        assertEquals(context.getString(R.string.error_preferences_load), messages.preferencesLoadFailed())
        assertEquals(context.getString(R.string.error_preferences_save), messages.preferencesSaveFailed())
        assertEquals(context.getString(R.string.error_notification_sync), messages.notificationSyncFailed())
        assertEquals(context.getString(R.string.error_drafts_load), messages.draftsLoadFailed())
        assertEquals(context.getString(R.string.error_draft_delete), messages.draftDeleteFailed())
        assertEquals(context.getString(R.string.sign_in_expired), messages.signInExpired())
        assertEquals(context.getString(R.string.sign_in_authorization_missing), messages.signInAuthorizationMissing())
        assertEquals(context.getString(R.string.sign_in_not_approved), messages.signInNotApproved())
        assertEquals(context.getString(R.string.error_misskey_incompatible), messages.misskeyServerIncompatible())
        assertEquals(context.getString(R.string.error_webfinger_handle), messages.webfingerHandleInvalid())
        assertEquals(context.getString(R.string.error_instance_domain), messages.instanceDomainInvalid())
        assertEquals(context.getString(R.string.error_response_limit), messages.responseLimitExceeded())
    }

    @Test
    fun serverRequestFailureFormatsTheStatusAndCode() {
        assertEquals(
            context.getString(R.string.error_server_request, 500, ":SERVER_ERROR"),
            messages.serverRequestFailed(500, "SERVER_ERROR"),
        )
        assertEquals(
            context.getString(R.string.error_server_request, 404, ""),
            messages.serverRequestFailed(404, null),
        )
    }

    @Test
    fun theDefaultCarriesNoUserFacingCopy() {
        assertTrue(AppMessages.Default.preferencesLoadFailed().isEmpty())
        assertTrue(AppMessages.Default.serverRequestFailed(500, null).isEmpty())
    }
}
