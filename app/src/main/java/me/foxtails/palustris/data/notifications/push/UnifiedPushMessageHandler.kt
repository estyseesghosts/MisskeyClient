package me.foxtails.palustris.data.notifications.push

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.misskey.MisskeyNotificationMapper
import me.foxtails.palustris.data.notifications.NotificationPresenter
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.work.NotificationWorkScheduler
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.NotificationReadState
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SocialEvent
import me.foxtails.palustris.domain.SourceError
import org.unifiedpush.android.connector.data.PushMessage

/** Decodes distributor messages and applies account-scoped notification effects. */
@Singleton
class UnifiedPushMessageHandler @Inject constructor(
    private val registrationRepository: PushRegistrationRepository,
    private val repository: NotificationRepository,
    private val sessionStore: SessionStore,
    private val sourceRegistry: AccountSourceRegistry,
    private val sourceFactory: SocialSourceFactory,
    private val scheduler: NotificationWorkScheduler,
    private val presenter: NotificationPresenter,
    private val pushMessageDecoder: PushMessageDecoder,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onMessage(message: PushMessage, instanceName: String) {
        val owner = registrationRepository.find(instanceName) ?: return
        scope.launch {
            try {
                val content = pushMessageDecoder.decode(instanceName, message)
                if (content == null) {
                    scheduleCatchUp(owner)
                    return@launch
                }
                when (owner.accountId.connection.protocol) {
                    Protocol.MISSKEY -> handleMisskeyPush(owner, content)
                    Protocol.MASTODON -> scheduleCatchUp(owner)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                scheduleCatchUp(owner)
            }
        }
    }

    private suspend fun handleMisskeyPush(owner: PushRegistrationOwner, content: ByteArray) {
        when (val push = MisskeyPushPayloadParser.parse(content)) {
            is MisskeyPushPayload.Notification -> try {
                val notification = MisskeyNotificationMapper.notification(
                    push.body,
                    owner.accountId.connection.origin,
                    owner.accountId,
                )
                val accepted = repository.applyStreamEvent(
                    owner.token,
                    Event(owner.accountId, SocialEvent.NotificationReceived(notification)),
                )
                if (accepted) scheduler.enqueueDelivery(owner.accountId)
                scheduleCatchUp(owner)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                scheduleCatchUp(owner)
            }
            MisskeyPushPayload.ReadAllNotifications -> {
                val accepted = repository.applyStreamEvent(
                    owner.token,
                    Event(
                        owner.accountId,
                        SocialEvent.NotificationReadChanged(
                            owner.accountId,
                            NotificationReadState(NotificationReadStatus.Read, serverAcknowledged = true),
                        ),
                    ),
                )
                if (accepted) {
                    repository.observe(owner.accountId).value.items.forEach { item ->
                        presenter.dismiss(owner.accountId, item.id)
                    }
                }
                scheduleCatchUp(owner)
            }
            is MisskeyPushPayload.NewChatMessage -> try {
                val notification = MisskeyNotificationMapper.chatMessage(
                    push.body,
                    owner.accountId.connection.origin,
                    owner.accountId,
                ) ?: error("chat message id missing")
                val accepted = repository.applyStreamEvent(
                    owner.token,
                    Event(owner.accountId, SocialEvent.NotificationReceived(notification)),
                )
                if (accepted) scheduler.enqueueDelivery(owner.accountId)
                scheduleCatchUp(owner)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                scheduleCatchUp(owner)
            }
            MisskeyPushPayload.Refresh -> scheduleCatchUp(owner)
        }
    }

    private fun scheduleCatchUp(owner: PushRegistrationOwner) {
        if (sessionStore.recordPushMessageHint(owner.accountId, owner.registration.instanceName)) {
            scheduler.enqueueCatchUp(owner.accountId)
        }
    }

    override fun close() {
        scope.cancel()
    }
}
