package me.foxtails.palustris.domain

/** Selects a visibility once for a composition and never overwrites an explicit choice. */
object PostingVisibilityPolicy {
    fun forNewPost(preferences: PostPreferences, capabilities: ServerCapabilities): Audience =
        supportedOrThrow(preferences.defaultAudience, capabilities)

    fun forReply(preferences: PostPreferences, capabilities: ServerCapabilities, context: Audience? = null): Audience {
        val requested = if (preferences.repliesUnlisted) Audience.Unlisted else preferences.defaultAudience
        val contextual = context?.let(::moreRestrictive)
        return supportedOrThrow(contextual ?: requested, capabilities)
    }

    fun validateExplicit(audience: Audience, capabilities: ServerCapabilities): Audience =
        supportedOrThrow(audience, capabilities)

    private fun supportedOrThrow(audience: Audience, capabilities: ServerCapabilities): Audience {
        if (audience !in capabilities.audiences) throw SourceError.Unsupported("audience:${audience.name}")
        return audience
    }

    private fun moreRestrictive(audience: Audience): Audience = when (audience) {
        Audience.Direct -> Audience.Direct
        Audience.Followers -> Audience.Followers
        Audience.Unlisted -> Audience.Unlisted
        Audience.Public -> Audience.Unlisted
    }
}
