# Task State: Wide Detail Photo Sizing

**Status:** in progress. The Photo Grid detail photo pager uses the
detail viewport instead of the fixed 4:3 ratio in compact and wide layouts.

**Source verified:** `ui/SinglePostScreen.kt`,
`ui/photogrid/PhotoPagerSizing.kt`, `ui/AppLargeDetailPane.kt`, and
`ui/large/LargeScreenShell.kt`.

**Test verified:** `SinglePostScreenTest` (13 tests, 0 failures) covers wide
5:4, wide clamp, and compact tall-viewport cases. `ktlintCheck` and
`test assembleRelease` pass.

**Device verified:** unavailable. Physical wide-layout rendering remains
unverified.

## Objective

Remove horizontal letterboxing for square media in Photo Grid detail.
Keep full pager width. Keep the post body below the media. Keep
`ContentScale.Fit`. Apply the new sizing in compact and wide layouts.

## Invariants

- `ContentScale.Fit` stays. The viewport changes, not the scale mode.
- The legacy `(width * 0.75).coerceAtLeast(240.dp)` path remains only as a
  fallback for an unknown viewport height.
- No protocol branch enters generic UI or ViewModels.
- No stored format changes. No new dependency.

## Decisions

- `SinglePostScreen` derives the viewport height from its own
  `BoxWithConstraints`. `AppLargeDetailPane` already passes the pane height
  through its modifier. `LargeScreenShell` needs no change.
- Photo Grid presentation always uses the viewport sizing in compact and
  wide layouts. The `embedded` flag no longer selects the sizing path.
- The policy lives in `ui/photogrid/PhotoPagerSizing.kt` as
  `resolveWidePhotoPagerHeight`. `SinglePostScreen.kt` stays the call site.
- The rule is `desired = width * 1.25`, `minimum = width`,
  `available = viewport - 64.dp header - 200.dp reserved content`,
  `actual = clamp(desired, minimum, available)`. A short pane uses the
  largest safe height.
- No agent ownership page changes. No architecture boundary changes.

## Files Involved

- `app/src/main/java/me/foxtails/palustris/ui/SinglePostScreen.kt`
- `app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoPagerSizing.kt`
- `app/src/test/java/me/foxtails/palustris/ui/SinglePostScreenTest.kt`
- `docs/wiki/ui-and-navigation.md`

## Verification

- `testDebugUnitTest --tests SinglePostScreenTest`: 13 tests, 0 failures.
- `ktlintCheck`: pass.
- `test assembleRelease`: pass.
- Physical device rendering stays device-dependent and unverified.

## Next

Run the focused UI tests. Fix failures. Run the full gate. Commit one slice.

## Blockers

- No emulator or device is reachable.

## Last Safe Commit

- `f3454fc273f63be033176a656be1acb9eb863a4b` Sync Photo Grid wide detail state
