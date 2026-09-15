# Decomposition 01 And 02 Acceptance Matrix

**Status:** current. This page replaces the conflicting completion claims in the old task files.

**Owner:** app-shell and feature-state maintainers.

**Last reviewed:** 2026-09-14.

**Source baseline:** `b629a2c`.

**Stale when:** A listed exit condition changes, or a slice in
`docs/agents/tasks/decomposition-01-02-completion.md` moves the status.

**Evidence:** source verified for every path in this page. Test files were inspected. The C-01
slice ran `ConnectedSessionContextTest`, `SessionViewModelTest`, `test assembleRelease`, and
`:app:lintDebug` on 2026-09-14. Other statuses repeat a pass that `logs/DONE.txt` records, not a
new run.

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
| 01-B | One feature action change does not change unrelated contracts. | `ui/shell/*.kt` contracts | contract tests, `AppShellFixtures.kt` | Implemented, source verified | C-12 |
| 01-C | No storage selection, repository call, or `SocialSource` remains in `PalustrisApp`. | `LocalPostActionOwner`; composer fields still in `PalustrisApp.kt` | `PalustrisApp.kt` 1439 lines | Partially implemented | C-05, C-06 |
| 01-D | One reviewed path owns fan-out. No duplicate listener, cycle, stale sink, or double increment. | `ui/shell/PostProjectionCoordinator.kt` | `PostProjectionCoordinatorTest.kt` | Partially implemented | C-07 |
| 01-E | Recomposition does not construct replacement sources. Session replacement cannot invoke old owners. | `ui/session/ConnectedSessionContext.kt`, `ConnectedSessionHost.kt`, `AccountManager.kt` | `ConnectedSessionContextTest.kt`, `SessionViewModelTest.kt` | Partially implemented. The coherent context and the registered source are test verified. Retired-owner teardown stays open. | C-02 |
| 01-F | `ConnectedApp` composes root hosts. It does not write settings, assemble actions, or own fan-out. | `ui/ConnectedApp.kt` (156 lines), `SettingsOverlayHost`, `NotificationLaunchHost` | `SettingsViewModelTest.kt`, `NotificationLaunchRouterTest.kt` | Partially implemented | C-09, C-10 |
| 01-G | `PalustrisApp` owns navigation and placement, not feature implementation. | navigation shell; composer fields remain | `NavigationTest.kt`, `WideNavigationTest.kt` | Partially implemented | C-05, C-12 |
| 01-H | A new feature action needs no unrelated fixture change. Source, tests, and documentation agree. | `AppShellFixtures.app`; documentation was not reconciled | `AppShellFixtures.kt` | Partially implemented | C-12, C-14 |

### Source Notes

- C-01 removed the unregistered fallback. `ConnectedSessionHost` now reads one
  `ConnectedSessionContext`. `AccountManager.connect` publishes that context after source
  registration is ready.
- `ConnectedSessionHost.kt:187` remembers `PostActionOwner` with the whole `profile` contract. An ordinary profile update can replace popup ownership.
- `PalustrisApp.kt` still holds composer editor fields, audience, reply, quote, and draft action state.

## 3. Plan 02 Exit Conditions

Plan 02 section 14 defines slices 02-A through 02-L. This table maps each exit condition.

