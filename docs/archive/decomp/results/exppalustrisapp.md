**Current Ownership**

`PalustrisApp.kt` owns:

- Root destination and transition state.
- Timeline, Search, Photo Grid, and Direct Messages panel state.
- Local pages and notification routes.
- Composer fields, draft persistence, and publish lifecycle.
- Viewed profile and profile editor state.
- Media, profile image, and selected-post state.
- Thread activation effects.
- Emoji picker and post-action bubble state.
- Sheets, dialogs, compact navigation, and wide navigation.
- Destination rendering and modal composition.

`AppShellState.kt` contains stable identities and pure mappings:

- `Destination`
- `NotificationsPanel`
- `LocalPage`
- `SearchPanel`
- `LargePostOrigin`
- `Overlay`
- `largeTargetFor`
- `LargePostOrigin.supportsComments`
- `LargePostOrigin.singlePostPresentation`
- Collection-title and timeline-label mappings
- `editableProfilePatch`

Do not move ownership into `AppShellState.kt`. It should remain a small state-definition file.

**Saveable State**

`PalustrisApp.kt` uses `rememberSaveable` for:

- `destination`
- `destinationTransitionDirection`
- `timeline`
- `page`
- `sheet`
- `overlayKey`
- `searchPanelName`
- `searchQuery`
- `searchCategory`
- `searchPrefill`
- `notificationsPanelName`
- `draftId`
- `draft`
- `savedDraft`
- `warning`
- `savedWarning`
- `warningEnabled`
- `composerAudience`
- `savedAudience`
- `draftError`
- `profileDialog`
- `navigationVisible`

These calls do not define explicit keys. Their declaration order and composition location therefore create restoration risk.

The following state uses ordinary `remember`:

- `drafts`
- `savedQuoteOf`
- `composerReplyTo`
- `savedReplyTo`
- `composerQuoteOf`
- `composerTarget`
- `viewedProfile`
- `profileEditor`
- `signOutDialog`
- `mediaRequest`
- `profileImageRequest`
- `singlePost`
- `singlePostOrigin`
- `emojiPickerTarget`
- `postActionBubbleTarget`
- `postReactionHandler`
- `pendingEmojiInsertion`
- `notificationRoute`
- `closing`

`rememberSaveableStateHolder()` owns destination-local state through:

```kotlin
screenStates.SaveableStateProvider(animatedDestination.name)
```

This provider must remain in `PalustrisApp`. The key must remain the destination name. Moving the provider into a destination renderer can reset Search, Profile, or other screen-local state.

**Effects That Must Stay Initially**

Keep these effects in `PalustrisApp` during the first extraction pass:

- `LaunchedEffect(account?.id, store)`
  - Migrates and reloads account-scoped drafts.
- `LaunchedEffect(overlayKey)`
  - Initializes the profile editor from `editorBase`.
- `LaunchedEffect(availableTimelines)`
  - Resets an unavailable timeline to `Timeline.Home`.
- `LaunchedEffect(feedState?.timeline, account?.id)`
  - Synchronizes the selected timeline with feed state.
- `LaunchedEffect(destination, page, overlayKey)`
  - Restores compact navigation visibility.
- `LaunchedEffect(account?.id)`
  - Clears account-sensitive navigation, composer, media, selected-post, profile, and popup state.
  - Calls `onThreadDeactivate()`.
- `LaunchedEffect(destination, searchPanel, account?.id, sessionGeneration)`
  - Loads Photo Grid when its panel becomes active.
- `LaunchedEffect(photoGridFeed.selectedFeed, account?.id, sessionGeneration)`
  - Scrolls Photo Grid to the top.
  - Clears Photo Grid selected-post state.
- `LaunchedEffect(singlePost?.post?.id, singlePostOrigin, singlePostOrigin.supportsComments())`
  - Activates or deactivates thread loading.
- `LaunchedEffect(destination, page, overlayKey, sheet, profileDialog, signOutDialog, mediaRequest, singlePost, notificationRoute)`
  - Clears the post-action bubble.
- `LaunchedEffect(initialNotificationRoute)`
  - Opens notification destinations or notification settings.

These effects coordinate multiple state owners. Moving them with a visual component could change effect lifetime, account reset behavior, or request timing.

**Back Handling**

