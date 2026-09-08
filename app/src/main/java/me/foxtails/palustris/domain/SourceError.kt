package me.foxtails.palustris.domain

sealed class SourceError : Exception() {
    object Unauthorized : SourceError()
    object AccountMismatch : SourceError()
    object RateLimited : SourceError()
    data class Unsupported(val feature: String) : SourceError()
    data class UnsupportedCredential(val feature: String) : SourceError()
    data class ServerUnsupported(val feature: String) : SourceError()
    object NetworkUnavailable : SourceError()
    data class ServerError(val detail: String?) : SourceError()
}
