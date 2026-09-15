# Task State: Plan 03 Protocol And Notification Persistence

**Plan:** `docs/decomposition_3/03.md`.

**Companion plan:** `docs/decomposition_3/04.md`.

**Specification:** `docs/decomposition_3/progressreport.md` section 4.

**Acceptance matrix:** `docs/agents/decomposition-01-02-acceptance-matrix.md` (Plan 01/02 record).

**Started:** 2026-09-15.

**This task is larger than one safe implementation slice.**

## Objective

Rebase Plan 03 against the completed Plan 01/02 and gate boundaries. Then implement its
behavior chunks. Make Mastodon capability decisions accurate. Reject foreign Misskey
entity identities before authenticated requests. Complete notification codec ownership
without changing installed data.

## Invariants

- Keep protocol JSON behind adapters.
- Notification persistence JSON converts domain values, not server payloads.
- Keep connection origins separate from remote public URLs.
- Keep account identity, runtime generation, and durable session revision distinct.
- Do not log tokens, push endpoints, full responses, private bodies, or corrupt blobs.
- Preserve cancellation explicitly. Do not map every failure to unsupported or corrupt.
- Preserve version 2, keys, enum strings, defaults, omission behavior, and catch boundaries.
- Do not change a stored format without a migration in the same slice.
- Use Beeline in user-facing text. Keep the codename out of user-facing content.
- Do not commit without separate authorization.

## Accepted Decisions

- The progress report is the Plan 03 rebase authority.
- Chunks 03-A through 03-C are independent of codec work.
- Chunks 03-F and 03-G need one shared storage failure contract.
- Chunks 03-I (visibility migration) and 03-F reset behavior need maintainer approval.
- No emulator is reachable. Instrumented and live-server checks stay blocked verification.

## Rebase Findings (R-01)

Source verified against `HEAD`. Plan 03's baseline `c78e2cf` predates C-01..C-15 and P-01..P-07.

- 03-A: still required. `MastodonCapabilityProbe.probeEmojiReactionMutation` still GETs
  `v1/pleroma/statuses/1/reactions/...` (`MastodonCapabilityProbe.kt:47-48`). `Ambiguous`
  still maps to `Unsupported` (`:198`).
- 03-B: closed by 03-B1 and 03-B2. The bare-404 downgrade and the non-reactive `EmojiHost`
  read are closed. Refreshed capabilities publish under a session-revision guard, and a
  failed metadata probe backs off for 30 seconds.
- 03-C: still required, reduced scope. `MisskeySource.post` (`:102`) and `delete` (`:549`)
  omit `validatePostId`. Repost undo (`:442`) and quote creation (`:350`) already validate.
- 03-D: not implemented. No `NotificationJsonCodecTest` or `app/src/test/resources/notifications/`.
- 03-E: still required. Recursive codecs remain in `NotificationRepository.kt:529-1107`.
  `NotificationJsonCodec.kt` holds only state-level `encode`/`decode`.
- 03-H: still required. `LegacyNotificationFileImporter` writes the marker before returning
  state and before Room saves (`LegacyNotificationFileImporter.kt:22-25`).
- 03-J: `AccountId.stableFileName()` still lives in the domain or data path. Rebase its
  target during 03-J.

## Rebase Corrections Applied

- Authority map: `data/SourceFactory.kt` does not exist at that path. Source construction uses
  `SocialSourceFactory`. Corrected in the plan.
- Capability publication owner: use the `ConnectedSessionContext` source from C-01.
- Notification caller-query validation already exists from Plan 02. Preserve it during codec work.
- Verification commands gained `--no-daemon --console=plain`.
- Added `MastodonReactionExtensionMapper` and the per-protocol cursor codecs to the authority map.
- `Post.contentVisibility` omission is confirmed and stays in 03-I behind approval.

## Completed Slices

| Slice | Scope | Exit | Status |
| --- | --- | --- | --- |
| R-01 | Rebase `docs/decomposition_3/03.md` against the completed boundaries. | Every rebase requirement is applied. Stale paths and commands are corrected. | implemented, source verified. |
| 03-C | Close Misskey entity boundaries. Validate `post`, `delete`, and quote identities before network access. | Invalid or foreign identities reach no network request. | implemented, test verified. |
| 03-A1 | Remove the sentinel reaction probe. Treat the recognized advertisement as the evidence. Bound the instance read and add a v1 fallback on v2 absence. | No capability request contains a sentinel status. Missing advertisement never proves support. | implemented, test verified. |
| 03-B1 | Preserve runtime capability evidence. Remove the bare-404 downgrade, publish capabilities reactively, reuse the metadata owner at login, and invalidate sentinel-era snapshots. | A resource failure keeps advertised support. Reaction controls read an observable capability value. | implemented, test verified. |
| 03-B2 | Publish refreshed capabilities under a session-revision guard and bound refresh retries. | A stale source cannot overwrite a replaced session. A failed probe does not re-probe on every request. | implemented, test verified. |

