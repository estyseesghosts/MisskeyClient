package me.foxtails.palustris.data.notifications.work

import java.security.MessageDigest
import me.foxtails.palustris.domain.AccountId

object NotificationWorkNames {
    const val INPUT_ORIGIN = "account.origin"
    const val INPUT_LOCAL_ID = "account.localId"
    const val INPUT_PROTOCOL = "account.protocol"

    fun reconcile(accountId: AccountId): String = "notifications.reconcile.${accountKey(accountId)}"
    fun catchUp(accountId: AccountId): String = "notifications.catch-up.${accountKey(accountId)}"
    fun delivery(accountId: AccountId): String = "notifications.delivery.${accountKey(accountId)}"
    fun periodic(accountId: AccountId): String = "notifications.periodic.${accountKey(accountId)}"

    private fun accountKey(accountId: AccountId): String = MessageDigest.getInstance("SHA-256")
        .digest("${accountId.connection.origin}\u0000${accountId.connection.protocol}\u0000${accountId.localId}".toByteArray())
        .take(12)
        .joinToString("") { byte -> "%02x".format(byte) }
}
