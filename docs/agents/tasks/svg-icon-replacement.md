# SVG Icon Replacement Pass

**Status:** implemented, committed. Verification (unit tests, lint, device) is owned by a parallel agent.

This task is larger than one safe implementation slice. It divides into: (a) central icon library, (b) interaction icons, (c) navigation icons, (d) link/hashtag/follow/user assets, (e) string-literal sweep of touched files. All slices are implemented in a single commit because the icon library is only exercised through its call sites.

## Scope Decision

- Source artwork: `appsvg/` folder in the repo root (untracked supplier drop). Path data is embedded in `ui/BeelineSvgPaths.kt`; no redraws, no substitute icons.
- Stars show Mastodon favourite state ONLY. Hearts show like and reaction state on every other service, including Misskey.
- `defaultuser.svg` is a generic user symbol only, never a profile picture. `defaultusericon.svg` is the profile-picture fallback, drawn with `Color.Unspecified` to keep its yellow backing.
- `unfollow?` confirmation text lives in `strings.xml` (`profile_unfollow_confirm`). No string literals in new code.

## Invariants

- Behaviour, state handling, accessibility labels, and click targets are preserved. Only iconography and the explicitly requested unfollow confirmation step change.
- Hashtag `#` is replaced by the logomark only inside clickable hashtag bubbles, never in plain post text.
- Protocol branches stay in UI-adjacent selection only (`favouriteIconFor`); shared domain models are untouched.
- Old `AppIcons` entries are kept so unrelated call sites keep working.

## Verification Limits

- `testDebugUnitTest` was run once during implementation: 22 tests, 1 failure in `ProfileScreenTest.remoteRelationshipActionReflectsAuthoritativeFollowRequestAndUnfollowStates`, caused by the new two-step unfollow confirmation. Left for the parallel testing agent.
- No lint run, no device run, no live-server run by this task.
