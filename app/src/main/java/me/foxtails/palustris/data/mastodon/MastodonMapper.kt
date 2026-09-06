package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import org.json.JSONObject

object MastodonMapper {
    fun account(json: JSONObject, origin: String): Account {
        val username = json.optString("username")
        val host = json.optString("acct").substringAfter('@', "").ifBlank {
            java.net.URI(origin).host.orEmpty()
        }
        return Account(
            id = AccountId(Connection(origin, Protocol.MASTODON), json.getString("id")),
            displayName = json.optString("display_name").ifBlank { username },
            handle = "@$username@$host",
            avatarUrl = json.optString("avatar").takeIf { it.isNotBlank() },
            biography = json.optString("note").stripHtml(),
        )
    }
}

private fun String.stripHtml(): String = replace(Regex("<[^>]*>"), "")
