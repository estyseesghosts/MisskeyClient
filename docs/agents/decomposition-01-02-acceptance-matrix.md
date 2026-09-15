# Decomposition 01 And 02 Acceptance Matrix

**Status:** current. This page replaces the conflicting completion claims in the old task files.

**Owner:** app-shell and feature-state maintainers.

**Last reviewed:** 2026-09-15.

**Source baseline:** `b629a2c` (assessment). C-01 through C-11 and C-12a status refreshed against `PENDING`.

**Stale when:** A listed exit condition changes, or a slice in
`docs/agents/tasks/decomposition-01-02-completion.md` moves the status.

**Evidence:** source verified for every path in this page. Test files were inspected. The C-01, C-02,
C-03, C-04, C-05, C-06a, C-06b, C-06c, C-07, C-08, C-09, C-10, C-11, C-12a, and L-01 slices ran their focused
tests, `test assembleRelease`, and `:app:lintDebug` on 2026-09-14 and 2026-09-15. Other statuses
repeat a pass that `logs/DONE.txt` records, not a new run.

## 1. How To Read This Page

Use one of these statuses.

| Status | Meaning |
| --- | --- |
| Implemented, test verified | Source implements the exit condition. A named test covers it. `logs/DONE.txt` records a pass. |
| Implemented, source verified | Source implements the exit condition. A test file exists or the behavior is clear. No focused run covers the full condition. |
| Partially implemented | Source implements part of the exit condition. A named completion slice owns the rest. |
| Not implemented | Source does not implement the exit condition. |
| Blocked verification | Source is complete. Device, live-server, or instrumentation evidence is missing. |
| Assigned to a later plan | Plan 03 or Plan 04 owns the condition. |

The completion slice column names the work in
`docs/agents/tasks/decomposition-01-02-completion.md` that changes the status.

## 2. Plan 01 Exit Conditions

Plan 01 section 9 defines slices 01-A through 01-H. This table maps each exit condition.

| Slice | Exit condition | Implementation | Evidence | Status | Completion slice |
| --- | --- | --- | --- | --- | --- |
| 01-A | Tests protect the behavior being moved. Plan 02 failures stay separate. | `AppShellFixtures.kt`, `ShellCharacterizationTest.kt` | those tests | Implemented, test verified | — |
| 01-B | One feature action change does not change unrelated contracts. | `ui/shell/*.kt` contracts | contract tests, `AppShellFixtures.kt` | Implemented, source verified | C-12b |
| 01-C | No storage selection, repository call, or `SocialSource` remains in `PalustrisApp`. | `LocalPostActionOwner`; composer fields moved to `ui/composer/ComposerOwner.kt` | `ComposerOwnerTest.kt`, `ReplyComposerTest.kt` | Implemented, test verified | — |
| 01-D | One reviewed path owns fan-out. No duplicate listener, cycle, stale sink, or double increment. | `ui/shell/PostProjectionCoordinator.kt` | `PostProjectionCoordinatorTest.kt` | Implemented, test verified | — |
| 01-E | Recomposition does not construct replacement sources. Session replacement cannot invoke old owners. | `ui/session/ConnectedSessionContext.kt`, `ui/session/ConnectedEntryStore.kt`, `ConnectedSessionHost.kt`, `AccountManager.kt` | `ConnectedSessionContextTest.kt`, `ConnectedEntryStoreTest.kt`, `SessionViewModelTest.kt` | Implemented, test verified. | — |
| 01-F | `ConnectedApp` composes root hosts. It does not write settings, assemble actions, or own fan-out. | `ui/ConnectedApp.kt` (root composition only), `SettingsOverlayHost`, `NotificationLaunchHost` | `SettingsViewModelTest.kt`, `NotificationLaunchRouterTest.kt`, `NotificationLaunchHostTest.kt` | Implemented, test verified | — |
| 01-G | `PalustrisApp` owns navigation and placement, not feature implementation. | navigation shell; composer editor moved to `ui/composer/`; composer sheet assembly moved to `ui/composer/ComposerOverlayHost.kt` | `NavigationTest.kt`, `WideNavigationTest.kt` | Partially implemented | C-12b |
| 01-H | A new feature action needs no unrelated fixture change. Source, tests, and documentation agree. | `AppShellFixtures.app`; documentation was not reconciled | `AppShellFixtures.kt` | Partially implemented | C-12b, C-14 |

