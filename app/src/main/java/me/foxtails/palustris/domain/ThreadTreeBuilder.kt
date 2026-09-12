package me.foxtails.palustris.domain

/** Builds a flat reply presentation without trusting transport adjacency or recursion depth. */
object ThreadTreeBuilder {
    fun build(
        focal: OwnedPost,
        ancestors: List<OwnedPost> = emptyList(),
        descendants: List<OwnedPost> = emptyList(),
    ): ThreadTree {
        val connection = focal.post.id.connection
        val focalId = focal.post.id
        val ordered = buildList {
            add(focal)
            addAll(ancestors)
            addAll(descendants)
        }
            .filter { it.post.id.connection == connection }
            .distinctBy { it.post.id }
        val byId = ordered.associateBy { it.post.id }
        val ancestorIds = ancestors.asSequence()
            .filter { it.post.id.connection == connection }
            .map { it.post.id }
            .toSet()
        val replyIds = descendants.asSequence()
            .filter { it.post.id.connection == connection }
            .map { it.post.id }
            .filter { it != focalId && it !in ancestorIds }
            .distinct()
            .toList()
        val parentById = replyIds.associateWith { id ->
            val post = byId.getValue(id).post
            post.replyTo?.takeIf { it != id && it in byId }
        }.toMutableMap()
        breakCycles(parentById, focalId)

        val children = parentById.entries.groupBy({ it.value }, { it.key })
        fun row(id: EntityId, parentId: EntityId?, depth: Int, branchId: String, missing: Boolean): ThreadRow {
            val siblings = children[parentId].orEmpty()
            return ThreadRow(
                ownedPost = byId.getValue(id),
                parentId = parentId,
                semanticDepth = depth,
                branchId = branchId,
                connector = ThreadConnectorInfo(
                    verifiedParent = parentId != null && !missing,
                    hasFollowingSibling = siblings.indexOf(id) < siblings.lastIndex,
                ),
                parentMissing = missing,
                stableKey = stablePostKey(byId.getValue(id)),
            )
        }

        val roots = replyIds.filter { id ->
            val parent = parentById[id]
            parent == focalId || parent == null
        }
        val visited = linkedSetOf<EntityId>()
        val connected = mutableListOf<ThreadRow>()
        val disconnected = mutableListOf<ThreadRow>()
        val disconnectedBranches = mutableSetOf<String>()
        val queue = ArrayDeque<Triple<EntityId, Int, String>>()
        roots.forEachIndexed { index, id ->
            val parent = parentById[id]
            if (parent == null) disconnectedBranches += "branch-$index"
        }
        roots.asReversed().forEachIndexed { reverseIndex, id ->
            val index = roots.lastIndex - reverseIndex
            val parent = parentById[id]
            queue.addFirst(Triple(id, if (parent == focalId) 1 else 0, "branch-$index"))
        }
        while (queue.isNotEmpty()) {
            val (id, depth, branch) = queue.removeFirst()
            if (!visited.add(id)) continue
            val parent = parentById[id]
            val missing = parent == null && byId.getValue(id).post.replyTo != null
            val built = row(id, parent, depth, branch, missing)
            if (branch in disconnectedBranches) disconnected += built else connected += built
            children[id].orEmpty().asReversed().forEach { child ->
                queue.addFirst(Triple(child, depth + 1, branch))
            }
        }
        replyIds.filterNot { it in visited }.forEach { id ->
            val built = row(id, null, 0, "branch-${disconnected.size}", missing = true)
            if (visited.add(id)) disconnected += built
        }

        val ancestorRows = ancestors.asSequence()
            .filter { it.post.id.connection == connection && it.post.id != focalId }
            .distinctBy { it.post.id }
            .mapIndexed { index, owned ->
                val parent = owned.post.replyTo?.takeIf { it in byId }
                ThreadRow(
                    ownedPost = owned,
                    parentId = parent,
                    semanticDepth = index,
                    branchId = "ancestor",
                    connector = ThreadConnectorInfo(parent != null, index < ancestors.lastIndex),
                    parentMissing = owned.post.replyTo != null && parent == null,
                    stableKey = stablePostKey(owned),
                )
            }.toList()
        return ThreadTree(ancestorRows, connected, disconnected)
    }

    fun build(
        focal: Post,
        ancestors: List<Post> = emptyList(),
        descendants: List<Post> = emptyList(),
    ): ThreadTree = build(
        OwnedPost(focal.author.id, focal),
        ancestors.map { OwnedPost(it.author.id, it) },
        descendants.map { OwnedPost(it.author.id, it) },
    )

    private fun breakCycles(parentById: MutableMap<EntityId, EntityId?>, focalId: EntityId) {
        parentById.keys.forEach { start ->
            val seen = mutableSetOf<EntityId>()
            var current: EntityId? = start
            while (current != null && current != focalId) {
                if (!seen.add(current)) {
                    parentById[current] = null
                    break
                }
                current = parentById[current]
            }
        }
    }

    private fun stablePostKey(owned: OwnedPost): String = buildString {
        append("post:")
        append(owned.fetchedBy.connection.origin)
        append(':')
        append(owned.fetchedBy.localId)
        append(':')
        append(owned.sessionRevision)
        append(':')
        append(owned.post.id.connection)
        append(':')
        append(owned.post.id.value)
    }
}
