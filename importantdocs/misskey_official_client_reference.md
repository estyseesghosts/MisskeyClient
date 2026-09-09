# Misskey API Reference for MissKeyClient

## Scope

This document describes the Misskey protocol behavior implemented by MissKeyClient.

It maps client operations to requests, responses, server contracts, and limitations.

It covers the client source and the matching Misskey backend source.

It does not describe every Misskey endpoint.

It does not claim that every Misskey fork exposes the same behavior.

The client source defines actual requests.

The server source defines the contract examined here.

### Source Snapshot

The client repository is `C:\Users\julie\Documents\MissKeyClient`.

The client source snapshot has commit `a24775563a7c8cc49a8f73adb752994214ac29d5`.

The client worktree contains unrelated uncommitted changes.

This reference reflects the source files that were inspected, not only that commit.

The server repository is `C:\Users\julie\Documents\fedifedifedi\misskey`.

The server source snapshot has commit `b3ce198f4c55ace4daeba4cb1858b3a85bc9529b`.

The server commit message is `[skip ci] Update CHANGELOG.md (prepend template)`.

### Terms

`origin` means the normalized HTTPS instance origin.

`token` means the account session token.

`localId` means an account or entity ID inside one Misskey instance.

`i` means the Misskey JSON body token field.

`endpoint` means the path after `/api/`.

`Note` means the server response object for one Misskey post.

`User` means a server account response.

The client treats Misskey IDs as opaque strings.

The client scopes IDs to one connection origin.

## Architecture

`SocialSourceFactory` selects `MisskeySource` when the session protocol is `MISSKEY`.

It supplies the session origin, token, HTTP client, account ID, and cached capabilities.

`MisskeySource` implements the protocol-neutral `SocialSource` boundary.

`MisskeyProfileService` owns account details, profile timelines, relationships, and pinned posts.

`MisskeyApi` owns HTTP transport and WebSocket construction.

`MisskeyMapper` converts Misskey users and notes into domain models.

`MisskeyNotificationMapper` converts REST, stream, and push notifications.

`UnifiedPushRegistrationManager` connects Android push delivery to the source.

`NotificationSyncOrchestrator` remains the freshness authority.

Sockets provide hints and fast events.

REST reconciliation continues after socket events and socket reconnects.

## Transport

### Instance Validation

`ServerAddress.normalize` trims the supplied instance value.

It adds `https://` when the value has no scheme.

It requires the HTTPS scheme.

It rejects user information, paths, queries, and fragments.

It removes the trailing root slash.

The resulting value is the origin used in all API URLs.

Example origin: `https://misskey.example`.

### JSON Requests

`MisskeyApi.post` builds this URL:

```text
<origin>/api/<endpoint>
```

It sends `Accept: application/json`.

It sends `User-Agent: Palustris/0.1 (Android)`.

It sends an `application/json; charset=utf-8` body.

Every authenticated `MisskeySource` JSON request places the token in `i`.

The client does not add an `Authorization` header to these JSON requests.

The client uses `POST` for `meta`, even when the server permits unauthenticated access.

The client uses `POST` for every API endpoint in the adapter.

### Other Transport Methods

`MisskeyApi` also defines form, multipart, `GET`, `PATCH`, `PUT`, and `DELETE` methods.

The Misskey adapter does not use those methods for its current operations.

`postMultipart` is used by the Mastodon adapter, not the Misskey adapter.

`patchMultipart` is available for future Misskey profile media support.

The Misskey adapter does not implement `uploadMedia`.

### Redirects and Timeouts

The default OkHttp client disables HTTP redirects.

It disables SSL redirects.

This prevents authenticated requests from forwarding tokens to another host.

The connect timeout is 15 seconds.

The read timeout is 30 seconds.

The total call timeout is 40 seconds.

Coroutine cancellation cancels the underlying OkHttp call.

The WebSocket also requires an HTTPS origin.

The WebSocket builder rejects user information in the origin.

### Response Handling

The transport reads the complete response body as text.

A successful empty response becomes an empty string.

The server returns HTTP 204 when an endpoint returns no value.

For a non-success response, the transport reads `error.code` when available.

It raises `ApiFailure` with the HTTP status and API code.

The client maps HTTP 401 and 403 to `SourceError.Unauthorized`.

It maps HTTP 429 to `SourceError.RateLimited`.

It maps HTTP 404 to `SourceError.Unsupported`.

It maps other API failures to `SourceError.ServerError`.

It maps I/O failures to `SourceError.NetworkUnavailable`.

The mapper does not preserve individual server error categories for the UI.

The push path handles selected error codes before the general mapping.

### Server Request Rules

Misskey reads the query for `GET` requests.

Misskey reads the body for non-`GET` requests.

