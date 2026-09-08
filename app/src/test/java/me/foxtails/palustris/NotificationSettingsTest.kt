package me.foxtails.palustris

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.notifications.FileNotificationStore
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.withCategoryEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationSettingsTest {
    @Test
    fun categorySwitchesEditAllMinusOneAndAllowEmptySelection() {
        val withoutMentions = NotificationSettings(
            categories = setOf(NotificationCategory.All),
        ).withCategoryEnabled(NotificationCategory.Mentions, enabled = false)
        assertFalse(NotificationCategory.All in withoutMentions.categories)
        assertFalse(NotificationCategory.Mentions in withoutMentions.categories)
        assertTrue(NotificationCategory.Replies in withoutMentions.categories)

        val empty = NotificationSettings(categories = setOf(NotificationCategory.Mentions))
            .withCategoryEnabled(NotificationCategory.Mentions, enabled = false)
        assertEquals(emptySet<NotificationCategory>(), empty.categories)

        val one = empty.withCategoryEnabled(NotificationCategory.Replies, enabled = true)
        assertEquals(setOf(NotificationCategory.Replies), one.categories)
    }

    @Test
    fun emptyCategorySelectionSurvivesRepositoryRestart() = runBlocking {
        val account = AccountId(Connection("https://settings.example", Protocol.MASTODON), "empty-categories")
        val token = NotificationSyncToken(account, 1)
        val first = NotificationRepository(FileNotificationStore(ApplicationProvider.getApplicationContext()))
        first.activate(token)
        first.updateSettings(token, NotificationSettings(categories = emptySet()))

        val restarted = NotificationRepository(FileNotificationStore(ApplicationProvider.getApplicationContext()))
        restarted.activate(token)
        assertEquals(emptySet<NotificationCategory>(), restarted.settings(account).categories)
        restarted.remove(account)
    }
}