### Source Notes

- C-01 removed the unregistered fallback. `ConnectedSessionHost` now reads one
  `ConnectedSessionContext`. `AccountManager.connect` publishes that context after source
  registration is ready.
- C-02 added `ui/session/ConnectedEntryStore.kt`. Feature hosts register their `stop` callback
  under a stable key. The store is activity-scoped, so it survives recreation. It retires a model
  on connected-lifetime change and on owner clear, not on composition disposal.
- C-05 added `ui/composer/ComposerEditorState.kt`, `ui/composer/ComposerOwner.kt`, and
  `ui/composer/ComposerHost.kt`. The owner holds editor fields, the dirty snapshot, the drafts list,
  reply and quote restoration, and the publish flow. It publishes `ComposerNavigation` requests. The
  shell places the overlay and keeps back precedence.
- C-06a added an editor revision and a reserved submission to `ComposerOwner`. `publish` captures the
  request, account, draft identity, revision, and session revision before the asynchronous save. A
  second publish is rejected while a submission is pending. An obsolete save callback cannot publish.
  Success clears and deletes only the submitted version.
- C-06b moved the draft storage owner to `data/auth/DraftActions.kt`. The presentation contract
  carries no storage type. `DraftActions` binds to one account and holds the legacy preferences
  lookup. `ComposerOwner.refreshDrafts` uses a load epoch. Load and delete failures report an explicit
  message.
- C-06c added `data/auth/DraftWriteAuthority.kt`. `AccountManager` activates the draft generation on
  connect, carries it through `updateAccount`, and revokes writers before deleting rows on removal.
  `DraftActions` routes save and delete through `commitIfCurrent`. A revoked writer writes nothing
  and reports no success.
- C-07 built the popup owner from the stable connected identity and reads the profile refresh
  callback without recreating the owner. The coordinator and the popup owner retire with the
  connected entry. Repeated publication deliveries are rejected by created-post identity.
  Family slots are typed with operation tokens. The narrow popup interface extraction stays
  deferred to C-12.
- C-10 bound post commands to the validated account set the shell publishes. A command for a
  removed account reports an unavailable error at call time. A queued command writes nothing after
  a removal. Failed commands support explicit retry. Command errors are typed, and the shell
  resolves the user-visible message from resources. Account settings models wait for the restored
  account index.
- C-12a added `ui/composer/ComposerOverlayHost.kt`. The shell keeps composer overlay
  placement with open, guarded close, and emoji-picker target requests. The feature owns the
  sheet assembly beside `ComposerOwner`. Owner lifetime and the saveable editor snapshot are
  unchanged. The feature-action review trace confirms a composer presentation change needs no
  shell contract change and no fixture change.

## 3. Plan 02 Exit Conditions

Plan 02 section 14 defines slices 02-A through 02-L. This table maps each exit condition.