R-01 verification: source verified for every named authority at `b715430`. No test ran. The
rebase changed documentation only.

03-C verification: `MisskeyIntegrationTest` and `CrossCuttingTest` pass. Added
`misskeyPostAndDeleteRejectForeignOriginsBeforeNetwork`,
`misskeyPostAndDeleteRejectBlankValuesBeforeNetwork`, and
`misskeyCreateRejectsForeignAndBlankQuoteBeforeNetwork`. `post` and `delete` now call
`validatePostId`. Quote creation uses the same validator. Repost undo already validated.

03-A1 verification: `MastodonCapabilityProbeTest` and `MastodonIntegrationTest` pass. Removed
`EmojiMutationProbeOutcome`, `probeEmojiReactionMutation`, and the status-1 sentinel request.
`parseCapabilities` now takes only the instance. A recognized advertisement yields listing and
mutation support with independent selection. Missing or malformed advertisement yields Unknown
and no React action. `fetchInstanceMetadata` prefers `v2/instance`, falls back to `v1/instance`
only on 404, bounds the read to 512 KiB, and propagates every other failure.

03-B1 verification: `MastodonIntegrationTest`, `MastodonCapabilityProbeTest`,
`MisskeyIntegrationTest`, `CrossCuttingTest`, and `SignInScreenTest` pass. `test assembleRelease`
and `:app:lintDebug` pass. `MastodonSource.react` and `removeReaction` no longer contain a
downgrade path. `SocialSource.observeCapabilities()` is added with a snapshot default.
`MisskeySource` and `MastodonSource` return their capability state flow. `EmojiHost` collects
that flow. `CURRENT_CAPABILITY_SCHEMA_VERSION` is `5`. `MastodonAuth` reuses
`MastodonCapabilityProbe.fetchInstanceMetadata`. New tests
`failedReactionMutationKeepsExtensionSupport`, `failedAddAndRemoveKeepSupportForLaterPosts`, and
`refreshedCapabilitiesPublishReactionSupportThroughTheFlow`.

03-B2 verification: `MastodonIntegrationTest`, `CrossCuttingTest`, `MisskeyIntegrationTest`, and
`ConnectedSessionContextTest` pass. `test assembleRelease` and `:app:lintDebug` pass.
`SessionStore.updateCapabilities` and `AccountFileStore.updateCapabilities` take an optional
`expectedRevision` and skip the write when the stored session revision differs.
`SocialSourceFactory` passes `session.sessionRevision` for both protocols. `MastodonSource`
publishes a successful refresh through `onCapabilitiesUpdated` and backs off 30 seconds after a
failed probe. New tests `refreshedCapabilitiesReachTheCapabilityCallback`,
`metadataFailureBoundsCapabilityRetries`, and `capabilityUpdateRequiresTheCurrentSessionRevision`.

## Current Slice

Closed. No active slice. The next Plan 03 chunk is 03-D (freeze compatibility fixtures). 03-A2
(NodeInfo discovery when instance metadata lacks the advertisement) and 03-E through 03-J stay
open. 03-F and 03-I need maintainer approval before coding.

## Required Verification

Use focused tests first. Then run the full gate.

```powershell
$env:GRADLE_OPTS="-Dorg.gradle.daemon=false"
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "*MastodonCapabilityProbeTest" --tests "*MastodonIntegrationTest" --tests "*MisskeyIntegrationTest" --tests "*CrossCuttingTest"
.\gradlew.bat --no-daemon --console=plain test assembleRelease
.\gradlew.bat --no-daemon --console=plain :app:lintDebug
```

Close standard input. Set an explicit timeout for each Gradle call.

## Unresolved Blockers

- No emulator or device is reachable. Connected instrumentation stays unverified.
- Live-server behavior stays unverified.
- Signed-release behavior stays unverified.
- The Android 15 system-bar failure stays in `logs/BUGS.txt`.
- 03-F reset policy and 03-I visibility migration need maintainer approval before coding.

## Last Safe Commit

`1c45afb` "Guard capability persistence by session revision and back off failed probes".
