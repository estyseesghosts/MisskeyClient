package me.foxtails.palustris.data.notifications

import me.foxtails.palustris.domain.AccountId

/**
 * True when every receiving-account reference in this state belongs to `accountId`.
 *
 * Only receiving-account fields participate. Remote actors, post authors, and remote public
 * URLs legitimately differ from the receiving account, so they must not trigger a mismatch.
 * `AccountId` equality compares the connection origin and the local ID, so the protocol stays
 * metadata.
 */
internal fun NotificationRepositoryState.hasReceivingAccount(accountId: AccountId): Boolean {
    if (items.any { it.accountId != accountId }) return false
    if (items.any { item -> item.group?.id?.accountId?.let { owner -> owner != accountId } == true }) return false
    if (deliveries.values.any { it.accountId != accountId }) return false
    if (checkpoint?.accountId?.let { it != accountId } == true) return false
    if (checkpoints.values.any { it.accountId != accountId }) return false
    if (pushRegistration?.accountId?.let { it != accountId } == true) return false
    if (dismissedIds.any { it.connection != accountId.connection.origin }) return false
    return true
}
