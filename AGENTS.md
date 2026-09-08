# AGENTS.md for MisskeyClient

## Project

- MisskeyClient is an Android 13+ Jetpack Compose/Material 3 client for both Misskey-family servers and Mastodon.
- Keep the domain and UI protocol-agnostic. Misskey has first-class support, while Mastodon remains a supported adapter.
- The package `me.foxtails.palustris` and existing legacy identifiers are internal leftovers; do not rename them casually or add that name to new user-facing strings.
- Do not add XML layouts, AppCompat, or Material 2 themes.
- Design guideline — floating surfaces: never place a full-width or row-sized solid/translucent background behind a group of floating controls. The compact navigation pill itself is the one shared group surface and must appear consistently on Home, Search (including placeholder Search), Notifications, and Profile. Other floating controls—the notification/profile chips, search field, and timeline/feed switcher—must each render their own background surface; the row/container behind them must remain transparent. Selected and pressed states may add their own filled affordance.
- A transparent overlay container is not sufficient if the page viewport is padded or inset to stop above it. Compact page viewports must extend behind floating controls. Put obstruction clearance inside scroll content (`LazyColumn.contentPadding` or an in-scroll trailing spacer), never on the full-screen content modifier. Tests must prove both visible underlap and final-item reachability.

## Account and protocol invariants

- Every adapter implements `SocialSource`; UI and ViewModels must not depend directly on Misskey or Mastodon adapters.
- Detect protocols in authentication/capability code, not in Compose. Prefer `ServerCapabilities` over protocol-name branches for feature availability.
- Normalize adapter failures to `SourceError` before they reach UI state.
- Keep cursors opaque above the adapter. UI and ViewModels must not construct protocol-specific pagination URLs.
- Validate pagination origins before attaching an account's credentials to authenticated follow-up requests.
- App registrations are connection/origin-scoped; session tokens and capabilities are account-scoped.
- Sessions are encrypted per account. `accounts/index.json` contains non-secret account metadata, and `accounts/pending.enc` contains only pending authentication state.
- Adding or reauthenticating an account must preserve all other sessions. Signing out must remove that account's stored and in-memory state and stop its polling/source work.
- The package currently uses `AccountManager` for account/session state, `FeedViewModel` for one account's feed, and `AccountSyncCoordinator` for supervised account sync. Preserve those boundaries.

## Logging and task handoff

- At the start of each task, create `logs/YYMMDD-HHMMSS.txt` and record the intended steps and files.
- Track active work in `logs/TODO.txt`; remove completed entries and append a concise result to `logs/DONE.txt`.
- Record failures or unresolved concerns in `logs/BUGS.txt`.
- Logs are gitignored but are not secret-safe: never write tokens, client secrets, MiAuth session IDs, authorization headers, or full API response bodies. Redact sensitive values in any other notes, comments, commit messages, or review text too.

## Coding and dependency policy

- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Prefer imports over fully qualified names and avoid wildcard imports.
- Keep closely related declarations together; one public declaration per file is not required.
- Do not add dependencies without recording the reason in `logs/DONE.txt`.

## Verification

- For focused Android unit tests, use `./gradlew :app:testDebugUnitTest --tests <fully-qualified-test-class>`.
- After auth, session, storage, adapter, or contract changes, run the relevant focused tests and `./gradlew lintDebug`.
- Before marking a task complete, run `./gradlew test assembleRelease`.
- Mocked tests do not prove device rendering, browser callbacks, playback, or authenticated-server behavior. State any unverified runtime checks in the completion log and final handoff.
- Preserve unrelated worktree changes. Make a focused commit after successful validation when repository permissions and task scope allow it.
