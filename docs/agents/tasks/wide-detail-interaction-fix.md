# Task State: Wide Detail Interaction Fix

**Status:** complete. The PhotoGrid detail now uses the shared repost confirmation default.

**Source verified:** `ui/posts/PostRow.kt` and `ui/SinglePostScreen.kt`.

**Test verified:** `SinglePostScreenTest.photoPostDetailShowsRepostConfirmation` passes.

**Device verified:** unavailable. Physical wide-layout interaction remains unverified.

The interaction row owns its default `PostRepostConfirmationOwner` request. Feed callers no
longer provide a duplicate forwarding lambda. The PhotoGrid detail therefore shows the same
confirmation surface as feed rows.
