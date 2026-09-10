package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.PushProviderInfo
import me.foxtails.palustris.domain.PushSubscription
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONObject

internal class MastodonPushService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId,
) {
    suspend fun providerInfo() = PushProviderInfo(status = CapabilityStatus.Supported)

    suspend fun query(knownEndpoint: ValidatedUrl?): PushSubscription? {
        val subscription = readOwned()
        if (knownEndpoint != null && subscription != null && subscription.endpoint != knownEndpoint) throw SourceError.ServerError("notifications.push.identity-changed")
        return subscription
    }

    suspend fun createOrReplace(spec: PushSubscriptionSpec, previous: PushSubscription?): PushSubscription {
        validateSpec(spec); previous?.let(::validateSubscription)
        return confirmed(api.postForm(origin, "api/v1/push/subscription", createFields(spec), token).body, spec.endpoint)
    }

    suspend fun updatePolicy(subscription: PushSubscription, alerts: Set<NotificationCategory>): PushSubscription {
        validateSubscription(subscription)
        val confirmed = confirmed(api.putForm(origin, "api/v1/push/subscription", alertFields(alerts), token).body, subscription.endpoint)
        if (!sameIdentity(confirmed, subscription)) throw SourceError.ServerError("notifications.push.identity-changed")
        return confirmed
    }

    suspend fun remove(subscription: PushSubscription) {
        validateSubscription(subscription)
        val current = readOwned() ?: return
        if (!sameIdentity(current, subscription)) throw SourceError.ServerError("notifications.push.identity-changed")
        api.delete(origin, "api/v1/push/subscription", token)
    }

    private fun validateSpec(spec: PushSubscriptionSpec) {
        if (spec.accountId != accountId || spec.publicKey.isBlank() || spec.authSecret.isBlank()) throw SourceError.Unsupported("notifications.push.spec")
    }
    private suspend fun readOwned(): PushSubscription? = try {
        confirmed(api.get(origin, "v1/push/subscription", token).body)
    } catch (error: ApiFailure) { if (error.status == 404) null else throw error }
    private fun confirmed(body: String, expectedEndpoint: ValidatedUrl? = null): PushSubscription {
        val json = JSONObject(body); val endpoint = ValidatedUrl.https(json.optString("endpoint")) ?: throw SourceError.ServerError("notifications.push.confirmation")
        if (expectedEndpoint != null && endpoint != expectedEndpoint) throw SourceError.ServerError("notifications.push.confirmation")
        val remoteId = json.optString("id").takeIf { it.isNotBlank() } ?: throw SourceError.ServerError("notifications.push.confirmation")
        return PushSubscription(accountId, endpoint, remoteId)
    }
    private fun validateSubscription(subscription: PushSubscription) {
        if (subscription.accountId != accountId || subscription.endpoint.value.isBlank()) throw SourceError.AccountMismatch
    }
    private fun sameIdentity(first: PushSubscription, second: PushSubscription) = first.accountId == second.accountId && first.endpoint == second.endpoint && first.remoteId == second.remoteId
    private fun createFields(spec: PushSubscriptionSpec) = buildList {
        add("subscription[endpoint]" to spec.endpoint.value); add("subscription[keys][p256dh]" to spec.publicKey); add("subscription[keys][auth]" to spec.authSecret)
        add("subscription[standard]" to spec.standardWebPush.toString()); addAll(alertFields(spec.alerts))
    }
    private fun alertFields(alerts: Set<NotificationCategory>): List<Pair<String, String>> {
        val all = NotificationCategory.All in alerts; fun enabled(category: NotificationCategory) = (all || category in alerts).toString()
        return listOf("data[alerts][mention]" to enabled(NotificationCategory.Mentions), "data[alerts][quote]" to enabled(NotificationCategory.Quotes),
            "data[alerts][reblog]" to enabled(NotificationCategory.Social), "data[alerts][follow]" to enabled(NotificationCategory.Social),
            "data[alerts][follow_request]" to enabled(NotificationCategory.Social), "data[alerts][favourite]" to enabled(NotificationCategory.Social),
            "data[alerts][poll]" to enabled(NotificationCategory.Polls), "data[alerts][status]" to enabled(NotificationCategory.Social),
            "data[alerts][update]" to enabled(NotificationCategory.Social), "data[alerts][quoted_update]" to enabled(NotificationCategory.Quotes))
    }
}
