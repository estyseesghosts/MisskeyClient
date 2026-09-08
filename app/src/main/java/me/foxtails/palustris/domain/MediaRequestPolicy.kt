package me.foxtails.palustris.domain

import java.net.URI

enum class MediaRequestRole { Preview, Full }

sealed interface MediaRequestDecision {
    data class Request(
        val url: String,
        val role: MediaRequestRole,
        val fullResourceAvailable: Boolean,
    ) : MediaRequestDecision

    data class NoRequest(val reason: MediaRequestReason) : MediaRequestDecision
}

enum class MediaRequestReason {
    HiddenSensitiveMedia,
    UnsupportedKind,
    MissingPreview,
    MissingFullResource,
    AmbiguousPreview,
    InvalidUrl,
}

object MediaRequestPolicy {
    fun resolve(
        attachment: Attachment,
        role: MediaRequestRole,
        revealed: Boolean,
        explicitlyOpened: Boolean,
    ): MediaRequestDecision {
        if (attachment.sensitive && !revealed) {
            return MediaRequestDecision.NoRequest(MediaRequestReason.HiddenSensitiveMedia)
        }
        if (attachment.kind !in setOf(MediaKind.Image, MediaKind.AnimatedImage)) {
            return MediaRequestDecision.NoRequest(MediaRequestReason.UnsupportedKind)
        }

        val preview = validWebUrl(attachment.previewUrl)
        val full = validWebUrl(attachment.url)
        val distinctPreview = preview != null && preview != full
        return when (role) {
            MediaRequestRole.Preview -> when {
                distinctPreview -> MediaRequestDecision.Request(preview, role, fullResourceAvailable = full != null)
                preview == null -> MediaRequestDecision.NoRequest(MediaRequestReason.MissingPreview)
                else -> MediaRequestDecision.NoRequest(MediaRequestReason.AmbiguousPreview)
            }
            MediaRequestRole.Full -> {
                if (!explicitlyOpened) return MediaRequestDecision.NoRequest(MediaRequestReason.MissingFullResource)
                when {
                    full != null -> MediaRequestDecision.Request(full, role, fullResourceAvailable = true)
                    preview != null -> MediaRequestDecision.Request(preview, role, fullResourceAvailable = false)
                    else -> MediaRequestDecision.NoRequest(MediaRequestReason.MissingFullResource)
                }
            }
        }
    }

    fun validWebUrl(value: String?): String? = value?.trim()?.takeIf { candidate ->
        if (candidate.isBlank() || candidate.equals("null", ignoreCase = true)) return@takeIf false
        runCatching {
            val uri = URI(candidate)
            uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}
