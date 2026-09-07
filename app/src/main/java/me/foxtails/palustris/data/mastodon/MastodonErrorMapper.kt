package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.domain.SourceError
import org.json.JSONObject
import java.io.IOException

object MastodonErrorMapper {
    fun map(error: ApiFailure): SourceError = map(error.status, error.body)

    fun map(status: Int, body: String? = null): SourceError = when (status) {
        401, 403 -> SourceError.Unauthorized
        429 -> SourceError.RateLimited
        404 -> SourceError.Unsupported("requested feature")
        else -> SourceError.ServerError(errorDetail(body))
    }

    fun map(error: Exception): SourceError = when (error) {
        is SourceError -> error
        is ApiFailure -> map(error)
        is IOException -> SourceError.NetworkUnavailable
        else -> SourceError.ServerError(error.message)
    }

    private fun errorDetail(body: String?): String? = body?.let {
        runCatching {
            val json = JSONObject(it)
            json.optString("error").takeIf(String::isNotBlank)
                ?: json.optString("error_description").takeIf(String::isNotBlank)
        }.getOrNull()
    }
}
