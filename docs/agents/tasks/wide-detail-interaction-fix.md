# Task State: Wide Detail Interaction Fix

**Status:** complete. Detail surfaces now use the shared interaction callbacks.

**Source verified:** `ui/posts/PostRow.kt` and `ui/SinglePostScreen.kt`.

**Test verified:** `SinglePostScreenTest.photoPostDetailRendersUpdatedInteractionState`,
`test`, `assembleRelease`, `lintDebug`, and
`ktlintCheck` pass after callback wiring changes.

**Device verified:** unavailable. Physical wide-layout interaction remains unverified.

The interaction row owns its default `PostRepostConfirmationOwner` request. Feed callers no
longer provide a duplicate forwarding lambda. Detail surfaces now receive the same reaction
bubble and picker callbacks as feed rows. Detail mutations use the shared feed interaction owner,
so optimistic state reaches Photo Grid and thread projections immediately. Selected icon tint
remains the shared primary accent.