The server accepts `Authorization: Bearer <token>`.

Otherwise, the server reads the token from the body field `i`.

The Bearer scheme check is case-sensitive.

The server authenticates before endpoint execution.

The server validates endpoint parameters after authentication and rate limiting.

Server errors use an envelope like this:

```json
{
  "error": {
    "message": "...",
    "code": "...",
    "id": "...",
    "kind": "..."
  }
}
```

The server returns 401 for missing credentials on credentialed endpoints.

The server returns 403 for permission failures and secure endpoint failures.

The server returns 429 for rate-limit failures.

The server returns 400 for invalid client parameters.

Endpoint metadata supplies the permission kind.

An app token must include that kind.

`secure: true` requires a native user token.

An app or MiAuth access token fails a secure endpoint before endpoint execution.

## Authentication

### Instance Check

`MisskeyAuth.prepare` normalizes the instance origin.

It calls `POST /api/meta` with `{ "detail": false }`.

The call does not include a token.

The client requires a nonblank `version` in the response.

The version check establishes Misskey compatibility.

The client then creates a pending login record.

The pending record expires after 15 minutes.

### MiAuth Browser Flow

`MisskeyAuth.browserUrl` builds this path:

```text
<origin>/miauth/<session>
```

It adds `name=Palustris`.

It adds callback `palustris://auth/misskey`.

It requests these permissions:

```text
read:account,write:account,write:notes,read:notifications,write:notifications,write:following,write:reactions,read:favorites,write:favorites
```

The browser flow is handled by the Misskey frontend route `/miauth/:session`.

The client does not call `miauth/gen-token`.

The browser flow creates the session token after user approval.

### MiAuth Completion

`MisskeyAuth.complete` calls this non-API route:

```text
POST /miauth/<session>/check
```

The request body is an empty JSON object.

The client requires a fresh pending login.

The server finds the session token by session ID.

The server marks the token as fetched.

The server returns `{ "ok": true, "token": "...", "user": { ... } }` once.

The server returns `{ "ok": false }` when approval is incomplete or already fetched.

The client persists the returned user together with the token.

It does not make a second user request before persistence.

This avoids losing a single-use check result.

The client sets `canPublish` to true after successful MiAuth completion.

The client marks every requested logical access scope as granted.

Those logical grants describe the requested MiAuth permissions.

They do not bypass server endpoint permission checks.

### Credential Classes

The server recognizes native user tokens.

It recognizes app access tokens.

It recognizes MiAuth access tokens.

The server represents app and MiAuth credentials as access-token records.

The server represents a native user token without an access-token record.

The server sets `isSecure` only when a user exists without an access-token record.

Therefore, current MiAuth sessions cannot call `secure: true` endpoints.

This affects Misskey push registration.

## Capabilities

### Probe

`MisskeyCapabilityProbe` calls `POST /api/meta` without a token.

It requires a nonblank `version`.

It reads `disableLocalTimeline`.

It reads `disableGlobalTimeline`.

Home is always included.

Local and Social are omitted when local timelines are disabled.

Federated is omitted when the global timeline is disabled.

The probe advertises all four Misskey audiences.

It advertises reply, reshare, favorite, reaction, and bookmark actions.

It advertises quotes.

It advertises reactions as the primary favourite mode.

It advertises native Misskey favourites as saved posts.

It advertises profile reading and display name or biography updates.

It advertises the emoji catalog and reaction mutation.

It advertises Web Push only when `meta.swPublickey` is nonblank.

The probe does not test every endpoint.

### Cache

Capabilities use a five-minute freshness period.

The cache key contains the origin and account ID.

Anonymous capability state uses a synthetic anonymous account ID.

The client preserves selected session capabilities when a fresh probe returns unknown fields.

A timeline HTTP 404 or `NOT_SUPPORTED` invalidates the capability cache.

The next timeline request probes again.

## Endpoint Inventory

Every request body below omits the common token field.

Authenticated requests also contain `"i": "<session-token>"`.

The placeholder is documentation text, not a real credential.

### Discovery and Read Operations

