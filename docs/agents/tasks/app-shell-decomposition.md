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

# Completed

- 01-A characterization — `ef8e8e6`.
- 01-B account switcher — `7a81eb7`.
- 01-B emoji presentation — `afc77ef`.
- 01-B notification settings — `eabbeb2`.
- 01-B saved collections — `04d31db`.
- 01-B notification inbox — `57a1753`.
- 01-B direct messages — `3816bd8`.
- 01-B profile — `5dfa74b`.
- 01-B thread — `d77e58c`.
- 01-B photo grid — `56fb1f0`.
- 01-B boundary cleanup — `5196d3a`.
- 01-D post projection coordinator — `1ce9842`, `27d2f7d`.
- 01-C post-action host — `0de4585`, `e39eace`.
- 01-B Home, Search, composer, post interactions — `b8037af`.
- 01-B reply seam and content policy removal — `32313eb`.
- 01-C draft host — `2379b44`, `c5b01b8`.
- 01-G obsolete import aliases — `d2d65c8`.
- 01-E connected-session host — `1f852c1`.
- 01-F settings overlay host and notification launch host — `7232978`.
- 01-G pass-through destination wrappers removed — `2dca95e`.

# Current slice

01-H. Documentation and ownership records are updated. Remaining 01-H work is the
test-construction rewrite. No code work is uncommitted.

# Files involved

- `app/src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt` (root composition, 158 lines)
- `app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/settings/SettingsOverlayHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/notifications/NotificationLaunchHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt`
- `app/src/main/java/me/foxtails/palustris/ui/shell/` contracts
- `docs/agents/app-shell-ownership.md`

# Verification

- `test assembleRelease` passed after `2dca95e`.
- `:app:lintDebug` passed.
- `:app:compileDebugAndroidTestKotlin` passed.
- `NotificationLaunchRouterTest` covers launch acknowledgment.
- Live-server, physical-device, and instrumentation execution remain unverified.

# Next

Optional 01-H work: move small screen scenarios to leaf or feature-host tests,
provide explicit feature fixtures, and remove the production no-argument app
construction used only by tests. Then either start `docs/decomposition_3/02.md`
or split the session host into focused feature hosts.

# Blockers

- `NotificationsViewModel`, `DirectMessageViewModel`, `NotificationSettingsViewModel`,
  and `ModerationViewModel` expose no `stop()`. Explicit teardown belongs to Plan 02.
- Account removal still does not delete account-scoped drafts. See `logs/BUGS.txt`.
- Android 15 system-bar instrumentation failure. See `logs/BUGS.txt`.

# Last safe commit

`2dca95e` "Remove pass-through destination wrappers".
