package me.foxtails.palustris.domain

import java.net.URI

enum class MediaRequestRole { Preview, Full }

/**
 * A validated custom-emoji image target. Emoji requests are credential-free, HTTPS-only,
 * and resolved against the owning origin before validation; redirects are reviewed by the
 * shared image loader during decoding.
 */
data class EmojiImageRequest(
    val url: ValidatedUrl,
    val static: Boolean,
)

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

    /** Static images load first; animated URLs are the fallback when no static image exists. */
    fun emojiImage(emoji: CustomEmoji): EmojiImageRequest? {
        val static = emoji.staticUrl
        val animated = emoji.animatedUrl
        return when {
            static != null -> EmojiImageRequest(static, static = true)
            animated != null -> EmojiImageRequest(animated, static = false)
            else -> null
        }
    }

    /**
     * Normalizes absolute and origin-relative image URLs into credential-free HTTPS
     * resources. Credential-bearing, malformed, and non-HTTPS URLs are rejected.
     */
    fun validatedWebUrl(candidate: String?, origin: String? = null): ValidatedUrl? {
        val trimmed = candidate?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: return null
        return runCatching {
            val resolved = if (origin != null && !trimmed.startsWith("http://", ignoreCase = true) &&
                !trimmed.startsWith("https://", ignoreCase = true)
            ) {
                URI(origin).resolve(trimmed).toString()
            } else {
                trimmed
            }
            ValidatedUrl.https(resolved)
        }.getOrNull()
    }
}