The root handler is at `PalustrisApp.kt:1014-1029`.

`dismissTopSurface()` uses this order:

1. Profile image viewer.
2. Wide notification settings.
3. Wide composer.
4. Wide profile editor.
5. Selected post.
6. Compact notification settings.
7. Compact composer.
8. Compact profile editor.
9. Notification detail route.
10. Local page.
11. Home destination fallback.

The root `BackHandler` is disabled while `mediaRequest` is non-null because `MediaViewerScreen` owns its own back handling.

`SinglePostScreen` also owns Back handling when it is not embedded. `NotificationSettings` has an additional handler near `PalustrisApp.kt:1713`. Modal bottom sheets also receive platform back callbacks. These handlers must not be duplicated during extraction.

The edge-swipe handler calls the same `dismissTopSurface()` function. Back and edge-swipe behavior must remain identical.

Do not include `sheet`, `profileDialog`, `signOutDialog`, or `emojiPickerTarget` in a new root Back policy unless the existing modal behavior changes intentionally. Their current dismissal is primarily owned by their modal or dialog hosts.

**Safe First-Pass Boundaries**

1. **Compact layout metrics**

Move these pure layout values and functions:

- `CompactNavigationHeight`
- `CompactTimelineSelectorWidth`
- `CompactTimelineSelectorHeight`
- `CompactOverlayControlSpacing`
- `CompactOverlayHorizontalPadding`
- `CompactOverlayVerticalPadding`
- `CompactSearchChipRowHeight`
- `CompactSearchControlsSpacing`
- `CompactSearchFieldHeight`
- `CompactFilterDockHeight`
- `CompactSearchDockHeight`
- `CompactContextualControlsPositioningClearance`
- `LegacyFeedBottomClearance`
- `compactGlobalNavigationPositioningInsets`
- `compactContextualControlsPositioningInsets`
- `compactScrollEndClearance`
- `compactHomeScrollEndClearance`

These functions only calculate layout. Preserve their visibility and calculations.

2. **Compact navigation presentation**

Move:

- `ContextualBottomAction`
- `Modifier.roundPressLayer`
- `contextualActionFor`
- `TimelineSelector`
- `CompactContextualNavigationBar`

Pass state and callbacks into the new composables. Do not move ownership of:

- `destination`
- `searchPanel`
- `notificationsPanel`
- `timeline`
- `navigationVisible`

3. **Destination renderer**

Move the explicit destination and local-page branches into a renderer. Keep the existing explicit `when` structure.

The renderer should receive a large parameter list initially. Do not create a generic registry or screen configuration abstraction.

Keep `SaveableStateProvider(animatedDestination.name)` in `PalustrisApp`. The renderer should be called inside that provider.

4. **Large detail pane**

Move the embedded `SinglePostScreen` assembly into a focused composable.

Pass in:

- The selected post.
- `singlePostOrigin`.
- Thread state.
- Available actions.
- Reaction callbacks.
- Quote support.
- Profile and hashtag callbacks.
- Media callbacks.
- Thread refresh and continuation callbacks.

Keep `singlePost` and `singlePostOrigin` ownership in `PalustrisApp`. Their changes affect compact rendering, wide rendering, profile summary visibility, and Photo Grid behavior.

5. **Selection sheets**

Move only the visible timeline and account sheet UI.

Keep `sheet` ownership in `PalustrisApp`. Do not replace the string values during the same extraction.

The current values are:

- `"Timelines"`
- `"Accounts"`

The sheet also controls:

- Account switching.
- Add-account navigation.
- Settings navigation.
- Sign-out dialog opening.
- Timeline refresh behavior.

6. **Composer sheet**

Move only the `ModalBottomSheet` and visible `ComposeScreen` wrapper.

Keep these in `PalustrisApp`:

- `draftId`
- `draft`
- `savedDraft`
- `warning`
- `savedWarning`
- `warningEnabled`
- `composerAudience`
- `savedAudience`
- `draftError`
- `savedQuoteOf`
- `savedReplyTo`
- `composerReplyTo`
- `composerQuoteOf`
- `composerTarget`
- `reloadDrafts`
- `draftValue`
- `saveCurrentDraft`
- `loadDraft`
- `openComposer`
- `openReply`
- `openQuote`
- `closeComposer`