| Client operation | Endpoint | Additional request fields | Response used by client | Server contract |
| --- | --- | --- | --- | --- |
| Instance compatibility | `POST /api/meta` | `detail` when requested | `version`, timeline flags, `swPublickey` | Public. `detail` defaults to true. |
| Load one note | `POST /api/notes/show` | `noteId` | One `Note` object | Public. The response includes detailed relations. |
| Load one user | `POST /api/users/show` | `userId` | One `UserDetailed` object | Public. `userId`, `username`, or `userIds` are accepted. |
| Search one account | `POST /api/users/show` | `username`, optional `host` | One `UserDetailed` object | Public. Remote resolution can fail. |
| Account timeline | `POST /api/users/notes` | `userId`, `limit=40`, tab flags, optional `untilId` | Array of `Note` objects | Public. `limit` accepts 1 through 100. |
| Account relationship | `POST /api/users/relation` | `userId` | Relationship object or one-item array | Credentialed. Requires `read:account`. |
| Pinned account posts | `POST /api/users/show` | `userId` | Inline `pinnedNotes` or ID fields | Public user response can expose inline posts or IDs. |
| Pinned post fallback | `POST /api/notes/show` | `noteId` | One `Note` object | Public. The client loads at most 20 IDs. |
| Resolve moved account | `POST /api/ap/show` | `uri` | `{ type: "User", object: UserDetailedNotMe }` | Credentialed. Requires `read:account`. |
| Search hashtag | `POST /api/notes/search-by-tag` | `tag`, `limit=30`, optional `untilId` | Array of `Note` objects | Public. The server also supports complex tag queries. |
| Thread descendants | `POST /api/notes/children` | `noteId`, `limit=30` | Array of `Note` objects | Public. `limit` accepts 1 through 100. |
| Saved posts | `POST /api/i/favorites` | `limit=30`, optional `untilId` | Array of `NoteFavorite` objects | Credentialed. Requires `read:favorites`. |
| Emoji catalog | `POST /api/emojis` | None | `{ emojis: EmojiSimple[] }` | Public. `GET` is also allowed. |

The client sends the token on public endpoints when the call goes through `MisskeySource`.

The token lets the server pack account-specific fields.

### Timeline Requests

`MisskeySource.timeline` maps domain timelines as follows.

| Domain timeline | Endpoint | Client body |
| --- | --- | --- |
| `Home` | `notes/timeline` | `limit=30`, optional `untilId` |
| `Local` | `notes/local-timeline` | `limit=30`, optional `untilId` |
| `Social` | `notes/hybrid-timeline` | `limit=30`, optional `untilId` |
| `Federated` | `notes/global-timeline` | `limit=30`, optional `untilId` |

The server returns arrays of `Note` objects.

The client maps the last returned outer note ID to `nextCursor`.

The client uses the outer renote ID for pagination.

It does not use the displayed nested note ID.

The server accepts `sinceId`, `untilId`, `sinceDate`, and `untilDate`.

The client uses only `untilId` for timeline pagination.

The home timeline requires `read:account`.

The local, hybrid, and global endpoints use server policy checks.

The client capability probe hides disabled local, hybrid, or global timelines.

A server can still reject an advertised timeline after settings change.

The client invalidates cached capabilities after a 404 or `NOT_SUPPORTED` result.

### Profile Timeline Requests

The client always requests 40 profile notes.

The server accepts a maximum of 100.

The client sends these tab flags:

| Profile tab | `withReplies` | `withRenotes` | `withFiles` |
| --- | ---: | ---: | ---: |
| Posts | false | false | false |
| Media | false | false | true |
| Reposts | false | true | false |
| Replies | true | false | false |

The client adds `userId` and optional `untilId`.

The server returns a note array.

The client maps the last server note ID to `nextCursor`.

The client applies `matchesProfileTimeline` after mapping.

This second filter protects domain tab semantics when server flags include mixed rows.

### Account Search

The client accepts one exact webfinger-style input.

It trims the input.

It removes one leading `@`.

It splits the input on `@`.

It accepts a username alone or a username with one host.

It sends `username` to `users/show`.

It sends `host` for a remote host.

It omits `host` when the host matches the current instance.

The client returns a one-item account list.

The server can resolve a remote user during this request.

The server can return `FAILED_TO_RESOLVE_REMOTE_USER`.

### Hashtag Search

The client accepts one exact hashtag.

It trims one leading `#`.

It rejects whitespace and punctuation outside letters, numbers, marks, and underscores.

It sends only the server `tag` form.

It does not use the server `query` form.

It requests 30 notes.

It sends `untilId` for later pages.

The server supports `reply`, `renote`, `withFiles`, `poll`, date cursors, and ID cursors.

The client does not expose those filters through this method.

### Thread Loading

The server has no single thread endpoint used by this client.

The client first loads the requested root with `notes/show`.

It follows `replyTo` links with repeated `notes/show` calls.

It reverses the collected ancestors.

It loads up to 30 direct children with `notes/children`.

It combines ancestors, root, and descendants.

It removes duplicate IDs.

The descendant request is one page.

The client does not recursively load all descendant pages.

## Publishing and Post Actions

### Create Post

`MisskeySource.create` rejects nonempty attachments before any network request.

The request uses `POST /api/notes/create`.

The client sends these fields:

