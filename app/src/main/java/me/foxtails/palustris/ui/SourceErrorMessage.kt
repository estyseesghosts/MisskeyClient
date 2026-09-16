package me.foxtails.palustris.ui

import android.content.Context
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline

/** Maps a normalized source failure to a localized message. */
internal fun sourceErrorMessage(context: Context, error: Exception): String = when (error) {
    is SourceError.Unauthorized -> context.getString(R.string.error_source_unauthorized)
    is SourceError.AccountMismatch -> context.getString(R.string.error_source_account_mismatch)
    is SourceError.RateLimited -> context.getString(R.string.error_source_rate_limited)
    is SourceError.Unsupported -> context.getString(R.string.error_source_unsupported, featureLabel(context, error.feature))
    is SourceError.AccessDenied -> context.getString(R.string.error_source_access_denied, featureLabel(context, error.feature))
    is SourceError.ResourceLimit -> context.getString(R.string.error_source_resource_limit, featureLabel(context, error.feature))
    is SourceError.ForeignOrigin -> context.getString(R.string.error_source_foreign_origin)
    is SourceError.NetworkUnavailable -> context.getString(R.string.error_source_network)
    // A blank detail must not silence the message.
    is SourceError.ServerError -> error.detail?.takeIf(String::isNotBlank) ?: context.getString(R.string.error_source_server)
    else -> context.getString(R.string.error_source_server)
}

/**
 * Resolves a technical feature identifier to a human label. The identifiers stay
 * protocol-neutral on the error. An unknown identifier falls back to a generic
 * phrase, so a raw protocol code never reaches the user.
 */
internal fun featureLabel(context: Context, feature: String): String = when {
    feature.startsWith(TIMELINE_PREFIX) -> timelineLabel(context, feature.removePrefix(TIMELINE_PREFIX))
    feature.startsWith(AUDIENCE_PREFIX) -> audienceLabel(context, feature.removePrefix(AUDIENCE_PREFIX))
    else -> context.getString(R.string.error_feature_generic)
}

private fun timelineLabel(context: Context, name: String): String =
    runCatching { context.getString(timelineLabelRes(Timeline.valueOf(name))) }
        .getOrDefault(context.getString(R.string.error_feature_generic))

private fun audienceLabel(context: Context, name: String): String = when (name) {
    Audience.Public.name -> context.getString(R.string.audience_everyone)
    Audience.Unlisted.name -> context.getString(R.string.audience_unlisted)
    Audience.Followers.name -> context.getString(R.string.audience_followers)
    Audience.Direct.name -> context.getString(R.string.audience_direct)
    else -> context.getString(R.string.error_feature_generic)
}

internal fun requiresSignIn(error: Exception): Boolean = error is SourceError.Unauthorized

private const val TIMELINE_PREFIX = "timeline:"
private const val AUDIENCE_PREFIX = "audience:"
