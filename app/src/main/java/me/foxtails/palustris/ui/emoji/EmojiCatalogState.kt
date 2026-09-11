package me.foxtails.palustris.ui.emoji

import me.foxtails.palustris.domain.CustomEmoji

/** Compose-friendly catalog state; only server-supplied picker-visible entries appear. */
data class EmojiCatalogState(
    val items: List<CustomEmoji> = emptyList(),
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val empty: Boolean = false,
    val unsupported: Boolean = false,
    val hasSnapshot: Boolean = false,
)
