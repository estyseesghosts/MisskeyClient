package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.PushProviderInfo
import me.foxtails.palustris.domain.PushSubscription
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONObject

internal class MisskeyPushService(private val origin: String, private val token: String, private val api: MisskeyApi, private val accountId: AccountId?) {
    suspend fun providerInfo(): PushProviderInfo {
        val key = JSONObject(api.post(origin, "meta").body).optString("swPublickey").takeIf(String::isNotBlank)
        return PushProviderInfo(if (key == null) CapabilityStatus.Unsupported else CapabilityStatus.Supported, vapidPublicKey = key)
    }
    suspend fun query(knownEndpoint: ValidatedUrl?): PushSubscription? = knownEndpoint?.let { read(it) }
    suspend fun createOrReplace(spec: PushSubscriptionSpec, previous: PushSubscription?): PushSubscription {
        validateSpec(spec)
        val created = confirmed(pushApiCall { api.post(origin, "sw/register", JSONObject().put("i", token).put("endpoint", spec.endpoint.value).put("auth", spec.authSecret).put("publickey", spec.publicKey).put("sendReadMessage", false)) }.body, spec.endpoint)
        if (previous != null && previous.endpoint != created.endpoint) removeRaw(previous)
        return created
    }
    suspend fun updatePolicy(subscription: PushSubscription, alerts: Set<NotificationCategory>): PushSubscription {
        validateSubscription(subscription)
        val confirmed = confirmed(pushApiCall { api.post(origin, "sw/update-registration", JSONObject().put("i", token).put("endpoint", subscription.endpoint.value).put("sendReadMessage", false)) }.body, subscription.endpoint)
        return confirmed.copy(remoteId = subscription.remoteId ?: confirmed.remoteId)
    }
    suspend fun remove(subscription: PushSubscription) { validateSubscription(subscription); removeRaw(subscription) }
    private fun requireAccountId() = accountId ?: throw SourceError.Unsupported("notifications.account")
    private fun validateSpec(spec: PushSubscriptionSpec) { if (spec.accountId != requireAccountId() || spec.publicKey.isBlank() || spec.authSecret.isBlank()) throw SourceError.Unsupported("notifications.push.spec") }
    private fun validateSubscription(subscription: PushSubscription) { if (subscription.accountId != requireAccountId() || subscription.endpoint.value.isBlank()) throw SourceError.AccountMismatch }
    private suspend fun read(endpoint: ValidatedUrl): PushSubscription? = try { confirmed(pushApiCall { api.post(origin, "sw/show-registration", JSONObject().put("i", token).put("endpoint", endpoint.value)) }.body, endpoint) } catch (error: ApiFailure) { if (error.status == 404 || error.code in MISSING_CODES) null else throw error }
    private suspend fun removeRaw(subscription: PushSubscription) {
        val current = read(subscription.endpoint) ?: return
        if (current.endpoint != subscription.endpoint) throw SourceError.ServerError("notifications.push.identity-changed")
        try { pushApiCall { api.post(origin, "sw/unregister", JSONObject().put("i", token).put("endpoint", subscription.endpoint.value)) } } catch (error: ApiFailure) { if (error.status != 404 && error.code !in MISSING_CODES) throw error }
    }
    private fun confirmed(body: String, expected: ValidatedUrl): PushSubscription {
        val json = JSONObject(body); val endpoint = ValidatedUrl.https(json.optString("endpoint")) ?: throw SourceError.ServerError("notifications.push.confirmation")
        if (endpoint != expected) throw SourceError.ServerError("notifications.push.confirmation")
        return PushSubscription(requireAccountId(), endpoint, json.optString("key").takeIf(String::isNotBlank))
    }
    private suspend fun pushApiCall(call: suspend () -> HttpResponse) = try { call() } catch (error: ApiFailure) {
        if (error.status == 403 && error.code in SECURE_CODES) throw SourceError.UnsupportedCredential("notifications.push.secure-credential")
        throw error
    }
    private companion object { val SECURE_CODES = setOf("ACCESS_DENIED", "AUTHENTICATION_FAILED", "SECURE_CREDENTIAL_REQUIRED"); val MISSING_CODES = setOf("NOT_FOUND", "NO_SUCH_REGISTRATION", "REGISTRATION_NOT_FOUND") }
}
