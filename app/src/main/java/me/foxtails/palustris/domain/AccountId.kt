package me.foxtails.palustris.domain

/** An account-scoped identifier. The protocol is metadata; origin and local ID define identity. */
data class AccountId(val connection: Connection, val localId: String) {
    override fun equals(other: Any?): Boolean = other is AccountId &&
        connection.origin == other.connection.origin && localId == other.localId

    override fun hashCode(): Int = 31 * connection.origin.hashCode() + localId.hashCode()
}
