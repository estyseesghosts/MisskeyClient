package me.foxtails.palustris.data.misskey

import okhttp3.Headers

/** The adapter-facing HTTP result; pagination metadata stays inside the adapter boundary. */
data class HttpResponse(val body: String, val headers: Headers) {
    fun linkHeader(): String? = headers["Link"]

    /** Returns the URL advertised for the next page by a Mastodon-style Link header. */
    fun linkHeaderCursor(): String? {
        val link = linkHeader() ?: return null
        val entryPattern = Regex("<([^>]+)>\\s*;\\s*([^,]*)")
        return entryPattern.findAll(link).firstOrNull { match ->
            val relation = Regex("\\brel\\s*=\\s*\\\"?([^\\\";,]+)").find(match.groupValues[2])
                ?.groupValues?.get(1).orEmpty().split(Regex("\\s+"))
            relation.contains("next")
        }?.groupValues?.get(1)
    }
}
