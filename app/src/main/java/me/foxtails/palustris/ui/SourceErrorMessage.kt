package me.foxtails.palustris.ui

import android.content.Context
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.SourceError

/** Maps a normalized source failure to a localized message. */
internal fun sourceErrorMessage(context: Context, error: Exception): String = when (error) {
    is SourceError.Unauthorized -> context.getString(R.string.error_source_unauthorized)
    is SourceError.AccountMismatch -> context.getString(R.string.error_source_account_mismatch)
    is SourceError.RateLimited -> context.getString(R.string.error_source_rate_limited)
    is SourceError.Unsupported -> context.getString(R.string.error_source_unsupported, error.feature)
    is SourceError.AccessDenied -> context.getString(R.string.error_source_access_denied, error.feature)
    is SourceError.ResourceLimit -> context.getString(R.string.error_source_resource_limit, error.feature)
    is SourceError.ForeignOrigin -> context.getString(R.string.error_source_foreign_origin)
    is SourceError.NetworkUnavailable -> context.getString(R.string.error_source_network)
    is SourceError.ServerError -> error.detail ?: context.getString(R.string.error_source_server)
    else -> context.getString(R.string.error_source_server)
}

internal fun requiresSignIn(error: Exception): Boolean = error is SourceError.Unauthorized
