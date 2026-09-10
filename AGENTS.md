# MissKeyClient Agent Guide

## Project

- MissKeyClient is a single-module Android application in `:app`.
- The application supports Android 10 and later (`minSdk 29`).
- The application uses Jetpack Compose and Material 3.
- The application supports Misskey-family servers and Mastodon.
- Keep domain models and UI protocol-neutral.
- Misskey has first-class support. Mastodon remains a supported adapter.
- The namespace and application ID are `me.foxtails.palustris`.
- Treat that package name as an internal legacy identifier.
- Do not rename it casually.
- Do not add it to user-facing text.
- Do not add XML layouts, AppCompat, or Material 2 UI APIs.
- Keep new UI in Compose Material 3.
- Existing XML resources support the manifest, platform theme, and backup rules.
- Do not confuse those resources with XML layouts.
- Direct messages and alternate search remain placeholder surfaces.
- Do not describe placeholder surfaces as supported features.

## Source Of Truth

- Use source code and tests as the current architecture authority.
- `notifications_progress.md` contains obsolete pre-implementation statements.
- Do not use its missing-feature claims to describe the current notification system.
- `README.md` describes early product scope.
- Treat README TODO items as unverified until current code and tests provide evidence.

## Build

- The project has one Gradle module named `:app`.
- Use the Gradle wrapper for every build.
- Use `./gradlew` in command examples.
- Use `gradlew.bat` in Windows shells.
- The project uses Gradle 9.6.
- The project uses Android Gradle Plugin 9.4.0.
- The project uses Kotlin 2.2.10.
- The application compiles and targets SDK 37.
- The minimum SDK is 29.
- Production code targets Java 11.
- Gradle test tasks use a Java 21 launcher.
- Record the reason for every new dependency in `logs/DONE.txt`.
- Do not add AppCompat or Material 2 dependencies.

## Architecture

- `domain/` defines protocol-neutral models and contracts.
- `data/misskey/` contains Misskey transport and mapping code.
- `data/mastodon/` contains Mastodon transport and mapping code.
- `data/auth/` contains authentication and session storage.
- `data/notifications/` owns notification storage, synchronization, delivery, and push integration.
- `ui/` contains Compose screens, ViewModels, and presentation state.
- Hilt provides application-scoped services through `di/AppModule.kt`.
- Every adapter implements `SocialSource`.
- `SocialSourceFactory` creates one source for one authenticated session.
- `AccountSourceRegistry` associates a source with one account.
- `AccountManager` owns account and session state.
- `FeedViewModel` owns one account feed.
- `NotificationsViewModel` owns one account notification screen.
- `ProfileViewModel` owns one account profile session.
- `SavedPostsViewModel` owns one account saved-post session.
- `AccountSyncCoordinator` is a compatibility typealias.
- `NotificationSyncOrchestrator` is the actual notification synchronization owner.
- Do not move account ownership into Compose functions.
- Do not tie notification synchronization to `FeedViewModel` lifetime.

## Authentication And Accounts

- Detect the protocol before the protocol-specific sign-in flow starts.
- Keep protocol branches in authentication, source creation, and adapters.
- Do not add protocol branches to Compose or generic ViewModels.
- Store a server as a validated HTTPS origin.
- Reject origins with credentials, paths, queries, or fragments.
- Bind every `AccountId` to its origin, protocol, and local account identifier.
- Keep app registration credentials scoped to the connection origin.
- Keep tokens, access grants, and capabilities scoped to the account session.
- Preserve all existing sessions when adding an account.
- Preserve all other sessions during reauthentication.
- Verify the returned account before replacing an existing session.
- Increment `sessionRevision` after every successful authentication.
- Reject callbacks from older session revisions.
- Stop streams before removing an account.
- Disable push before removing an account.
- Remove notification state, post preferences, drafts, session files, and account metadata during account removal.
- Treat remote push cleanup as best effort.
- Do not block local sign-out because a server is unavailable.

## Protocol Boundary

- Use `ServerCapabilities` for feature availability.
- Keep unknown, denied, unsupported, and temporarily unavailable states separate.
- Refresh capability data through adapter capability probes.
- Do not infer unsupported behavior from one failed request.
- Normalize adapter failures to `SourceError` before they reach UI state.
- Keep cursors opaque above the adapter.
- Do not construct protocol pagination URLs in UI or ViewModels.
- Bind cursors to the account, query, direction, and protocol variant.
- Validate pagination origins before adding authentication credentials.
- Reject foreign origins, credentials, fragments, and invalid routes.
- Keep HTTP redirects disabled for authenticated requests.
- Validate entity origins before performing account actions.
- Preserve transport order when processing pagination.
- Do not compare opaque identifiers numerically, lexically, or by timestamp.

## Storage And Privacy

- Store account files under the application `noBackupFilesDir`.
- Store non-secret account metadata in `accounts/index.json`.
- Store each account session in its own encrypted file.
- Use the Android Keystore AES-GCM key for session encryption.
- Store pending authentication data in encrypted `accounts/pending.enc`.
- Store drafts in encrypted account-scoped files.
- Store non-secret post preferences in no-backup storage.
- Use `RoomNotificationStore` in production.
- Store notification data in the account-scoped Room database.
- Keep notification data separate from session secrets.
- Do not place tokens in notification storage.
- Keep account, notification, and UnifiedPush data excluded from backup and device transfer.
- Delete account-scoped notification and push state during account removal.
- Never log tokens, client secrets, authorization codes, push keys, session identifiers, or authorization headers.
- Never log complete API response bodies.
- Redact URLs, secrets, credentials, and long opaque identifiers from diagnostics.

