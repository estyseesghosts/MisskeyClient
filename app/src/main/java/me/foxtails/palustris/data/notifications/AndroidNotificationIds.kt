package me.foxtails.palustris.data.notifications

import java.nio.ByteBuffer
import java.security.MessageDigest
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId

object AndroidNotificationIds {
    fun tag(accountId: AccountId): String = "account-${digest(accountKey(accountId)).take(16)}"

    fun group(accountId: AccountId): String = "notifications-${digest(accountKey(accountId)).take(16)}"

    fun id(notificationId: EntityId): Int {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(entityKey(notificationId).toByteArray(Charsets.UTF_8))
        val value = ByteBuffer.wrap(bytes).int and Int.MAX_VALUE
        return value.coerceAtLeast(1)
    }

    private fun accountKey(accountId: AccountId): String =
        "${accountId.connection.origin}\u0000${accountId.connection.protocol}\u0000${accountId.localId}"

    private fun entityKey(notificationId: EntityId): String =
        "${notificationId.connection}\u0000${notificationId.value}"

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
