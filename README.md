# Palustris UI prototype

A Kotlin / Jetpack Compose Android shell inspired by Moshidon's navigation and screen structure. Runs offline with empty screens; no login, HTTP client, server connection, or sample feed. The manifest deliberately has no internet permission.

## Run

Open this folder in Android Studio and run `app` on Android 13 or newer. The project uses the installed Android 37 SDK and Gradle's Java 25 toolchain.

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. Local UI tests use Robolectric (Android 35) and write review images to `app/build/ui-screenshots/`; a device is not required.

## Screens

- Home: Moshidon-style title/timeline chooser, overflow menu, and compose floating action button.
- Search: rounded search field with Posts, Hashtags, News, and For you tabs.
- Notifications: All and Mentions tabs.
- Profile: 144 dp cover area, overlapping rounded avatar, account controls, follower counts, and Posts/Replies/Media/About tabs.
- Compose: local text editing, optional content warning, draft saving, and unsaved-change protection. Publishing is disabled. One draft is persisted on-device; opening compose resumes it.
- Bookmarks, drafts, and account sheet with explicit empty states.

Uses Material 3 components, standard typography, wallpaper-derived dynamic colors, system light/dark mode, edge-to-edge insets, and a navigation rail at 600 dp and above. Each main tab retains its local UI state. All icons are bundled vectors.

## API boundary

`domain/SocialModels.kt` is independent of Android, Compose, and either server's transport schema. An eventual Mastodon or Misskey adapter should implement `SocialSource` and map its DTOs into these shared models. Nothing implements or invokes that boundary yet.

- IDs include a connection scope and an opaque value. IDs from different servers cannot collide, and no code assumes numeric IDs.
- Pagination cursors stay opaque; adapters own server-specific paging semantics.
- Timelines, audience, reactions, reshares, attachments, and content warnings use neutral concepts.
- `ServerCapabilities` starts with no capabilities. Connected UI must use the adapter's supported timelines, audiences, actions, publishing support, and optional character limit. The offline timeline selector currently previews the three common layout choices; it is not a claim of server support.
- `Post.availableActions` supports permissions varying by post as well as server.
- No fixed Mastodon character limit or API-specific authentication assumptions are built into the composer.

The source boundary is intentionally small. Add account, notification, search, and mutation contracts when those features are implemented rather than inventing transport behavior now. UI strings are currently English. Profile, feed, media, search results, and notifications are empty placeholders; this phase does not implement post rendering.

## Design references inspected

The sibling Moshidon checkout was used as a structural reference, especially `HomeFragment.java`, `DiscoverFragment.java`, `home_toolbar.xml`, `tab_bar.xml`, `fragment_profile.xml`, `fragment_compose.xml`, and the `display_item_*` post layouts. The four root screenshot files were reviewed for placement and hierarchy. Their custom font and colors are deliberately not reproduced. No Moshidon source or image assets were copied.

Material guidance: https://developer.android.com/develop/ui/compose/designsystems/material3
