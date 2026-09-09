package me.foxtails.palustris.ui.emoji

import me.foxtails.palustris.domain.CustomEmoji

/** Compose-friendly catalog snapshot; only server-supplied picker-visible entries appear. */
data class EmojiCatalogState(
    val items: List<CustomEmoji> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val empty: Boolean = false,
    val unsupported: Boolean = false,
)