| Domain value | Misskey field | Client behavior |
| --- | --- | --- |
| Text | `text` | Always sends the text value. |
| Audience | `visibility` | Maps to `public`, `home`, `followers`, or `specified`. |
| Content warning | `cw` | Sends only when nonnull. |
| Reply target | `replyId` | Sends only when present. |
| Quote target | `renoteId` | Sends only for a same-origin target. |
| Poll choices | `poll.choices` | Sends the list as supplied. |
| Poll multiplicity | `poll.multiple` | Sends the boolean. |
| Poll expiry | `poll.expiresAt` | Sends epoch milliseconds when present. |

The client does not send `visibleUserIds` for a Direct post.

The client does not send `fileIds` or `mediaIds`.

The client does not send `localOnly`.

The client does not send reaction acceptance settings.

The server requires text for a text-only post.

The server accepts two through ten poll choices.

The server accepts up to 16 file IDs.

The server requires `write:notes`.

The server limits this endpoint to 300 calls per hour.

The server returns `{ createdNote: Note }`.

The client maps `createdNote` and returns it.

Relevant server error codes include `NO_SUCH_REPLY_TARGET`, `NO_SUCH_RENOTE_TARGET`, `NO_SUCH_FILE`, `CANNOT_RENOTE_DUE_TO_VISIBILITY`, and `CONTAINS_PROHIBITED_WORDS`.

### Reply and Quote Semantics

The client represents a reply with `replyId`.

The client represents a quote with text plus `renoteId`.

The quote target must use the current Misskey origin.

Cross-origin quote targets raise `SourceError.Unsupported`.

The server treats `renoteId` without text, files, or a poll as a pure renote.

### Renote

`renote` calls `POST /api/notes/create` with `renoteId`.

The server returns the created note wrapper.

The simple `renote` method ignores that response.

`setReshared` parses the returned `createdNote`.

It returns the new outer renote ID as `createdRepostId`.

### Undo Renote

`unrenote` requires `ownRepostId`.

It rejects the operation when that ID is absent.

It calls `POST /api/notes/delete` with the owned renote ID.

The server also exposes `POST /api/notes/unrenote`.

The client does not call that endpoint.

The server `notes/unrenote` endpoint searches for all of the current user renotes of a target.

The client instead deletes one known owned renote.

### Reactions

`react` calls `POST /api/notes/reactions/create`.

It sends `noteId` and the exact reaction submission value.

The client trims a raw string reaction.

It rejects a blank reaction.

It preserves custom emoji identities.

It does not convert a custom emoji into a display shortcode.

The server requires `write:reactions`.

The server can return `ALREADY_REACTED`, `YOU_HAVE_BEEN_BLOCKED`, or `CANNOT_REACT_TO_RENOTE`.

`removeReaction` calls `POST /api/notes/reactions/delete`.

It sends only `noteId`.

The server removes the current user reaction for that note.

The client ignores the supplied emoji when removing a reaction.

The server can return `NOT_REACTED`.

The delete endpoint allows 60 calls per hour.

It also enforces a three-second minimum interval.

The client does not call `POST /api/notes/reactions` to list reactions.

It reads reaction counts from the `reactions` object inside each `Note`.

### Favourite and Bookmark Semantics

Misskey distinguishes reactions from native favourites.

The client maps domain `favorite` to a reaction.

The default favourite reaction is the configured heart reaction.

The source stores that value as a reaction submission identity.

The client maps domain `save` to native Misskey favourites.

`save` calls `POST /api/notes/favorites/create`.

`unsave` calls `POST /api/notes/favorites/delete`.

Both endpoints require `write:favorites`.

`favorite` does not call `notes/favorites/create`.

`unfavorite` does not call `notes/favorites/delete`.

`savedPosts` calls `POST /api/i/favorites`.

It requests 30 entries.

It sends `untilId` for later pages.

The server response normally wraps the note inside `note`.

The client also accepts an unwrapped note fallback.

It marks mapped saved posts with `saved=true`.

The client uses the wrapper entry ID as the next cursor.

### Delete Post

`delete` calls `POST /api/notes/delete` with `noteId`.

The server requires `write:notes`.

The server permits the author or a moderator to delete the note.

The client does not call `validatePostId` in this method.

Other post actions reject IDs from another origin before network access.

## Profile Operations

### Load Editable Profile

`loadEditableProfile` calls `POST /api/i`.

It sends only `i`.

The server requires `read:account`.

The client reads `name` and `description`.

It uses the authenticated local account ID.

It ignores the many other fields in `MeDetailed`.

### Update Editable Profile

`updateEditableProfile` calls `POST /api/i/update`.

The client supports only `displayName` and `biography`.

It maps them to `name` and `description`.

It sends only fields that are nonnull in the patch.

It rejects patches that change any other domain field.

The server requires `write:account`.

The server allows up to 20 calls per hour.

