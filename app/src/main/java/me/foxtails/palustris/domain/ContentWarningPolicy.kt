package me.foxtails.palustris.domain

enum class ContentWarningDecision {
    Normal,
    Collapsed,
    ExpandedByDefault,
    Hidden,
}

/** Applies server visibility first, then local hide rules, then local expansion rules. */
object ContentWarningPolicy {
    fun matchesHashtagMute(hashtags: Collection<String>, mutedHashtags: Collection<String>): Boolean {
        if (hashtags.isEmpty() || mutedHashtags.isEmpty()) return false
        val normalizedHashtags = hashtags.map { it.trim().removePrefix("#").lowercase() }.toSet()
        return mutedHashtags.any { it.trim().removePrefix("#").lowercase() in normalizedHashtags }
    }

    fun decide(
        warning: String?,
        hashtags: Collection<String> = emptyList(),
        rules: ContentWarningRules = ContentWarningRules(),
        serverVisibility: PostContentVisibility = PostContentVisibility.Visible,
        bodyText: String = "",
    ): ContentWarningDecision {
        if (serverVisibility != PostContentVisibility.Visible) return ContentWarningDecision.Hidden
        if (warning == null) return ContentWarningDecision.Normal
        val normalizedRules = rules.normalized()
        val warningText = warning.trim()
        val body = bodyText.trim()
        val normalizedHashtags = hashtags.map { it.trim().removePrefix("#").lowercase() }.toSet()
        val hide = normalizedRules.hideAll ||
            normalizedRules.hideKeywords.any { warningText.contains(it, ignoreCase = true) || body.contains(it, ignoreCase = true) } ||
            normalizedRules.hideHashtags.any { it in normalizedHashtags }
        if (hide) return ContentWarningDecision.Hidden
        val expand = normalizedRules.expandAll ||
            normalizedRules.expandKeywords.any { warningText.contains(it, ignoreCase = true) || body.contains(it, ignoreCase = true) } ||
            normalizedRules.expandHashtags.any { it in normalizedHashtags }
        return if (expand) ContentWarningDecision.ExpandedByDefault else ContentWarningDecision.Collapsed
    }
}
