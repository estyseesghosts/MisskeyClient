package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.directmessages.DirectMessageConversationScreen
import me.foxtails.palustris.ui.directmessages.DirectMessageInboxScreen
import me.foxtails.palustris.ui.directmessages.DirectMessageUiState
import me.foxtails.palustris.ui.profile.ProfileScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DirectMessageScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val owner = accountFixture("owner", "Owner")
    private val recipient = accountFixture("recipient", "Recipient")
    private val conversation = DirectConversation(
        id = ConversationId(connection.origin, "conversation"),
        participants = listOf(owner, recipient),
        lastPost = post("last", recipient, "A private message"),
        unread = true,
        rootPostId = EntityId(connection.origin, "root"),
    )

    @Test
    fun inboxDisplaysPrivateConversationAndOpensIt() {
        var opened: DirectConversation? = null
        show {
            DirectMessageInboxScreen(
                accountId = owner.id,
                state = DirectMessageUiState(conversations = listOf(conversation)),
                compactLayout = false,
                onOpenConversation = { opened = it },
            )
        }

        compose.onNodeWithText("Recipient").assertIsDisplayed().performClick()
        assertEquals(conversation.id, opened?.id)
        compose.onNodeWithText("A private message").assertIsDisplayed()
    }

    @Test
    fun conversationComposerSubmitsPrivateMessage() {
        var sent = ""
        show {
            DirectMessageConversationScreen(
                accountId = owner.id,
                state = DirectMessageUiState(
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    thread = listOf(conversation.lastPost),
                ),
                compactLayout = false,
                onSend = { sent = it },
            )
        }

        compose.onNodeWithTag("direct_message_input").performTextInput("New private message")
        compose.onNodeWithTag("direct_message_send").performClick()

        assertEquals("New private message", sent)
        compose.onNodeWithTag("direct_message_thread").assertIsDisplayed()
    }

    @Test
    fun profileMessageActionReturnsTheDisplayedRemoteAccount() {
        var messaged: Account? = null
        show {
            ProfileScreen(
                account = recipient,
                authenticatedAccountId = owner.id,
                compactLayout = false,
                onMessage = { messaged = it },
            )
        }

        compose.onNodeWithTag("profile_message_action").performClick()

        assertEquals(recipient.id, messaged?.id)
    }

    private fun show(content: @Composable () -> Unit) {
        compose.activity.runOnUiThread {
            compose.activity.setContent { PalustrisTheme { content() } }
        }
        compose.waitForIdle()
    }

    private fun accountFixture(localId: String, displayName: String) = Account(
        id = AccountId(connection, localId),
        displayName = displayName,
        handle = "@$localId@example.org",
    )

    private fun post(id: String, author: Account, text: String) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = text,
        publishedAtEpochMillis = 0L,
        audience = Audience.Direct,
    )
}