| Slice | Exit condition | Implementation | Evidence | Status | Completion slice |
| --- | --- | --- | --- | --- | --- |
| 02-A | Old successes and failures cannot change current rows, cursors, errors, or independent state. | `FeedViewModel` request epochs | `FeedViewModelRequestTest.kt` | Implemented, test verified | — |
| 02-B | Late thread or send results cannot move selection or write into another conversation. | `DirectMessageViewModel` selection and send ownership | `DirectMessageViewModelTest.kt` | Partially implemented | C-04 |
| 02-C | Removed accounts stay deleted. Old sessions cannot write. Accepted sends survive thread refresh. | `DirectMessageWriteAuthority`, `DirectMessageRepository` | `DirectMessageRepositoryTest.kt` | Partially implemented | C-03 |
| 02-D | Rejected pages leave memory and persistent state unchanged. Synchronization reports rejection. | `NotificationSynchronizer`, `NotificationRepository` caller query | `NotificationSynchronizerTest.kt`, `NotificationRepositoryTest.kt` | Implemented, source verified | C-09 |
| 02-E | Refresh, removal, retry, and replacement cannot leave stuck or misowned moderation state. | `ModerationViewModel`, removal tokens | `ModerationViewModelTest.kt` | Implemented, test verified | C-02 |
| 02-F | One failed action cannot restore unrelated fields or undo another family's result. | `PostInteractionMutationOwner` | `PostInteractionMutationOwnerTest.kt` | Partially implemented | C-07 |
| 02-G | Refresh cannot revive removed reactions. Stale jobs cannot modify a replacement thread or popup. | `PostThreadViewModel` overlays and projection | `PostThreadViewModelTest.kt`, `PostProjectionTest.kt` | Partially implemented | C-07 |
| 02-H | Home reaches older visible content without unbounded requests or hidden continuation. | `HomeFeed.kt`, `HomePagingDemand.kt` | `HomePagingDemandTest.kt`, `HomeFeedTest.kt` | Partially implemented | C-08 |
| 02-I | Settings changes cannot overwrite newer fields or reopen under the wrong account or page. | `SettingsViewModel`, `SettingsRoute` saver | `SettingsViewModelTest.kt`, `SettingsRouteRestorationTest.kt` | Partially implemented | C-10 |
| 02-J | All 15 resource locales are listed and selectable. System default stays separate. | `AppLanguage`, `locales_config.xml`, `LanguageSettingsScreen` | `LocalizationResourceTest.kt`, `LanguageSettingsScreenTest.kt` | Implemented, test verified | — |
| 02-K | Selecting a language changes actual resources and survives supported restoration without loops. | `AppLocaleController`, `MainActivity` | `AppLocaleControllerTest.kt` | Partially implemented | C-11 |
| 02-L | Cancellation stays cancellation. Cleanup stays reliable. Repair tests pass. | cancellation rethrows in touched paths | `PushCancellationTest.kt`, `DraftActionsTest.kt`, `NotificationsViewModelTest.kt` | Partially implemented | C-13 |

### Source Notes

- `DirectMessageRepository.markRead` (lines 97-103) checks authority and then writes in two steps. It does not use `commitIfCurrent`.
- `DirectMessageConversationScreen.kt:124` clears the draft immediately on Send.
- `HomeFeed.kt:116-121` keys paging demand on `visibleRows.size`. Filter identity is absent.
- `HomePagingDemand.onPageRequested` counts requests. Its contract describes accepted pages.
- `AppLocaleController.reconcilePlatformSelection` imports a differing platform locale on every call. It cannot tell startup reconciliation from a later user command.
- `NotificationLaunchHost` clears a pending launch after a `Unit` callback. That callback cannot confirm receiving-shell acceptance.

## 4. Progress Report Gap Map

`progressreport.md` section 1 lists ten gaps. This table maps each gap to a completion slice.

| Gap | Source evidence | Completion slice |
| --- | --- | --- |
| Connected identity | Closed by C-01. `AccountManager.connect` publishes one `ConnectedSessionContext`. | C-01 (implemented, test verified) |
| Source ownership | Closed by C-01. `ConnectedSessionHost` reads the context source. The fallback is removed. | C-01 (implemented, test verified) |
| ViewModel lifetime | Feature hosts stop activity-store models on composition disposal. | C-02 |
| Composer | `PalustrisApp.kt` clears editor state without an editor-version check. | C-05, C-06 |
| Draft callbacks | `DraftActions` has no request or session publication guard. | C-06 |
| DM text | `DirectMessageConversationScreen.kt:124` clears the input at once. | C-04 |
| DM storage | `markRead` uses a separate authority check and store write. | C-03 |
| Locale changes | `reconcilePlatformSelection` always imports a differing platform locale. | C-11 |
| Home paging | Budget reset uses row count. Filter identity is absent. | C-08 |
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