The server accepts avatar and banner IDs.

The client does not expose those fields through this method.

The server accepts profile fields.

The client does not send profile fields.

The server returns `MeDetailed`.

The client reads only `name` and `description` from that response.

### Profile Details

`profile` calls `users/show` with `userId`.

The source validates the target origin before the request.

The mapper reads `username`, `host`, `name`, `avatarUrl`, `description`, counts, lock state, bot state, banner URL, fields, and emojis.

The mapper uses the current instance host when `host` is null.

The mapper keeps at most four profile fields.

If `movedTo` is present, the client resolves it.

An HTTP-like `movedTo` value uses `ap/show` with `uri`.

The client requires an `ap/show` response type of `User`.

An ID-like `movedTo` value uses `users/show` with `userId`.

Move resolution failures become a null `movedTo` value.

### Relationships

`profileRelationship` calls `POST /api/users/relation`.

It sends `userId`.

The server returns an object or an array.

The client accepts either form.

It also unwraps `relation` or `relationship` when present.

It maps both current and legacy field names.

The mapped fields include following, followed-by, pending request, muting, and blocking.

The client requires at least one relationship field in the response.

`followProfile` calls `POST /api/following/create`.

`unfollowProfile` calls `POST /api/following/delete`.

Both send `userId`.

Both call `users/relation` again after the mutation.

The mutation responses are ignored.

Both mutation endpoints require `write:following`.

Each allows 100 calls per hour.

Relevant errors include `ALREADY_FOLLOWING`, `NOT_FOLLOWING`, `BLOCKING`, and `BLOCKED`.

### Pinned Posts

`pinnedPosts` first calls `users/show`.

It accepts inline `pinnedNotes` objects.

It also accepts `pinnedNotes` string IDs.

It also accepts `pinnedNoteIds`.

For ID lists, it calls `notes/show` for at most 20 IDs.

It ignores malformed individual pinned posts.

It keeps posts whose mapped author matches the requested account.

HTTP 400, 404, and 422 from the profile request produce an empty list.

## Notification Synchronization

### Query Model

The source converts domain notification categories into Misskey types.

| Domain category | Misskey include types |
| --- | --- |
| All | No `includeTypes` field |
| Mentions | `mention`, `reply` |
| Replies | `reply` |
| Quotes | `quote` |
| Social | `note`, `renote`, `reaction`, `follow`, `receiveFollowRequest`, `followRequestAccepted` |
| Polls | `pollEnded` |
| System | `scheduledNotePosted`, `scheduledNotePostFailed`, `roleAssigned`, `achievementEarned`, `exportCompleted`, `login`, `createToken`, `app`, `test`, `chatRoomInvitationReceived` |

The client removes duplicate type values.

An empty non-All category query returns an empty page without a server call.

The client does not send `excludeTypes`.

### REST Listing

`notifications` selects one endpoint.

| Query mode | Endpoint |
| --- | --- |
| Ungrouped | `POST /api/i/notifications` |
| Grouped | `POST /api/i/notifications-grouped` |

The request includes `limit`.

The request includes `markAsRead=false`.

The request includes `includeTypes` for non-All queries.

Older pagination sends `untilId`.

Newer pagination sends `sinceId`.

Newer pagination can also send `untilId` to bound a stable catch-up window.

The server defaults `markAsRead` to true.

The client explicitly disables that default.

This prevents a background read from acknowledging notifications.

Both listing endpoints require `read:notifications`.

Both endpoints allow 30 calls per 30 seconds.

The endpoint schema allows a limit from 1 through 100.

The client applies its own query limit.

The server returns an array of `Notification` objects.

The grouped endpoint can return `reaction:grouped` and `renote:grouped` objects.

The server groups consecutive reactions for one note.

The server groups consecutive renotes for one target note.

### Opaque Cursors

Misskey uses IDs for REST notification pagination.

The client wraps the raw ID in a URL-safe Base64 JSON cursor.

The encoded data contains the origin.

It contains the account local ID.

It contains a query fingerprint.

It contains the cursor direction.

It contains the raw Misskey ID.

The client rejects a cursor for another account.

It rejects a cursor for another query.

It rejects a cursor for another direction.

The UI receives the opaque cursor.

The UI does not construct Misskey pagination values.

### Baseline and Catch-Up

`NotificationSynchronizer` establishes a baseline when no checkpoint exists.

It stores the initial page.

It then reads unread state.

The orchestrator polls every 60 seconds.

It uses newer cursors for catch-up.

It allows four pages per catch-up cycle.

It marks a cycle delayed when more pages remain.

It schedules delivery after synchronization.

The foreground stream does not replace REST reconciliation.

The durable worker also uses REST catch-up.

### Unread State