The current audience fields must be included. Older decomposition notes omit them because they predate the current implementation.

The extracted sheet must receive callbacks for:

- Text changes.
- Warning changes.
- Audience changes.
- Emoji requests.
- Draft save.
- Close.
- Publish.
- Quote removal.
- Tracking-parameter cleanup.

Do not move draft I/O in this pass.

7. **Profile and notification sheets**

Move visible rendering only:

- Edit-profile sheet.
- Notification-settings sheet.

Keep editor state, dirty-state calculation, close confirmation, and callbacks in `PalustrisApp`.

8. **Dialogs**

Move only the two `AlertDialog` declarations:

- Discard profile changes.
- Sign out.

Keep `profileDialog` and `signOutDialog` state in `PalustrisApp`.

9. **Popup layer**

Move the composition of:

- `PostActionBubbleHost`
- `EmojiPickerHost`

Keep their targets, reaction handler, pending insertion, and callbacks in `PalustrisApp`.

**Composition-Order Risks**

Preserve the current top-level order:

1. Root content and navigation.
2. Post-action bubble host.
3. Compact selected-post presentation.
4. Media viewer.
5. Profile image viewer.
6. Selection sheet.
7. Composer sheet.
8. Edit-profile sheet.
9. Emoji picker.
10. Notification settings sheet.
11. Notification settings Back handler.
12. Profile discard dialog.
13. Sign-out dialog.

Changing this order can alter:

- Which modal receives Back.
- Which surface appears above another.
- Whether compact navigation hides.
- Whether the media system-bar policy activates.
- Whether the post-action bubble clears before a new modal opens.

**Existing Test Evidence**

- `NavigationTest.kt:235-249`
  - Destination, timeline, Search state, query, and Search category survive navigation.
- `SearchPanelRestorationTest.kt:22-32`
  - Photo Grid mode survives saved-instance restoration.
- `NavigationTest.kt:284-355`
  - Compact navigation, selector placement, contextual actions, and selected indicators.
- `NavigationTest.kt:357-485`
  - Floating controls, content underlap, and final scroll-item clearance.
- `NavigationTest.kt:487-581`
  - Profile navigation, profile category restoration, and profile switching.
- `NavigationTest.kt:583-635`
  - Local profile pages and profile editor opening.
- `NavigationTest.kt:637-661`
  - Notification settings sheet opening and closing.
- `NavigationTest.kt:663-704`
  - Search dock and IME positioning.
- `NavigationTest.kt:706-745`
  - Navigation visibility during scrolling.
- `NavigationTest.kt:755-787`
  - Draft persistence, activity recreation, deletion, and composer autosave.
- `NavigationTest.kt:789-918`
  - Contextual actions, Photo Grid restoration, account and hashtag navigation, redirects, and account switching.
- `ReplyComposerTest.kt:33-64`
  - Reply composer uses the effective reply target.
- `WideNavigationTest.kt:61-234`
  - Wide navigation rail, large shell, timeline dock, large detail pane, and wide profile presentation.
- `HomeFeedTest.kt:568-631`
  - Compact selected-post and thread presentation.
- `MediaViewerScreenTest.kt:87-211`
  - Media viewer opening and closing behavior.

**Missing Characterization Tests**

Before moving state or effects, add tests for:

- Root Back ordering across notification routes, local pages, selected posts, and destinations.
- Edge-swipe dismissal matching system Back.
- Account switching while the composer is open.
- Account switching during an asynchronous draft save.
- Notification route initialization and replacement.
- Profile image viewer Back behavior.
- Selected-post replacement when feed data refreshes.
- Photo Grid feed changes clearing the selected post.
- Dialog precedence over root Back.
- Modal composition order on compact and wide layouts.

**Recommended Extraction Sequence**

1. Extract compact metrics.
2. Extract compact navigation presentation.
3. Extract destination rendering while retaining the state holder.
4. Extract the wide detail pane.
5. Extract selection sheets.
6. Extract dialogs.
7. Extract composer visible UI.
8. Extract profile and notification sheets.
9. Extract the popup layer.
10. Consider small state holders only after all behavior tests pass.

Do not create one `PalustrisAppState` object. Keep state grouped by ownership and retain root coordination in `PalustrisApp`.
