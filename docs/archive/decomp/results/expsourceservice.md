# Source-Service Extraction Boundaries

## Scope and Current Shape

`SocialSource` is the protocol-neutral facade. Its relevant methods are:

- `timeline(timeline: Timeline, cursor: String?): Page<Post>`
- `threadContext(focalId: EntityId, continuation: ThreadContinuation?): ThreadContext`

`MisskeySource` and `MastodonSource` implement both methods. Both sources also own the common `request` wrapper, capability state, and the authenticated session values.

Neither protocol has a separate timeline or thread service today. Existing protocol services establish the local pattern. `MisskeyProfileService` and `MastodonProfileService` receive `origin`, `token`, `api`, and account identity, then expose protocol operations without owning source-level error normalization.

`SocialSourceFactory` creates each source with the session token, account identity, capabilities, and session revision. It does not create any operation service. A new service must therefore remain an implementation detail of the source facade unless dependency injection changes later.

## Shared Contract Boundary

`SocialSource.timeline` returns a `Page<Post>`. The page contains mapped protocol-neutral posts and one opaque `nextCursor`.

`SocialSource.threadContext` returns a flat `ThreadContext`. The result carries the focal post, ordered ancestors, ordered descendants, optional continuation, limitations, acquisition state, and an optional refresh hint.

The contract does not expose endpoints, request bodies, HTTP headers, protocol cursors, or protocol capability probes. A service can implement request and mapping details behind these methods without changing the contract.

The facade must continue to expose `capabilities`. UI code reads this property directly. `FeedViewModel` uses capability timelines and calls `source.timeline`. `PostThreadViewModel` calls `source.threadContext` and uses source capabilities for thread actions. No caller should receive a new protocol service.

## Misskey Trace

### Timeline

`MisskeySource.timeline` is at `app/src/main/java/me/foxtails/palustris/data/misskey/MisskeySource.kt:95-118`.

The current sequence is:

1. The source enters `request(invalidateCapabilitiesOnNotFound = true)`.
2. The source calls `refreshCapabilities()`.
3. The source converts `capabilities.timelineStatus(timeline)` into a shared `SourceError`.
4. The source creates a JSON body with `i = token` and `limit = 30`.
5. The source adds `untilId = cursor` when the cursor is not null.
6. The source maps the timeline to a native endpoint.
7. The source adds `withFiles = true`.
8. The source calls `MisskeyApi.post(origin, endpoint, body)`.
9. The source parses the response as a `JSONArray`.
10. The source maps every note with `MisskeyMapper.post(note, origin)`.
11. The source uses the last raw note `id` as `nextCursor`.

The endpoint mapping is:

- `Timeline.Home` -> `notes/timeline`
- `Timeline.Local` -> `notes/local-timeline`
- `Timeline.Social` -> `notes/hybrid-timeline`
- `Timeline.Bubble` -> `notes/bubble-timeline`
- `Timeline.Federated` -> `notes/global-timeline`

The cursor is an opaque Misskey note identifier. The next request sends it as `untilId`. The source deliberately reads the outer raw note identifier. This matters for renotes because the displayed original note has a different identity.

### Thread

`MisskeySource.threadContext` and its private helpers occupy `MisskeySource.kt:165-365`.

The acquisition sequence is:

1. The source validates the focal identifier against `origin` and rejects blank identifiers.
2. The source creates `ThreadSessionKey(fetchingAccount(), sessionRevision, focalId)`.
3. A null continuation starts `beginThreadAcquisition`.
4. A non-null continuation is consumed from `continuationStore` after session-key validation.
5. The source calls `acquireDescendants`.
6. The source creates a new random continuation token when pending work remains and the hard limit is not reached.
7. The source removes the old token when acquisition finishes.
8. The source returns `ThreadContext` with ordered data and derived acquisition state.

`beginThreadAcquisition` performs these operations:

- It loads the focal post through `notes/show`.
- It starts with `requestsUsed = 1` because the focal request counts.
- It follows `replyTo` links through repeated `notes/show` calls.
- It stops at `MAX_ANCESTORS = 20` and records `AncestorLimit`.
- It detects repeated ancestor identifiers.
- It records `UnavailableParent` for a non-cancellation `SourceError` while loading a parent.
- It reverses collected ancestors so the root appears first.
- It queues the focal post as descendant work at depth `1`.

`acquireDescendants` uses breadth-first queued work. Each `ChildWork` contains a parent identifier, semantic depth, and optional `untilId` cursor. The source calls `notes/children` with `i`, `noteId`, `limit = 30`, and `untilId` when present.

The exact Misskey limits are:

- `MAX_DESCENDANTS = 200`
- `MAX_DESCENDANT_DEPTH = 10`
- `MAX_BATCH_REQUESTS = 8`
- `MAX_REQUESTS = 40`
- `MAX_BATCH_TIME_MILLIS = 15_000`
- `CHILDREN_PAGE_LIMIT = 30`
- `MAX_THREAD_RESPONSE_BYTES = 4 * 1024 * 1024`

The descendant rules are significant:

- The batch stops with `BatchTimeLimit` after eight child requests or fifteen seconds.
- A work item beyond depth ten records `DepthLimit`.
- Every request passes through `reserveRequest`.
- A request-limit hit clears pending work, marks the acquisition hard-limited, and records `RequestLimit`.
- The source reads the last raw child identifier before mapping.
- The mapper can skip malformed child objects.
- The source rejects mapped children from a different origin.
- The source accepts only children whose `replyTo` equals the current parent identifier.
- The source deduplicates descendants by `EntityId`.
- The source queues each accepted child for the next depth.
- A full raw page queues another request for the same parent with the last raw identifier.
- The source avoids re-queuing when the new cursor equals the current cursor.
- A node limit clears pending work and prevents further requests.

`loadThreadPost` uses `notes/show` with the four-megabyte response limit. It validates the mapped post origin. `normalizeThreadError` preserves `SourceError` and maps `ApiFailure` through `MisskeyErrorMapper`.

Cancellation is explicit. `CancellationException` is rethrown while loading ancestors and child responses. The outer `request` wrapper also rethrows cancellation. Other failures become limitations for descendant branches, while focal-post failures leave the normal source error path.

The continuation map is `ConcurrentHashMap<String, ThreadAcquisition>`. It is instance-local and therefore not persisted. The continuation binds account, session revision, and focal post. `continuationState` removes the token before resuming, so a token is single-use.

## Mastodon Trace

### Timeline

`MastodonSource.timeline` is at `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonSource.kt:101-116`.

The current sequence is:

1. The source enters its `request` wrapper.
2. The source calls `refreshCapabilities()`.
3. The source rejects a timeline absent from `capabilities.timelines`.
4. The source maps the timeline to a Mastodon endpoint.
5. The source calls `getPage(endpoint, cursor)`.
6. The source parses the response as a status array.
7. The source maps each status with `MastodonMapper.post(status, origin)`.
8. The source reads the `next` relation from the HTTP `Link` header.

The endpoint mapping is:

- `Timeline.Home` -> `v1/timelines/home`
- `Timeline.Local` -> `v1/timelines/public?local=true`
- `Timeline.Federated` -> `v1/timelines/public`
- `Timeline.Social` and `Timeline.Bubble` -> `SourceError.Unsupported("timeline:$timeline")`

`getPage` has three cursor paths:

- A null cursor calls `api.get(origin, endpoint, token)`.
- An absolute HTTP or HTTPS cursor calls `api.getUrl` after validation.
- Any other cursor calls `api.get(origin, cursor.removePrefix("/api/"), token)`.

`validatePaginationUrl` requires the same scheme, host, and port as the authenticated origin. It rejects credentials and fragments. It does not send the bearer token before validation. The helper accepts an absolute URL with an arbitrary path, because the server owns the opaque continuation URL.

`HttpResponse.linkHeaderCursor` parses the `Link` header and returns the URL whose relation contains `next`. An absent link produces a null cursor. The service must preserve the full URL, including query parameters.

### Thread

`MastodonSource.threadContext` is at `MastodonSource.kt:124-156`.

The source first rejects `Unsupported` or `Denied` thread capability states. It validates a supplied continuation session key against `ThreadSessionKey(accountId, sessionRevision, focalId)`. It then ignores the continuation token because Mastodon supplies a complete context in one request.

The source calls these endpoints:

- `GET /api/v1/statuses/{focalId}` for the focal status.
- `GET /api/v1/statuses/{focalId}/context` for ancestors and descendants.

Both calls use the bearer token and `MAX_THREAD_RESPONSE_BYTES = 4 * 1024 * 1024`. The context parser maps each array item independently with `MastodonMapper.post`. Malformed array entries are skipped by `JSONArray.toPosts`. The result always has `ThreadAcquisitionState.Finished`, no continuation, and the optional `ThreadRefreshHint` from `Mastodon-Async-Refresh`.

`parseRefreshHint` accepts only `id="...", retry=<seconds>, result_count=<count>`. It converts retry seconds to milliseconds. Invalid or absent headers produce no hint.

The shared `request` wrapper rethrows `CancellationException`, preserves `SourceError`, converts `ResponseLimitExceeded` to `SourceError.ResourceLimit("thread")`, and maps other failures with `MastodonErrorMapper`.

## Move-Only Extraction Boundaries

### Misskey timeline service

Create a focused service, for example `MisskeyTimelineService`, with the same session dependencies as `MisskeyProfileService`.

Move only:

- The body of `MisskeySource.timeline` from JSON body creation through `Page` construction.
- The Misskey timeline endpoint mapping.
- The timeline page limit `30`.
- The `withFiles = true` request option.
- The raw last-note cursor rule.

Keep in `MisskeySource`:

- `refreshCapabilities()`.
- `capabilities.timelineStatus` and its `CapabilityStatus` to `SourceError` mapping.
- The call to `request(invalidateCapabilitiesOnNotFound = true)`.
- Capability cache invalidation after a 404 or `NOT_SUPPORTED` response.

The service should return `Page<Post>` and receive an already-authorized timeline request. It must not receive or mutate `ServerCapabilities`. This keeps capability policy and cache ownership in the facade.