`notificationUnreadState` calls `POST /api/i`.

It reads `notificationCount` when present.

It returns an exact nonnegative count.

If that field is absent, it reads `hasUnreadNotification`.

It returns `Present` or `None` from that boolean.

It returns `Unknown` when neither field is present.

The client does not call a separate unread-count endpoint.

### Acknowledgement

`acknowledgeNotifications` calls `POST /api/notifications/mark-all-as-read`.

It sends only the common token field.

The server requires `write:notifications`.

The server returns HTTP 204.

The client returns `NotificationUnreadState.None` locally.

The client records the local acknowledgement time.

The server operation is account-wide.

The client does not implement single-notification dismissal.

### Follow Request Actions

The client responds to a follow request from a notification action.

Accept calls `POST /api/following/requests/accept`.

Reject calls `POST /api/following/requests/reject`.

Both send `userId` for the requesting follower.

Both require `write:following`.

Both return HTTP 204 on success.

The client does not call `following/requests/list`.

## Streaming

### Connection

`MisskeySource.streamEvents` opens a WebSocket at `/streaming`.

OkHttp upgrades the HTTPS request to WebSocket transport.

The client sends `Authorization: Bearer <session-token>`.

The server accepts a Bearer token during the WebSocket upgrade.

The server also accepts query parameter `i`.

The client does not put the token in the query.

### Main Channel Request

On open, the client sends:

```json
{
  "type": "connect",
  "body": {
    "channel": "main",
    "id": "notifications",
    "params": {
      "i": "<session-token>"
    }
  }
}
```

The server requires the `main` channel to have a credential.

The server requires `read:account` for an app token.

The channel subscribes to the account main stream.

The server permits at most 32 channels per connection.

### Received Messages

The server wraps channel events in a channel message.

The client accepts a message whose outer type is `channel`.

It reads the nested body type.

It maps nested `notification` events to domain notifications.

It maps nested `readAllNotifications` events to a read state event.

It turns other nested event types into a refresh hint.

The client ignores malformed messages.

Malformed messages generate a refresh hint.

Socket failure closes the source flow.

Socket closure also closes the source flow.

The foreground controller reconnects with exponential backoff.

The backoff starts at one second.

It caps at 60 seconds.

The controller performs REST refresh after a disconnect.

### Readiness Compatibility Note

The client expects a `connected` message with ID `notifications`.

The server sends that acknowledgement only when the connect request has `pong=true`.

The current client connect body omits `pong`.

Therefore, this server does not emit the readiness acknowledgement for the current request.

The client can still process channel events.

The controller remains in its not-ready path until the socket closes.

The controller labels a resulting disconnect as `stream_not_ready`.

This is an observed client-server mismatch.

### Server Main Events

The server main stream can publish notifications, mentions, replies, renotes, follows, follow events, account updates, read events, and chat messages.

The client directly maps only notification and read-all events.

Other events cause notification refresh.

The client does not subscribe to note streams.

It does not subscribe to timeline channels.

## Push Notifications

### Provider Discovery

`pushProviderInfo` calls `POST /api/meta`.

It reads `swPublickey`.

A nonblank value marks the provider as supported.

An absent value marks it unsupported.

The client does not expose the server private key.

### Registration Flow

UnifiedPush supplies a distributor endpoint.

The callback also supplies a public key and authentication secret.

The client validates the endpoint as HTTPS.

It stores the callback state encrypted with the account session.

After the callback, the source calls `POST /api/sw/register`.

The request contains these fields:

| Field | Value |
| --- | --- |
| `endpoint` | UnifiedPush endpoint URL |
| `auth` | UnifiedPush authentication secret |
| `publickey` | UnifiedPush public key |
| `sendReadMessage` | `false` |

The server declares this endpoint `secure: true`.

The server requires `endpoint`, `auth`, and `publickey`.

The response contains state, a server key, user ID, endpoint, and read-message policy.

The client validates the returned endpoint.

It stores the returned `key` as an optional remote ID.

It does not use the returned server key.

If a previous subscription exists, the client removes it after the new subscription succeeds.

### Subscription Query

The client calls `POST /api/sw/show-registration` when it has a known endpoint.

The request contains `endpoint`.

The server declares this endpoint `secure: true`.

The server returns a matching registration or null.

The client accepts HTTP 404 and selected missing-registration codes as null.

### Policy Update

The client calls `POST /api/sw/update-registration`.

The request contains `endpoint` and `sendReadMessage=false`.

The server declares this endpoint `secure: true`.

The source receives an `alerts` set but does not translate it into the request.

Misskey therefore receives no per-category alert policy from this method.

The returned endpoint must match the requested endpoint.

### Removal

The client verifies the current server registration before removal.

It calls `POST /api/sw/unregister` with `endpoint`.

