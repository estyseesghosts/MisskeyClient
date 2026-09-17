# Task State: Wide Detail Interaction Fix

**Status:** complete. Wide detail selects the thread mutation owner for its complete single-post
lifetime, including the initial thread-loading composition.

**Source verified:** `ui/PalustrisApp.kt`, `ui/AppLargeDetailPane.kt`, `ui/SinglePostScreen.kt`,
`ui/posts/PostRow.kt`, and `ui/thread/PostThreadViewModel.kt`.

**Test verified:** `DetailActionPolicyTest`, `PostThreadViewModelTest`, and
`SinglePostScreenTest.wideMisskeyDetailRendersAllUpdatedInteractionStates` pass. The focused detail
test task passes.

**Device verified:** unavailable. Physical wide-layout interaction remains unverified.

The interaction row owns its default `PostRepostConfirmationOwner` request. Wide detail uses the
active `PostThreadViewModel` for focal mutations because it renders `selectedThreadState.focal`.
The thread owner emits accepted updates to the coordinator, so feed projections remain shared.
Compact detail keeps its existing feed owner because it renders the selected navigation snapshot.
The defect was a timing-dependent owner selection in `ui/PalustrisApp.kt`: wide detail used the
collection owner until a matching thread state existed. The fix keeps the thread owner bound for
the full wide-detail lifetime. The Misskey rendering regression confirms that updated interaction
fields change the selected control state after recomposition.
Physical device and live-server behavior remain unverified.
