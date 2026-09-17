# Task State: Wide Detail Interaction Fix

**Status:** complete. Wide detail routes focal mutations through the owner that renders it.

**Source verified:** `ui/posts/PostRow.kt` and `ui/SinglePostScreen.kt`.

**Test verified:** `DetailActionPolicyTest.activeThreadOwnsWideDetailMutations` and
`PostThreadViewModelTest.wideMastodonDetailMutationsUpdateTheFocalPostAndRollbackOnFailure`,
focused unit tests, the full unit test suite, release build, lint, and ktlint pass.

**Device verified:** unavailable. Physical wide-layout interaction remains unverified.

The interaction row owns its default `PostRepostConfirmationOwner` request. Wide detail uses the
active `PostThreadViewModel` for focal mutations because it renders `selectedThreadState.focal`.
The thread owner emits accepted updates to the coordinator, so feed projections remain shared.
Compact detail keeps its existing feed owner because it renders the selected navigation snapshot.
The focused tests verify Mastodon-shaped source operations, immediate focal state, and rollback.
Physical device and live-server behavior remain unverified.
