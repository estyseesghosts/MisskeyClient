# Task State: Wide Detail Interaction Fix

**Status:** complete. Photo Grid wide detail now keeps its navigator snapshot aligned with the
thread-owned focal post after optimistic interaction updates.

**Source verified:** `ui/PalustrisApp.kt`, `ui/AppLargeDetailPane.kt`, `ui/SinglePostScreen.kt`,
`ui/posts/PostRow.kt`, `ui/photogrid/PhotoGridController.kt`, and
`ui/thread/PostThreadViewModel.kt`.

**Test verified:** `DetailActionPolicyTest`, `PostThreadViewModelTest`, and
`SinglePostScreenTest.wideMisskeyDetailRendersAllUpdatedInteractionStates` pass. The focused detail
test task passes.

**Device verified:** unavailable. Physical wide-layout interaction remains unverified.

The interaction row owns its default `PostRepostConfirmationOwner` request. Photo Grid wide detail
uses the active `PostThreadViewModel` for focal mutations and synchronizes the matching navigator
snapshot from the thread focal state after recomposition.
The thread owner emits accepted updates to the coordinator, so feed projections remain shared.
Compact detail keeps its existing feed owner because it renders the selected navigation snapshot.
The defect was stale state competition in `ui/PalustrisApp.kt`: Photo Grid wide detail could retain
the navigator snapshot while the thread owner held the optimistic update. The fix keeps the thread
owner bound for the full wide-detail lifetime and replaces only a matching Photo Grid snapshot.
The Misskey rendering regression confirms that updated interaction fields change the selected
control state after recomposition.
Physical device and live-server behavior remain unverified.
