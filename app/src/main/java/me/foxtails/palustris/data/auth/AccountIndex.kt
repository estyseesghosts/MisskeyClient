package me.foxtails.palustris.data.auth

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.Protocol
import org.json.JSONObject

/** Non-secret account metadata used to render the account switcher. */
data class AccountRef(
    val accountId: AccountId,
    val handle: String,
    val avatarUrl: String?,
    val displayName: String,
    val protocol: Protocol = accountId.connection.protocol,
    val biography: String = "",
    val profileFields: List<ProfileField> = emptyList(),
)

data class AccountIndex(
    val version: Int = 1,
    val accounts: List<AccountRef> = emptyList(),
    val activeAccountId: AccountId? = null,
    val schemaVersion: Int = version,
)

fun AccountRef.toAccount(): Account = Account(accountId, displayName, handle, avatarUrl, biography, profileFields)

internal fun AccountId.toIndexJson(): JSONObject = JSONObject()
    .put("origin", connection.origin)
    .put("localId", localId)
    .put("protocol", connection.protocol.name)

internal fun JSONObject.toAccountId(): AccountId = AccountId(
    Connection(getString("origin"), Protocol.valueOf(getString("protocol"))),
    getString("localId"),
)