| Slice | Exit condition | Implementation | Evidence | Status | Completion slice |
| --- | --- | --- | --- | --- | --- |
| 02-A | Old successes and failures cannot change current rows, cursors, errors, or independent state. | `FeedViewModel` request epochs | `FeedViewModelRequestTest.kt` | Implemented, test verified | — |
| 02-B | Late thread or send results cannot move selection or write into another conversation. | `DirectMessageViewModel` selection, send, and editor ownership | `DirectMessageViewModelTest.kt` | Implemented, test verified | — |
| 02-C | Removed accounts stay deleted. Old sessions cannot write. Accepted sends survive thread refresh. | `DirectMessageWriteAuthority`, `DirectMessageRepository` | `DirectMessageRepositoryTest.kt` | Implemented, test verified | — |
| 02-D | Rejected pages leave memory and persistent state unchanged. Synchronization reports rejection. | `NotificationSynchronizer`, `NotificationRepository` caller query, `NotificationsViewModel` request epoch | `NotificationSynchronizerTest.kt`, `NotificationRepositoryTest.kt`, `NotificationsViewModelTest.kt` | Implemented, test verified | — |
| 02-E | Refresh, removal, retry, and replacement cannot leave stuck or misowned moderation state. | `ModerationViewModel`, removal tokens, connected entry store | `ModerationViewModelTest.kt` | Implemented, test verified | — |
| 02-F | One failed action cannot restore unrelated fields or undo another family's result. | `PostInteractionMutationOwner`, `PostActionFamily` | `PostInteractionMutationOwnerTest.kt`, `PostInteractionExecutionAuthorityTest.kt` | Implemented, test verified | — |
| 02-G | Refresh cannot revive removed reactions. Stale jobs cannot modify a replacement thread or popup. | `PostThreadViewModel` overlays and projection, retired `PostActionOwner` | `PostThreadViewModelTest.kt`, `PostProjectionTest.kt`, `PostActionOwnerTest.kt` | Implemented, test verified | — |
| 02-H | Home reaches older visible content without unbounded requests or hidden continuation. | `HomeFeed.kt`, `HomePagingDemand.kt` | `HomePagingDemandTest.kt`, `HomeFeedTest.kt` | Implemented, test verified | — |
| 02-I | Settings changes cannot overwrite newer fields or reopen under the wrong account or page. | `SettingsViewModel` validity gate and retry, `SettingsRoute` saver | `SettingsViewModelTest.kt`, `SettingsRouteRestorationTest.kt` | Implemented, test verified | — |
| 02-J | All 17 resource locales are listed and selectable. System default stays separate. | `AppLanguage`, `locales_config.xml`, `LanguageSettingsScreen` | `LocalizationResourceTest.kt`, `LanguageSettingsScreenTest.kt` | Implemented, test verified | — |
| 02-K | Selecting a language changes actual resources and survives supported restoration without loops. | `AppLocaleOwner`, `MainActivity` | `AppLocaleOwnerTest.kt`, `AppLocaleControllerTest.kt` | Implemented, test verified | — |
| 02-L | Cancellation stays cancellation. Cleanup stays reliable. Repair tests pass. | cancellation rethrows in touched paths | `PushCancellationTest.kt`, `DraftActionsTest.kt`, `NotificationsViewModelTest.kt` | Partially implemented | C-13 |

### Source Notes

- `DirectMessageWriteAuthority` serializes activation, revocation, deletion, and accepted writes
  under one lock per account. The account lifecycle issues the generation. A repository captures it.
  `DirectMessageRepository.markRead` routes its local write through `commitIfCurrent`.
- `DirectMessageViewModel` owns the composer text and an editor revision. A selection change resets
  the text and advances the revision. A send clears the editor only when accepted and unchanged. A
  failed send keeps the text.
- C-08 published the feed request epoch through `FeedState` and `HomeFeedUiState`. The demand
  tracks filter identity and the epoch beside the row count. `reset` advances a demand generation
  so evaluation reruns on unchanged rows. `onPageAccepted` counts only accepted pages. The demand
  blocks while sign-in is required.
- C-11 added `ui/localization/AppLocaleOwner.kt`. Startup runs first-upgrade precedence once.
  A moved repository exports the in-app choice. A moved platform imports the external choice,
  including a clear to System default. A pending import repeats until applied. `MainActivity`
  serializes each decision with its side effect, prefers the platform value for the base
  context on Android 13 and later, and reports a failed import through repository state
  without a recreation loop.
- C-09 made launch delivery return explicit acceptance. The host always calls the latest route
  callback and clears a launch only when the receiving shell accepts it. `ConnectedApp` accepts
  only with an accepted connected context. The inbox carries a request epoch, so rejected pages
  change no state.