The server permits unauthenticated removal by endpoint.

The client still sends the session token.

The client ignores missing-registration errors during removal.

The source validates the subscription account before this request.

### Secure Credential Limitation

The Misskey server marks registration, lookup, and update as secure.

The server accepts those endpoints only for a native user token.

MiAuth returns an access-token record.

The server therefore rejects current MiAuth sessions with `ACCESS_DENIED`.

The source maps selected 403 credential failures to `UnsupportedCredential`.

The push manager records that credential limitation as unsupported.

The current MissKeyClient MiAuth flow cannot complete native Misskey push registration.

### Push Payloads

The server sends JSON with `type`, `body`, `userId`, and `dateTime`.

The supported Misskey types are `notification`, `readAllNotifications`, and `newChatMessage`.

The server can also send `unreadAntennaNote`.

The current parser treats that unlisted type as a refresh.

The server may truncate notification note data.

It removes content warnings, replies, and nested renotes from truncated notes.

The client accepts payloads up to 64 KiB.

It rejects payload trees deeper than eight levels.

It rejects payload trees larger than 256 nodes.

Invalid or unknown payloads schedule REST catch-up.

`readAllNotifications` clears local notification presentation after the repository accepts it.

`notification` and `newChatMessage` are mapped into local notifications.

Each accepted push schedules delivery and REST catch-up.

The server sends read-all push messages only when `sendReadMessage` is true.

The client always registers that setting as false.

Therefore, read-all push messages are not expected from current client registrations.

## Response Mapping

### Account Mapping

`MisskeyMapper.account` requires `id` and `username`.

It builds an account ID from the current Misskey origin and the raw ID.

It uses `host` when supplied.

It uses the current instance host when `host` is null.

It builds handles as `@username@host`.

It uses `name` or the username as the display name.

It maps `avatarUrl`, `bannerUrl`, and `description`.

It maps nonnegative `followersCount`, `followingCount`, and `notesCount`.

It maps `isLocked` and `isBot`.

It maps at most four profile fields.

It reads user custom emoji metadata from `emojis`.

### Note Mapping

`MisskeyMapper.post` requires `id` and `user`.

It maps the raw ID to the current origin.

It maps the nested user through the account mapper.

It parses `createdAt` as an ISO timestamp.

Invalid dates become epoch zero.

It maps visibility values as follows:

| Misskey visibility | Domain audience |
| --- | --- |
| `public` | `Public` |
| `home` | `Unlisted` |
| `followers` | `Followers` |
| `specified` | `Direct` |

It maps `text`, `cw`, `replyId`, and reply author ID.

It maps `repliesCount` and `renoteCount`.

It maps attachments from `files`.

It maps `type` to a domain media kind.

It maps `thumbnailUrl` to the preview URL.

It maps `comment` to the attachment description.

It maps `isSensitive`, dimensions, and blurhash when present.

It maps poll choices to text and vote counts.

It does not map poll expiry or multiple-choice state.

It maps reaction counts from the `reactions` object.

It maps `myReaction` as the selected reaction.

It reads emoji metadata from `reactionEmojis` and `emojis`.

It maps `isFavorited` or `isBookmarked` to `saved`.

It maps `myRenoteId` to repost state and owned repost ID.

It advertises reply, reshare, favorite, reaction, and bookmark actions.

### Pure Renote Mapping

A pure renote has a nested `renote` and no text, files, poll, or content warning.

The mapper displays the nested note content.

It keeps the outer renote ID as the displayed row ID.

It sets `resharedBy` from the outer note user.

It maps `myRenoteId` to the owned outer repost ID.

It sets the action target to the displayed note ID.

Nested mapping stops after three levels.

### Emoji Mapping

The client reads custom emoji maps from users and notes.

It reads note reaction metadata from `reactionEmojis`.

It reads the public catalog from `/api/emojis`.

It validates emoji media URLs before retaining metadata.

It indexes raw submission identities.

It also indexes display names and aliases for lookup.

It never replaces the raw submission identity during a reaction request.

The display key exists only for metadata resolution.

### Notification Mapping

The notification mapper preserves the raw Misskey `type`.

It maps `id` to the current origin.

It accepts ISO and numeric time values.

It reads `createdAt` first.

It falls back to `dateTime` for push-shaped data.

It maps these activity types:

| Misskey type | Domain activity |
| --- | --- |
| `note` | Subscribed post |
| `mention` | Mention |
| `reply` | Reply |
| `renote` | Reshare |
| `quote` | Quote |
| `follow` | Follow |
| `receiveFollowRequest` | Follow request |
| `followRequest` | Follow request compatibility alias |
| `followRequestAccepted` | Accepted request |
| `pollEnded` | Poll result |
| `scheduledNotePosted` | Subscribed post |
| `scheduledNotePostFailed` | System app event |
| `app` | System app event |
| `achievementEarned` | System role or achievement |
| `roleAssigned` | System role or achievement |
| `moderation` | System moderation event |
| `relationship` | System relationship change |
| `exportCompleted` | System app event |
| `login` | System app event |
| `createToken` | System app event |
| `test` | System app event |
| `chatRoomInvitationReceived` | Unknown unsupported display |
| Other | Unknown activity |

It maps `reaction` and `reaction:grouped` to emoji reactions.

It resolves reaction metadata from `customEmoji`, `emoji`, or mapped note metadata.

It maps grouped reaction actors from the `reactions` array.

It maps grouped renote actors from the `users` array.

It creates a group ID from account, type, and target ID.

It maps post targets from `noteId`, `targetNoteId`, or nested note data.

It maps profile targets for follows and follow requests.

It maps chat messages to conversation notifications.

## Unsupported or Partial Features

The following `SocialSource` operations remain unsupported or partial in `MisskeySource`.

| Domain operation | Current Misskey behavior |
| --- | --- |
| Upload media | Unsupported. The source rejects attachments during post creation. |
| Edit post | Unsupported. |
| General text search | Unsupported. |
| Poll voting | Unsupported, although the server exposes `notes/polls/vote`. |
| Notification dismissal | Unsupported. |
| Notification list retrieval | Implemented through the newer query API. |
| Native favourite action | Implemented as a reaction instead. |
| Reaction listing | Not requested separately. Note reaction fields supply counts. |
| Full profile editing | Only display name and biography are supported. |
| Cross-origin quotes | Unsupported. |
| Undo renote without owned repost ID | Unsupported. |
| Mute post | Unsupported. |
| Block account | Unsupported. |
| Per-category push policy | Not translated to Misskey fields. |
| MiAuth push registration | Rejected by secure server endpoints. |

The Misskey server exposes `drive/files/create` for multipart upload.

The client does not call it.

The Misskey server exposes `notes/polls/vote`.

The client does not call it.

The Misskey server exposes `notes/unrenote`.

The client does not call it.

The Misskey server exposes `notes/reactions`.

The client does not call it.

## Source References

### Client

`app/src/main/java/me/foxtails/palustris/data/SourceFactory.kt`

`app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyApi.kt`

`app/src/main/java/me/foxtails/palustris/data/misskey/MisskeySource.kt`

`app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyProfileService.kt`

`app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyCapabilityProbe.kt`

`app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyMapper.kt`

`app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyNotificationMapper.kt`

`app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyEmojiMapper.kt`

`app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyErrorMapper.kt`

`app/src/main/java/me/foxtails/palustris/data/auth/MisskeyAuth.kt`

`app/src/main/java/me/foxtails/palustris/data/notifications/NotificationSynchronizer.kt`

`app/src/main/java/me/foxtails/palustris/data/notifications/NotificationSyncOrchestrator.kt`

`app/src/main/java/me/foxtails/palustris/data/notifications/ForegroundNotificationStreamController.kt`

`app/src/main/java/me/foxtails/palustris/data/notifications/work/NotificationWorkers.kt`

`app/src/main/java/me/foxtails/palustris/data/notifications/work/PushRegistrationWorker.kt`

`app/src/main/java/me/foxtails/palustris/data/notifications/push/UnifiedPushRegistrationManager.kt`

`app/src/main/java/me/foxtails/palustris/data/notifications/push/MisskeyPushPayloadParser.kt`

`app/src/main/java/me/foxtails/palustris/data/notifications/push/MisskeyPushPayload.kt`

`app/src/main/java/me/foxtails/palustris/data/auth/SessionStore.kt`

`app/src/main/java/me/foxtails/palustris/domain/SocialSource.kt`

`app/src/main/java/me/foxtails/palustris/domain/SocialModels.kt`

### Server

`packages/backend/src/server/api/ApiCallService.ts`

`packages/backend/src/server/api/AuthenticateService.ts`

`packages/backend/src/server/api/ApiServerService.ts`

`packages/backend/src/server/api/StreamingApiServerService.ts`

`packages/backend/src/server/api/endpoint-list.ts`

`packages/backend/src/server/api/stream/Connection.ts`

`packages/backend/src/server/api/stream/channels/main.ts`

`packages/backend/src/core/GlobalEventService.ts`

`packages/backend/src/core/PushNotificationService.ts`

`packages/backend/src/models/json-schema/notification.ts`

`packages/backend/src/types.ts`

The endpoint source files are under `packages/backend/src/server/api/endpoints`.

Relevant directories include `notes`, `users`, `following`, `i`, `notifications`, `sw`, `ap`, and `miauth`.