## Notifications

- `NotificationRepository` is the single merge point for notification data.
- Bind every repository write to an account synchronization token.
- Reject writes from old account generations.
- Preserve local seen state during refetches.
- Preserve server acknowledgement state during refetches.
- Preserve Android presentation state during refetches.
- Keep notification identity separate from group identity.
- Keep account identity separate from notification identity.
- Keep local dismissal separate from server acknowledgement.
- Establish a baseline before generating delivery records.
- Do not alert for notifications imported during the initial baseline.
- Use adapter unread state instead of counting one loaded page.
- Preserve exact, lower-bound, boolean, and unknown unread precision.
- Use the actor account ID for follow-request actions.
- Never use a notification ID as a follow-request account ID.
- Treat unsupported dismissal as a local tombstone.
- Do not treat Android dismissal as server acknowledgement.

## Synchronization And Delivery

- `NotificationSyncOrchestrator` owns account synchronization.
- REST reconciliation remains the freshness authority.
- The orchestrator polls registered accounts at its configured interval.
- `ForegroundNotificationStreamController` owns sockets for the active foreground account.
- Reconcile after a stream closes or reports an incomplete event.
- Use bounded backoff for stream failures.
- Treat unsupported streams as a terminal stream state.
- Use WorkManager for reconciliation, catch-up, delivery, and registration work.
- Re-read the session and generation when background work starts.
- Use unique work per account.
- Use a random per-account UnifiedPush instance name.
- Never use an access token as a UnifiedPush instance name.
- Validate the callback owner, account, session revision, and endpoint generation.
- Define `Connected` only after distributor and server registration succeed.
- Treat endpoint rotation as a new registration generation.
- Remove an old server subscription only after the replacement succeeds.
- Misskey push payloads can contain notification, read, chat, or refresh events.
- Mastodon push callbacks currently schedule authenticated catch-up.
- Do not treat every push callback as a complete notification.
- Misskey secure push endpoints can reject MiAuth or application credentials.
- Handle `SourceError.UnsupportedCredential` as a terminal push result.
- Keep REST and foreground stream fallback available after push rejection.
- Keep presentation policy separate from notification ingestion.
- Apply account settings, category filters, permission state, and quiet hours in `NotificationDeliveryPlanner`.
- Use stable account-bound notification tags and IDs.
- Keep notification previews privacy-safe.
- Use immutable account-bound notification intents.
- Route notification launches to the receiving account.
- Show an account-unavailable state when that account no longer exists.

## Compose Layout

- Keep the compact navigation pill visible on Home, Search, Search placeholders, Notifications, and Profile.
- Use the compact navigation pill as the shared group surface.
- Give the timeline selector its own surface.
- Give notification and profile action controls their own surfaces.
- Give the search field its own surface.
- Give filter chips their own surfaces.
- Keep the row behind floating controls transparent.
- Allow selected and pressed states to add their own filled surface.
- Extend compact page viewports behind floating controls.
- Put obstruction clearance inside scroll content.
- Use `LazyColumn.contentPadding` or an in-scroll trailing spacer for clearance.
- Do not pad or inset the full-screen content to reserve floating controls.
- Keep the final scroll item reachable above every floating control.
- Preserve live IME positioning for compact search controls.
- Preserve wide-layout navigation rail behavior.
- Test both visible underlap and final-item reachability.

## Coding Rules

- Follow the Kotlin official coding conventions.
- Prefer imports over fully qualified names.
- Avoid wildcard imports.
- Keep related declarations together.
- One public declaration per file is not required.
- Use one technical name for one concept.
- Preserve exact code identifiers when documenting APIs.
- Keep comments short and explain only non-obvious behavior.
- Preserve unrelated worktree changes.
- Do not revert user changes.
- Do not commit unless the user explicitly requests a commit.

## Task Logs

- For coding tasks, create `logs/YYMMDD-HHMMSS.txt` at task start.
- Record intended steps and affected files in the task log.
- Track active work in `logs/TODO.txt`.
- Remove completed entries from `logs/TODO.txt`.
- Append concise results to `logs/DONE.txt`.
- Record failures and unresolved concerns in `logs/BUGS.txt`.
- Keep logs free of secrets and complete response bodies.
- If the user forbids file changes, you are not permitted to edit code. You are only permitted to edit log files. 

## Verification

- Run focused unit tests with `./gradlew :app:testDebugUnitTest --tests <fully-qualified-test-class>`.
- Run `./gradlew lintDebug` after authentication, storage, adapter, contract, or notification changes.
- Run `./gradlew test assembleRelease` before declaring a coding task complete.
- Run Compose layout tests after changing floating controls or scroll clearance.
- Review `HomeFeedTest`, `NavigationTest`, `NotificationsScreenTest`, and `ProfileScreenTest` for underlap coverage.
- Review `WideNavigationTest` after changing wide-layout navigation.
- Review account lifecycle tests after changing sessions or account removal.
- Use mocked HTTP tests to verify adapter contracts and origin validation.
- Use instrumented tests to verify Android-only behavior.
- Do not treat mocked tests as proof of live server behavior.
- Do not treat local notification tests as proof of UnifiedPush delivery.
- Do not treat Compose tests as proof of device rendering.
- Do not treat authentication tests as proof of browser callback behavior.
- Record unverified live-server, push, browser, and device checks in the final handoff.
```