## 4. Progress Report Gap Map

`progressreport.md` section 1 lists ten gaps. This table maps each gap to a completion slice.

| Gap | Source evidence | Completion slice |
| --- | --- | --- |
| Connected identity | Closed by C-01. `AccountManager.connect` publishes one `ConnectedSessionContext`. | C-01 (implemented, test verified) |
| Source ownership | Closed by C-01. `ConnectedSessionHost` reads the context source. The fallback is removed. | C-01 (implemented, test verified) |
| ViewModel lifetime | Closed by C-02. Feature hosts retire models through `ConnectedEntryStore`. | C-02 (implemented, test verified) |
| Composer | Closed by C-05, C-06a, C-06b, and C-06c. The editor lives in `ui/composer/`. Publish is version-aware. The draft storage owner lives in `data/auth/`. Removal revokes pending draft writers before deletion. | C-05, C-06a, C-06b, C-06c (implemented, test verified) |
| Draft callbacks | Closed by C-06a, C-06b, and C-06c. Publish reserves the submission, rejects obsolete callbacks, and reports load and delete failures. A revoked draft writer reports nothing. | C-06a, C-06b, C-06c (implemented, test verified) |
| DM text | Closed by C-04. `DirectMessageViewModel` owns the composer text and revision. A failed send keeps the text. | C-04 (implemented, test verified) |
| DM storage | Closed by C-03. `markRead` writes through `commitIfCurrent`. One lock owns activate, revoke, delete, and commit. | C-03 (implemented, test verified) |
| Locale changes | Closed by C-11. Startup runs first-upgrade precedence once. Later in-app changes export and later external changes import. | C-11 (implemented, test verified) |
| Home paging | Closed by C-08. The budget resets on filter identity and request epoch changes. Only accepted pages count. | C-08 (implemented, test verified) |
| Documentation | Plans, task state, and ownership pages contradict each other. | C-14, this pass |

## 5. Work Assigned To Later Plans

These items are not part of the 01/02 completion. Plan 03 or Plan 04 owns each one.

| Item | Owner |
| --- | --- |
| Mastodon sentinel capability probing | Plan 03, chunk 03-A |
| Mastodon runtime downgrade on bare 404 | Plan 03, chunk 03-B |
| Misskey foreign-origin entity validation | Plan 03, chunk 03-C |
| Notification codec extraction and fixtures | Plan 03, chunks 03-D, 03-E |
| Notification corruption recovery and schema policy | Plan 03, chunks 03-F, 03-G, 03-H |
| Notification visibility persistence | Plan 03, chunk 03-I |
| External-link ownership | Plan 04, chunk 04-A |
| Unicode default data extraction | Plan 04, chunk 04-B |
| Emoji coordination and storage retention | Plan 04, chunks 04-C, 04-D |
| DM cache dependence | Plan 04, chunk 04-E |
| Misskey continuation retention | Plan 04, chunk 04-F |
| HTTP client lookup retention | Plan 04, chunk 04-G |
| Capability and registration caches | Plan 04, chunk 04-H |
| Media metadata retention | Plan 04, chunk 04-I |
| Correctness-critical notification records | Plan 04, chunk 04-J |

Plan 03 and Plan 04 require a rebase against the completed boundaries before implementation.

## 6. Final Acceptance Standard

Plans 01 and 02 are complete only when all of these statements are true.

- Connected identity is coherent.
- Retired owners cannot publish.
- Recreated features remain usable.
- Draft and DM text survive failed operations.
- Publish completion cannot clear newer edits.
- Mutations reconcile across surfaces.
- Home paging obeys its accepted-page budget.
- Settings retain valid targets and routes.
- Language changes follow the latest accepted user intent.
- Feature actions stay out of unrelated shell layers.
- Required tests execute successfully.
- Maintained documentation matches source.

Track the remaining work in `docs/agents/tasks/decomposition-01-02-completion.md`.
