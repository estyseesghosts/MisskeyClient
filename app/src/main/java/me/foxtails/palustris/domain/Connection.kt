package me.foxtails.palustris.domain

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A server origin together with the protocol used to communicate with it. */
data class Connection(val origin: String, val protocol: Protocol) {
    /** Returns whether this origin follows the same rules as ServerAddress.normalize. */
    fun isValid(): Boolean {
        val value = origin.trim()
        val url = value.toHttpUrlOrNull()
        return url != null && url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty() &&
            url.encodedPath == "/" && url.query == null && url.fragment == null
    }
}

enum class Protocol {
    MISSKEY,
    MASTODON,
}
