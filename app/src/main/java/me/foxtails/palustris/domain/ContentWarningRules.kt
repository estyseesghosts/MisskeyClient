package me.foxtails.palustris.domain

/** Local-only rules for posts that already carry a server content warning. */
data class ContentWarningRules(
    val hideAll: Boolean = false,
    val expandAll: Boolean = false,
    val hideKeywords: List<String> = emptyList(),
    val hideHashtags: List<String> = emptyList(),
    val expandKeywords: List<String> = emptyList(),
    val expandHashtags: List<String> = emptyList(),
) {
    fun normalized(): ContentWarningRules = copy(
        hideKeywords = normalizeTerms(hideKeywords),
        hideHashtags = normalizeHashtags(hideHashtags),
        expandKeywords = normalizeTerms(expandKeywords),
        expandHashtags = normalizeHashtags(expandHashtags),
    )

    fun merge(accountRules: ContentWarningRules): ContentWarningRules = ContentWarningRules(
        hideAll = hideAll || accountRules.hideAll,
        expandAll = expandAll || accountRules.expandAll,
        hideKeywords = hideKeywords + accountRules.hideKeywords,
        hideHashtags = hideHashtags + accountRules.hideHashtags,
        expandKeywords = expandKeywords + accountRules.expandKeywords,
        expandHashtags = expandHashtags + accountRules.expandHashtags,
    ).normalized()
}

private fun normalizeTerms(values: List<String>): List<String> = values
    .asSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(String::lowercase)
    .take(100)
    .toList()

private fun normalizeHashtags(values: List<String>): List<String> = values
    .asSequence()
    .map { it.trim().removePrefix("#") }
    .filter(String::isNotEmpty)
    .map(String::lowercase)
    .distinct()
    .take(100)
    .toList()
