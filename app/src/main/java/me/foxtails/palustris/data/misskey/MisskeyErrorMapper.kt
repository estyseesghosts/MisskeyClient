package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.SourceError
import java.io.IOException

object MisskeyErrorMapper {
    fun map(error: ApiFailure): SourceError = when (error.status) {
        401, 403 -> SourceError.Unauthorized
        429 -> SourceError.RateLimited
        404 -> SourceError.Unsupported(error.code ?: "requested feature")
        else -> SourceError.ServerError(error.code ?: error.message)
    }

    fun map(error: Exception): SourceError = when (error) {
        is SourceError -> error
        is ApiFailure -> map(error)
        is IOException -> SourceError.NetworkUnavailable
        else -> SourceError.ServerError(error.message)
    }
}
