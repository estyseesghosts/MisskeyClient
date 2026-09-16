package me.foxtails.palustris.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import me.foxtails.palustris.ui.shell.AccountSwitcher
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import me.foxtails.palustris.ui.shell.DraftsContract
import me.foxtails.palustris.ui.shell.EmojiPresentation
import me.foxtails.palustris.ui.shell.NotificationSettingsContract
import me.foxtails.palustris.ui.shell.NotificationsContract
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.PostInteractions
import me.foxtails.palustris.ui.shell.ProfileContract
import me.foxtails.palustris.ui.shell.SearchContract
import me.foxtails.palustris.ui.shell.ThreadContract

// Preview-only placement lives here, beside the production shell it composes.
// PalustrisApp.kt keeps navigation and placement only.
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,dpi=420")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppPreview() {
    PalustrisApp(
        account = null,
        sessionGeneration = 0L,
        sessionRevision = 0L,
        home = null,
        photoGrid = PhotoGridContract.Empty,
        profile = ProfileContract.Empty,
        accountSwitcher = AccountSwitcher.Empty,
        composer = ComposerContract.Empty,
        search = SearchContract.Empty,
        postInteractions = PostInteractions.Empty,
        thread = ThreadContract.Empty,
        draftsContract = DraftsContract.Empty,
        emojiPresentation = EmojiPresentation.Empty,
        bookmarks = BookmarksContract.Empty,
        notifications = NotificationsContract.Empty,
        directMessages = DirectMessagesContract.Empty,
        initialNotificationRoute = null,
        notificationSettings = NotificationSettingsContract.Empty,
    )
}
