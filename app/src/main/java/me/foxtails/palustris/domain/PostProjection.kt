package me.foxtails.palustris.domain

/**
 * Whether an external projection carries complete reaction state.
 *
 * A projection can omit reactions. An empty list alone is ambiguous: it can mean
 * "this projection has no reaction data" or "the server confirmed every reaction was
 * removed". A known [PostInteractionCounts.reactionCount] disambiguates the two.
 */
fun Post.hasAuthoritativeReactionState(): Boolean =
    reactions.isNotEmpty() || interactionCounts.reactionCount != null

/**
 * Merges the action fields an external projection may own into this post.
 *
 * Reaction fields are taken only when [hasAuthoritativeReactionState] is true, so a
 * confirmed removal of the last reaction can clear old chips while a partial adapter
 * response cannot erase confirmed local reaction state. Every other action field is
 * taken directly. All collection surfaces share this rule so they agree.
 */
fun Post.mergeExternalActionFields(incoming: Post): Post = copy(
    favourited = incoming.favourited,
    myReaction = if (incoming.hasAuthoritativeReactionState()) incoming.myReaction else myReaction,
    selectedReactions = if (incoming.hasAuthoritativeReactionState()) {
        incoming.selectedReactions
    } else {
        selectedReactions
    },
    reactions = if (incoming.hasAuthoritativeReactionState()) incoming.reactions else reactions,
    reposted = incoming.reposted,
    interactionCounts = interactionCounts.merge(incoming.interactionCounts),
    ownRepostId = incoming.ownRepostId,
    saved = incoming.saved,
)