### Misskey thread service

Create a focused `MisskeyThreadService`.

Move all thread-only state and methods:

- `continuationStore`.
- `threadContext` acquisition logic, excluding the outer source error wrapper.
- `beginThreadAcquisition`.
- `acquireDescendants`.
- `loadThreadPost`.
- `reserveRequest`.
- `enqueue`.
- `continuationState`.
- `fetchingAccount`.
- `normalizeThreadError`.
- `ChildWork` and `ThreadAcquisition`.
- All thread constants, including the response byte limit.

Keep in `MisskeySource`:

- The `SocialSource.threadContext` method signature.
- The call to `request` so source errors use the existing Misskey-wide normalization.
- Construction and injection of the service.
- The protocol capability state and capability refresh policy.

The service must receive `origin`, `token`, `api`, `accountId`, `sessionRevision`, and `clock`. It must retain its own continuation store. It must not expose the store or convert the opaque token into a UI value. The service must rethrow cancellation at every current cancellation boundary.

There is no current Misskey thread capability check in `threadContext`. Do not add one as part of extraction. A capability change would alter behavior rather than move code.

### Mastodon timeline service

Create a focused `MastodonTimelineService`.

Move only:

- Timeline endpoint selection.
- Mastodon unsupported timeline mapping.
- `getPage`.
- `validatePaginationUrl`.
- Status-array parsing and mapping.
- `HttpResponse.linkHeaderCursor` consumption.

Keep in `MastodonSource`:

- `refreshCapabilities()`.
- The membership check against `capabilities.timelines`.
- The outer `request` wrapper and error mapping.
- Source construction and capability ownership.

The service must receive `origin`, `token`, and `api`. It must use `MisskeyApi.get` for origin-relative requests and `getUrl` only after validating absolute cursors. It must not construct a new cursor from a status identifier. The full `Link` URL remains the cursor.

### Mastodon thread service

Create a focused `MastodonThreadService`.

Move:

- Focal status loading.
- Context endpoint loading.
- Four-megabyte response limits.
- Context JSON parsing.
- `JSONArray.toPosts` mapping.
- `parseRefreshHint`.
- Construction of the finished `ThreadContext`.

Keep in `MastodonSource`:

- The `SocialSource.threadContext` signature.
- The thread capability check.
- Continuation session-key validation.
- The outer `request` wrapper.
- Service construction.

The service should not create or consume a continuation store. Mastodon returns the complete context in one response. The facade should continue to reject a continuation from another session before making any request, as the current test requires.

## Tests and Verification Evidence

The shared timeline contract is in `SocialSourceContractTest.kt:36-125`. It verifies cursor use, non-repeated identifiers, empty-page termination, unsupported timeline errors, and capability refresh. `MisskeySourceContractTest` and `MastodonSourceContractTest` provide protocol fixtures.

Misskey-specific timeline evidence is in `MisskeyIntegrationTest.kt`:

- `homeFeedUsesOuterRenoteCursorAndMapsSensitiveMediaAndQuotes` at `565-599` verifies the outer renote cursor and `untilId`.
- `misskeyTimelineRoutesUseNativeEndpoints` at `656-670` verifies Local, Social, and Federated endpoint mapping.
- Capability tests at `601-654` verify disabled, supported, and denied timeline states.

Misskey thread evidence is in `MisskeyIntegrationTest.kt:673-696`. It verifies focal, ancestor, and child requests, mapped ordering, and the three `notes/show` calls followed by `notes/children`.

Mastodon-specific timeline evidence is in `MastodonIntegrationTest.kt:225-242` and `701-722`. It verifies `Link` cursors, `max_id` request reuse, bearer authentication, and rejection of foreign hosts, changed ports, and scheme changes before any request.

Mastodon thread evidence is in `MastodonIntegrationTest.kt:184-222`. It verifies both canonical endpoints, ordered context mapping, refresh-hint conversion, and rejection of a continuation from another session without a request.

The profile contract is separate. `ProfileSourceContractTest.kt` proves that profile timeline extraction already works through `MisskeyProfileService` and `MastodonProfileService`. It should not move as part of this source-service extraction.

`PostThreadViewModelTest.kt:153-179` verifies only the consumer contract with a fake source. It should remain unchanged. The UI must continue to see only `SocialSource` and `ThreadContext`.

## Risks and Required Checks

- A Misskey continuation token must remain single-use and session-bound.
- A Misskey child-page cursor must use the last raw child identifier, not the last accepted mapped child.
- Misskey raw pages can contain invalid or foreign children without stopping the whole acquisition.
- Misskey cancellation must not become a branch limitation.
- Mastodon absolute cursors must remain origin-validated before bearer authentication.
- Mastodon `Link` URLs must remain opaque and complete.
- Capability refresh and invalidation must remain in the source facade.
- No thread or timeline state belongs in persisted storage.

After extraction, run the focused source integration and contract tests. Then run `gradlew.bat test assembleRelease` as required by the repository guidance. The current exploration did not change source code and did not run the build.
