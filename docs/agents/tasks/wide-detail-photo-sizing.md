# Task State: Wide Detail Photo Sizing

**Status:** complete, uncommitted. The Photo Grid detail pager uses aspect-aware heights in compact and wide layouts.

**Source verified:** `ui/SinglePostScreen.kt`, `ui/photogrid/PhotoPagerSizing.kt`, `ui/AppLargeDetailPane.kt`, and `ui/large/LargeScreenShell.kt`.

**Test verified:** `SinglePostScreenTest` (23 tests, 0 failures) covers 1:1, 4:5, 16:9, wider-than-16:9, taller-than-4:5, limited viewport, missing dimensions, invalid dimensions, and shared multi-photo height. `ktlintCheck` and `test assembleRelease` pass.

**Device verified:** unavailable. Physical compact and wide rendering remains unverified.

## Objective

Remove vertical letterboxing for wide media in Photo Grid detail. Keep full pager width. Keep the post body below the media. Keep `ContentScale.Fit`. Apply the new sizing in compact and wide layouts.

## Invariants

- `ContentScale.Fit` stays. The viewport changes, not the scale mode.
- Square through 4:5 uses the natural height.
- Media at or wider than 16:9 uses the natural short height.
- Media taller than 4:5 stays capped at the 4:5 viewport.
- A short viewport clamps the height to the available space.
- Unknown or invalid dimensions use the square to 5:4 fallback.
- Multi-photo posts share one stable height. The shared height is the smallest aspect-aware height. Letterboxing on taller pages is accepted.
- No protocol branch enters generic UI or ViewModels.
- No stored format changes. No new dependency.

## Decisions

- `SinglePostScreen` derives the viewport height from its own `BoxWithConstraints`. `AppLargeDetailPane` already passes the pane height through its modifier. `LargeScreenShell` needs no change.
- The policy lives in `ui/photogrid/PhotoPagerSizing.kt` as `resolveWidePhotoPagerHeight` and `resolveSharedPhotoPagerHeight`. `SinglePostScreen.kt` stays the call site.
- `attachmentAspect` prefers full dimensions and then preview dimensions. It returns null for missing or invalid values.
- Known aspects use `pagerWidth * aspect`, capped at 1.25. Wide aspects at or below 9/16 keep the natural short height with no lower clamp.
- `available = viewport - 64.dp header - 200.dp reserved content`. A short viewport returns the available height.
- The shared height is the minimum across photos. The minimum prevents vertical letterboxing for the active photo and keeps the pager stable across pages.
- No agent ownership page changes. No architecture boundary changes.

## Files Involved

- `app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoPagerSizing.kt`
- `app/src/main/java/me/foxtails/palustris/ui/SinglePostScreen.kt`
- `app/src/test/java/me/foxtails/palustris/ui/SinglePostScreenTest.kt`
- `docs/wiki/ui-and-navigation.md`

## Verification

- `testDebugUnitTest --tests SinglePostScreenTest`: 23 tests, 0 failures.
- `ktlintCheck`: pass.
- `test assembleRelease`: pass.
- Physical device rendering stays device-dependent and unverified.

## Next

- Run the focused UI tests. Fix failures. Run the full gate. Commit one slice.
- Preserve unrelated worktree deletions and untracked files. Stage only slice files.

## Blockers

- No emulator or device is reachable.

## Last Safe Commit

- `e25c0e5145329b9396d10481557c5cd2c8edb4d4` Use square to 5:4 photo viewport in Photo Grid detail
