package me.foxtails.palustris.ui.thread

import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ThreadContinuation
import me.foxtails.palustris.domain.ThreadLimitation
import me.foxtails.palustris.domain.ThreadRow
import me.foxtails.palustris.domain.ThreadRefreshHint

enum class PostThreadPhase {
    Inactive,
    InitialLoading,
    Content,
    Refreshing,
    Continuing,
    Partial,
    InitialFailure,
    AccountUnavailable,
}

data class PostThreadUiState(
    val phase: PostThreadPhase = PostThreadPhase.Inactive,
    val focal: OwnedPost? = null,
    val ancestors: List<OwnedPost> = emptyList(),
    val rows: List<ThreadRow> = emptyList(),
    val disconnectedRows: List<ThreadRow> = emptyList(),
    val continuation: ThreadContinuation? = null,
    val limitations: List<ThreadLimitation> = emptyList(),
    val refreshHint: ThreadRefreshHint? = null,
    val error: SourceError? = null,
    val pendingActions: Set<String> = emptySet(),
) {
    val loading: Boolean get() = phase == PostThreadPhase.InitialLoading
    val refreshing: Boolean get() = phase == PostThreadPhase.Refreshing
    val continuing: Boolean get() = phase == PostThreadPhase.Continuing
}
