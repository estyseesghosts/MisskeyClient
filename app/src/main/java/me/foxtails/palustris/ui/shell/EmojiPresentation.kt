package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.ui.emoji.EmojiCatalogState

/**
 * Emoji catalog and picker presentation shared by reaction and composer surfaces.
 *
 * The contract is read-only catalog data plus picker-preference actions. It must not carry
 * mutation transport or any post state. [Empty] is an inert preview value.
 */
data class EmojiPresentation(
    val catalog: EmojiCatalogState,
    val capabilities: EmojiCapabilities,
    val actions: Actions,
) {
    /** Catalog loading and picker-preference operations owned by the emoji catalog owner. */
    interface Actions {
        fun loadCatalog()
        fun retryCatalog()
        fun toggleGroupCollapsed(groupId: String)
        fun toggleGroupPinned(groupId: String)
        fun togglePinnedEmoji(identity: String)
    }

    companion object {
        val Empty = EmojiPresentation(EmojiCatalogState(), EmojiCapabilities(), EmojiPresentationEmptyActions)
    }
}

private object EmojiPresentationEmptyActions : EmojiPresentation.Actions {
    override fun loadCatalog() = Unit
    override fun retryCatalog() = Unit
    override fun toggleGroupCollapsed(groupId: String) = Unit
    override fun toggleGroupPinned(groupId: String) = Unit
    override fun togglePinnedEmoji(identity: String) = Unit
}
