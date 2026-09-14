# Objective

Implement `docs/decomposition_3/01.md`. Give `PalustrisApp` and `ConnectedApp`
narrow, distinct responsibilities. Remove feature operations from unrelated
shell layers.

# Invariants

- Keep `MainActivity` as the Android entry point.
- Keep `ConnectedApp` for root composition.
- Keep `PalustrisApp` for navigation and layout.
- Keep protocol behavior out of the shell.
- Keep Home and Photo Grid state independent.
- Keep compact and wide navigation behavior.
- Preserve unrelated worktree changes.
- Preserve encrypted account-scoped drafts and settings.

# Decisions

- Use one immutable state snapshot and one narrow action contract for each feature.
- Group actions by ownership, not by position in the old argument list.
- Do not create one aggregate feature bag.
- Keep runtime generation, durable session revision, and notification registry generation distinct.
- Keep every committed slice usable.
- Tests bind real test-only contracts in `AppShellFixtures`. Do not add a
  production default that reports fake success.
- One origin resolves to one detail action owner through `DetailActionPolicy`.

# Completed

- 01-A characterization — `ef8e8e6`.
- 01-B contracts — `7a81eb7`, `afc77ef`, `eabbeb2`, `04d31db`, `57a1753`,
  `3816bd8`, `5dfa74b`, `d77e58c`, `56fb1f0`, `5196d3a`, `b8037af`, `32313eb`.
- 01-C post-action host — `0de4585`, `e39eace`.
- 01-C draft host — `2379b44`, `c5b01b8`.
- 01-D post projection coordinator — `1ce9842`, `27d2f7d`.
- 01-E connected-session host — `1f852c1`.
- 01-F settings overlay host and notification launch host — `7232978`.
- 01-G import aliases — `d2d65c8`.
- 01-G pass-through wrappers — `2dca95e`.
- 01-G profile editor state behind `ProfileContract` — `921d7f8`.
- 01-G shared detail action policy — `af793a8`.
- 01-G focused feature hosts — `fb9d8bf`.
- Boundary records — `fbe6c2d`.

# Current slice

All requested items are implemented. No code work is uncommitted.

# Files involved

- `app/src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt` (root composition)
- `app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt`
- Focused hosts: `ui/profile/ProfileHost.kt`, `ui/thread/ThreadHost.kt`,
  `ui/notifications/NotificationsHost.kt`,
  `ui/notifications/NotificationSettingsHost.kt`,
  `ui/directmessages/DirectMessagesHost.kt`, `ui/emoji/EmojiHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/DetailActionPolicy.kt`
- `app/src/main/java/me/foxtails/palustris/ui/settings/SettingsOverlayHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/notifications/NotificationLaunchHost.kt`
- `docs/agents/app-shell-ownership.md`

# Verification

- `test assembleRelease` passed before the item 2-4 slices.
- `test`, `:app:lintDebug`, and `:app:compileDebugAndroidTestKotlin` passed after
  the item 2-4 slices.
- `ProfileViewModelTest` covers editor draft tracking.
- `DetailActionPolicyTest` covers origin-based handler selection.
- Live-server, physical-device, and instrumentation execution remain unverified.

# Next

- Optional: extract `FeedHost` and `SavedCollectionsHost` after a feed-action handoff.
- Optional 01-H work: remove the production no-argument app construction used by tests.
- Recommended: start `docs/decomposition_3/02.md`.

# Blockers

- The feed, composer, saved collections, and post-action owner share the feed model, so those
  owners stay in the session host.
- `NotificationsViewModel`, `DirectMessageViewModel`, `NotificationSettingsViewModel`,
  and `ModerationViewModel` expose no `stop()`. Explicit teardown belongs to Plan 02.
- Account removal still does not delete account-scoped drafts. See `logs/BUGS.txt`.
- Android 15 system-bar instrumentation failure. See `logs/BUGS.txt`.

# Last safe commit

`fb9d8bf` "Split the session host into focused feature hosts".
