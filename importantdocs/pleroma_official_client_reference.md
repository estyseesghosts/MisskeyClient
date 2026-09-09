# Pleroma API Reference for Pleroma-FE

## Scope

This reference describes the Pleroma APIs that Pleroma-FE calls or consumes.

It records the checked client source and the matching Pleroma server source.

The client source reports version `2.11.1`.

This reference does not describe every Pleroma endpoint.

It describes request methods, parameters, OAuth scopes, response fields, client normalization, and fallbacks.

## Source Map

The client API layer is in `pleroma-fe/src/api`.

- `helpers.js`: request construction, authentication, and query encoding.
- `public.js`: public account, status, search, emoji, and scrobble calls.
- `user.js`: authenticated user actions and Pleroma account calls.
- `timelines.js`: timeline and notification calls.
- `chats.js`: Pleroma chat calls.
- `admin.js`: Pleroma administration calls.
- `oauth.js` and `mfa.js`: OAuth and MFA calls.
- `websocket.js`: Mastodon streaming and Pleroma streaming events.

The server route map is in `pleroma/lib/pleroma/web/router.ex`.

The server API specifications are in `pleroma/lib/pleroma/web/api_spec`.

The server response views are in `pleroma/lib/pleroma/web/*/views`.

The main server references are:

- `docs/development/API/pleroma_api.md`
- `docs/development/API/differences_in_mastoapi_responses.md`
- `docs/development/API/chats.md`
- `docs/development/API/admin_api.md`

## Terms

In this reference, `client` means Pleroma-FE.

`server` means the Pleroma backend.

`status` means a Mastodon API status entity.

`chat message` means a Pleroma `ChatMessage` entity.

`account ID` means a Pleroma Flake ID unless the endpoint says otherwise.

## Transport Rules

### Request URLs

The client uses relative URLs for the current server.

The browser sends these requests to the current origin.

The OAuth authorization URL uses the selected instance URL.

### Query parameters

`paramsString` applies these rules:

- It drops `null` and `undefined` values.
- It converts camelCase names to snake_case.
- It encodes primitive values with `encodeURIComponent`.
- It encodes arrays as repeated `name[]` parameters.
- It rejects objects and functions as parameter values.

Examples:

```text
withRelationships: true -> with_relationships=true
replyVisibility: following -> reply_visibility=following
includeTypes: [mention, reblog] -> include_types[]=mention&include_types[]=reblog
```

The client builds some administration URLs manually.

Those URLs do not receive the automatic camelCase conversion.

### Request bodies

`promisedRequest` uses JSON when the caller supplies `payload`.

It uses `FormData` when the caller supplies `formData`.

The helper sets `Content-Type: application/json` for JSON requests.

The browser sets the multipart content type for `FormData` requests.

The client sends `Accept: application/json` by default.

The helper can force another response type.

### Authentication

The helper sends this header when the caller supplies a token:

```http
Authorization: Bearer <access-token>
```

The helper always uses `credentials: same-origin`.

This also permits same-origin cookies when the server uses cookie authentication.

Pleroma also documents the `_pleroma_key` cookie and HTTP Basic Authentication.

### OAuth scope model

Pleroma checks OAuth scopes at the controller or router level.

The client registers one application with these broad scopes:

```text
read write follow push admin
```

The client requests the same scope string during authorization.

Pleroma can enforce more specific scopes for individual endpoints.

The following table lists the scopes relevant to the client calls.

| Operation | Required scope |
| --- | --- |
| Read account data | `read:accounts` |
| Update account data | `write:accounts` |
| Read statuses and timelines | `read:statuses` |
| Create, edit, boost, and delete statuses | `write:statuses` |
| Read follows | `read:follows` |
| Change follows | `follow` and `write:follows` |
| Read blocks | `read:blocks` |
| Change blocks | `follow` and `write:blocks` |
| Read mutes | `follow` and `read:mutes` |
| Change mutes | `follow` and `write:mutes` |
| Read favourites | `read:favourites` |
| Read bookmarks | `read:bookmarks` |
| Change bookmarks | `write:bookmarks` |
| Read notifications | `read:notifications` |
| Change notifications | `write:notifications` |
| Read media | `read:media` |
| Change media | `write:media` |
| Read and change lists | `read:lists` and `write:lists` |
| Read and change chats | `read:chats` and `write:chats` |
| Read and change conversations | `read:statuses` and `write:conversations` |
| Read and change security settings | `read:security` and `write:security` |
| Read and create backups | `read:backups` |
| Read scrobbles | `read:scrobbles` |
| Create scrobbles | `write:scrobbles` |
| Manage emoji packs | `admin:write` plus the required staff privilege |

Admin API routes also require a staff role and a route-specific privilege.

### Response errors

API calls through `promisedRequest` reject with `StatusCodeError` when the response is not successful.

The error includes the HTTP status, parsed response data, request URL, and request options.

Some direct `window.fetch` calls handle errors locally instead.

## Pleroma Path Compatibility

Pleroma supports both of these prefixes for many older Pleroma endpoints:

```text
/api/v1/pleroma/*
/api/pleroma/*
```

The server reroutes legacy `/api/pleroma/*` requests to the versioned Pleroma API.

The server also reroutes legacy `/api/pleroma/admin/*` requests.

Pleroma-FE still uses both prefixes.

The versioned prefix is preferred for new integrations.

## Capability Discovery

The client does not call every Pleroma extension unconditionally.

It loads capability data before it enables optional features.

### Instance endpoint

```http
GET /api/v1/instance
```

Authentication is not required.

The client reads these Pleroma fields:

- `pleroma`: indicates Pleroma extensions in the instance response.
- `pleroma.metadata.birthday_required`.
- `pleroma.metadata.birthday_min_age`.
- `pleroma.vapid_public_key`.
- `approval_required`.
- `max_toot_chars`.

The client stores the Pleroma extension marker in `pleromaExtensionsAvailable`.

The server also returns Pleroma metadata such as feature names, post formats, upload limits, poll limits, federation data, and instance statistics.

The client reads additional values from `pleroma.metadata` after NodeInfo loads.

### NodeInfo

```http
GET /nodeinfo/2.1.json
GET /nodeinfo/2.0.json
```

The client tries NodeInfo 2.1 first.

It tries NodeInfo 2.0 when the first request fails.

The client reads `metadata.features` and maps feature names to local capability flags.

The important mappings are:

| NodeInfo feature | Client capability |
| --- | --- |
| `chat` | `shoutAvailable` |
| `pleroma_chat_messages` | `pleromaChatMessagesAvailable` |
| `pleroma_custom_emoji_reactions` or `custom_emoji_reactions` | `pleromaCustomEmojiReactionsAvailable` |
| `pleroma:bookmark_folders` | `pleromaBookmarkFoldersAvailable` |
| `quote_posting` | `quotingAvailable` |
| `pleroma:block_expiration` | `blockExpiration` |
| `polls` | `pollsAvailable` |
| `editing` | `editingAvailable` |
| `media_proxy` | `mediaProxyAvailable` |
| `safe_dm_mentions` | `safeDM` |
| `gopher` | `gopherAvailable` |
| `pleroma:group_actors` | `groupActorAvailable` |

The client also reads `metadata.postFormats`, `metadata.pollLimits`, `metadata.uploadLimits`, `metadata.fieldsLimits`, `metadata.suggestions`, `metadata.federation`, and `metadata.staffAccounts`.

The client disables scrobble support after a scrobble request fails.

### Frontend configuration

```http
GET /api/pleroma/frontend_configurations
```

Authentication is not required.

The server returns the configured frontend map.

The client selects the `pleroma_fe` entry.

The client merges this data with `/static/config.json`.

API configuration normally overrides static configuration.

Development overrides can reverse that order.

### Custom emoji

The client first requests:

```http
GET /api/v1/pleroma/emoji
```

Authentication is not required.

The response is a map from shortcode to an object with these fields:

- `image_url`.
- `tags`.

The client falls back to this path when the first request fails:

```http
GET /api/pleroma/emoji.json
```

The fallback accepts the older array format.

The client converts that array into an object before it builds the emoji picker.

The client prefixes relative image paths with the instance URL.

The standard endpoint `GET /api/v1/custom_emojis` is also part of Mastodon compatibility, but this code path uses the Pleroma format.

## Pleroma Response Extensions

### Flake IDs

Pleroma uses 128-bit Flake IDs.

The IDs remain lexically sortable strings.

The client converts account and status IDs to strings in its normalizers.

It preserves pagination IDs as strings when it uses them as Flake cursors.

Clients must not parse Pleroma IDs as JavaScript numbers.

### Account entity

Pleroma adds these fields to the account response.

The exact set depends on the viewer and endpoint.

Common Pleroma fields include:

- `fqn`: the fully qualified nickname.
- `pleroma.ap_id`: the ActivityPub ID.
- `pleroma.also_known_as`: account move aliases.
- `pleroma.background_image`: the account background image.
- `pleroma.tags`: server-side account tags.
- `pleroma.is_confirmed`: account email confirmation state.
- `pleroma.is_suggested`: follower recommendation state.
- `pleroma.is_admin`: administrator state.
- `pleroma.is_moderator`: moderator state.
- `pleroma.privileges`: detailed administrator or moderator privileges.
- `pleroma.hide_favorites`: favorite visibility preference.
- `pleroma.hide_followers`: follower visibility preference.
- `pleroma.hide_follows`: following visibility preference.
- `pleroma.hide_followers_count`: follower count visibility preference.
- `pleroma.hide_follows_count`: following count visibility preference.
- `pleroma.skip_thread_containment`: thread containment preference.
- `pleroma.allow_following_move`: account move preference.
- `pleroma.accepts_chat_messages`: chat acceptance setting.
- `pleroma.favicon`: the remote account instance favicon.
- `pleroma.notification_settings`: notification settings for the account owner.
- `pleroma.settings_store`: frontend-specific settings for the account owner.
- `pleroma.chat_token`: the Shoutbox token for the account owner.
- `pleroma.unread_conversation_count`: unread conversation count for the owner.
- `pleroma.unread_notifications_count`: unread notification count for the owner.
- `pleroma.deactivated`: deactivation state for privileged viewers.
- `pleroma.follow_requests_count`: follow request count for the account owner.

The `source` object contains editable account data for the account owner.

Pleroma adds these `source.pleroma` fields:

- `actor_type`.
- `discoverable`.
- `no_rich_text`.
- `show_role`.
- `show_birthday` for the account owner.
- `accepts_chat_messages` for the account owner.

Pleroma can embed a relationship object in `pleroma.relationship`.

The relationship includes standard fields such as `following`, `followed_by`, `blocking`, `muting`, `requested`, `subscribing`, and `notifying`.

It can also include `mute_expires_at` and `block_expires_at`.

The server returns `pleroma.settings_store` only for the account owner on credential verification and credential updates.

The server returns `pleroma.chat_token` only for the account owner on credential verification.

### Client account normalization

`parseUser` maps the Mastodon account to the client account model.

The client copies `pleroma.settings_store['pleroma-fe']` to `storage`.

It copies `pleroma.settings_store.user_highlight` to `user_highlight`.

It copies `pleroma.chat_token` to `token`.

It maps Pleroma role flags to `rights.admin`, `rights.moderator`, and `role`.

It maps `pleroma.tags` to a JavaScript `Set`.

It maps `pleroma.notification_settings` to the account model.

It maps `pleroma.is_active` to the client `deactivated` flag when present.

It falls back to the older `pleroma.deactivated` field otherwise.

The client stores the original server response in `_original`.

Profile settings read from `_original` use server field names.

### Status entity

Pleroma adds these fields under `status.pleroma`:

- `local`: whether the status originated on the current instance.
- `context`: the ActivityPub thread context.
- `conversation_id`: the older numeric context ID.
- `direct_conversation_id`: the direct-message conversation ID.
- `in_reply_to_account_acct`: the replied account nickname.
- `content`: alternate content representations by MIME type.
- `spoiler_text`: alternate spoiler text representations by MIME type.
- `expires_at`: automatic deletion time, or `null`.
- `thread_muted`: whether the status thread is muted.
- `emoji_reactions`: reaction counts without reacting account details.
- `parent_visible`: whether the parent status is visible.
- `pinned_at`: pin time, or `null`.
- `bookmark_folder`: the bookmark folder ID, or `null`.
- `list_id`: the addressed list ID for the status author.
- `quote`: the quoted status, when visible.
- `quote_id`: the quoted status ID.
- `quote_url`: the quoted status URL.
- `quote_visible`: whether the quoted status is visible.
- `quotes_count`: the quote count in the Pleroma object.

The top-level status can also contain `quotes_count`.

Pleroma accepts the additional visibility values `local` and `list:LIST_ID`.

Pleroma supports 128-bit status IDs as strings.

### Client status normalization

`parseStatus` maps `pleroma.content['text/plain']` to the client `text` field.

It maps `pleroma.spoiler_text['text/plain']` to the client `summary` field.

It maps `pleroma.conversation_id` to `statusnet_conversation_id`.

It does not map `pleroma.direct_conversation_id` to the client status model.

It maps `pleroma.in_reply_to_account_acct` to `in_reply_to_screen_name`.

It copies `thread_muted`, `emoji_reactions`, `parent_visible`, `quotes_count`, and `bookmark_folder`.

It recursively normalizes `pleroma.quote` or the top-level `quote` object.

It reads the quote ID from `quote.id`, `quote_id`, the normalized quote, or `pleroma.quote_id`.

It reads the quote URL from `quote.url`, the normalized quote, or `pleroma.quote_url`.

It maps `pleroma.bookmark_folder` to `bookmark_folder_id`.

It maps Pleroma attachment `pleroma.mime_type` to `mimetype`.

It treats `gifv` attachments as videos.

It maps a chat message attachment to a one-item `attachments` array.

The current normalizer uses `pleroma.quote_visible || true`.

Therefore, a server value of `false` becomes `true` in the client model.

### Notification entity

Pleroma adds `notification.pleroma.is_seen`.

Pleroma supports these additional notification types:

- `pleroma:emoji_reaction`.
- `pleroma:chat_mention`.
- `pleroma:report`.

Move notifications include a `target` account.

Emoji reaction notifications include `emoji`, `account`, and `status`.

Chat mention notifications include `chat_message`.

Report notifications include a `report` object.

The client maps `pleroma.is_seen` to `notification.seen`.

It maps `favourite` to `like` and `reblog` to `repeat`.

It parses status notifications as statuses.

It parses the reporting account and reported statuses in a Pleroma report notification.

## Status Creation and Actions

### Create a status

```http
POST /api/v1/statuses
```

Required scope: `write:statuses`.

The client sends `multipart/form-data`.

The client always sends these fields:

- `status`.
- `source=Pleroma FE`.

The client sends these optional fields:

- `spoiler_text`.
- `visibility`.
- `sensitive`.
- `content_type`.
- `media_ids[]`.
- `poll[expires_in]`.
- `poll[multiple]`.
- `poll[options][]`.
- `in_reply_to_id`.
- `quote_id`.
- `preview=true`.

The client converts the poll expiration to an integer.

The client sends `idempotency-key` as an HTTP header when supplied.

Pleroma supports these additional request fields:

- `preview`: render the status without publishing it.
- `content_type`: MIME type for server-side conversion to HTML.
- `to`: explicit ActivityPub recipients.
- `expires_in`: status expiration time in seconds.
- `in_reply_to_conversation_id`: reply to a Pleroma conversation.
- `quote_id`: older quote parameter.

The current client does not send `to`, `expires_in`, or `in_reply_to_conversation_id`.

The server operation marks `quoted_status_id` as the preferred quote parameter.

The client still sends the older `quote_id` parameter.

The response is a status entity unless `preview=true` changes only the publish behavior.

The client parses the response with `parseStatus`.

### Edit a status

```http
PUT /api/v1/statuses/:id
```

Required scope: `write:statuses`.

The client sends `multipart/form-data` with these fields:

- `status`.
- `spoiler_text` when non-empty.
- `sensitive` when true.
- `content_type` when present.
- `media_ids[]`.
- `poll[expires_in]`.
- `poll[multiple]`.
- `poll[options][]`.

The client parses the returned status.

The server checks that the authenticated account owns the status.

### Get a status

```http
GET /api/v1/statuses/:id
```

Unauthenticated access is permitted when the status is public.

An authenticated request normally uses `read:statuses`.

The client parses the status entity.

The server includes Pleroma fields such as quotes, reactions, expiration, direct conversation data, context, and bookmark folder data.

### Get status context

```http
GET /api/v1/statuses/:id/context
```

Required scope: `read:statuses` when authentication is used.

The response contains `ancestors` and `descendants` arrays.

The client parses every status in both arrays.

### Get status source

```http
GET /api/v1/statuses/:id/source
```

Required scope: `read:statuses`.

The response contains:

- `id`.
- `text`.
- `spoiler_text`.
- `content_type`.

The Pleroma `content_type` field identifies the source format.

The client maps this response to its source model.

### Get status history

```http
GET /api/v1/statuses/:id/history
```

Required scope: `read:statuses`.

The response contains status history entries.

The client reverses the returned array.

It assigns the requested status ID to every history entry.

It then parses every entry as a status.

### Get statuses by IDs

```http
GET /api/v1/statuses?ids[]=<id>&ids[]=<id>
```

Required scope: `read:statuses` when authentication is used.

Pleroma limits this request to 100 IDs.

The current client defines this route in the server, but the inspected API module does not call it directly.

### Get quotes for a status

Pleroma exposes both of these routes:

```http
GET /api/v1/statuses/:id/quotes
GET /api/v1/pleroma/statuses/:id/quotes
```

Required scope: `read:statuses` when authentication is used.

Pleroma documents the versioned Mastodon path as preferred.

Pleroma-FE uses the Pleroma path in `timelines.js`.

The client sends these optional pagination parameters:

- `min_id`.
- `since_id`.
- `max_id`.
- `limit`.
- `reply_visibility`.

The response is an array of status entities.

### Emoji reactions

#### List reactions

```http
GET /api/v1/pleroma/statuses/:id/reactions
```

Authentication is optional.

The authenticated request uses `read:statuses`.

The response is an array of reaction objects.

Each reaction includes:

- `name`.
- `count`.
- `me`.
- `accounts`.
- `url` for a custom emoji when available.

The client parses each reaction account as an account entity.

The client stores both full account objects and account IDs.

The server filters blocked users and muted users unless the request uses `with_muted=true`.

The client does not send `with_muted` on this call.

#### List one reaction

```http
GET /api/v1/pleroma/statuses/:id/reactions/:emoji
```

Authentication is optional.

The endpoint returns the reaction object for the selected emoji.

The current Pleroma-FE API module lists all reactions.

#### Add a reaction

```http
PUT /api/v1/pleroma/statuses/:id/reactions/:emoji
```

Required scope: `write:statuses`.

The server fully qualifies the emoji before it creates the reaction.

The response is the updated status entity.

The client sends no request body.

#### Remove a reaction

```http
DELETE /api/v1/pleroma/statuses/:id/reactions/:emoji
```

Required scope: `write:statuses`.

The response is the updated status entity.

The client sends no request body.

### Bookmark a status

```http
POST /api/v1/statuses/:id/bookmark
```

Required scope: `write:bookmarks`.

The client optionally sends this JSON field:

```json
{"folder_id":"<folder-id>"}
```

The server creates or updates the bookmark folder association.

Omitting `folder_id` can remove the existing folder association.

The response is the updated status entity.

### List bookmarks

```http
GET /api/v1/bookmarks
```

Required scope: `read:bookmarks`.

The client sends standard cursor parameters.

It sends `folder_id` when the user selects a bookmark folder.

The server returns an array of status entities.

### Unbookmark a status

```http
POST /api/v1/statuses/:id/unbookmark
```

Required scope: `write:bookmarks`.

The response is the updated status entity.

### Mute a status conversation

```http
POST /api/v1/statuses/:id/mute
```

Required scope: `write:mutes`.

Pleroma accepts `expires_in` in the request body or query string.

The server uses zero as the default for no expiration.

The current client sends no expiration value for this action.

The response is the updated status entity.

### Other status actions

Pleroma-FE uses these standard-compatible routes:

| Method | Path | Scope | Client response handling |
| --- | --- | --- | --- |
| `POST` | `/api/v1/statuses/:id/favourite` | `write:favourites` | Parses status |
| `POST` | `/api/v1/statuses/:id/unfavourite` | `write:favourites` | Parses status |
| `POST` | `/api/v1/statuses/:id/reblog` | `write:statuses` | Parses status |
| `POST` | `/api/v1/statuses/:id/unreblog` | `write:statuses` | Parses status |
| `POST` | `/api/v1/statuses/:id/pin` | `write:accounts` | Parses status |
| `POST` | `/api/v1/statuses/:id/unpin` | `write:accounts` | Parses status |
| `POST` | `/api/v1/statuses/:id/unmute` | `write:mutes` | Parses status |
| `DELETE` | `/api/v1/statuses/:id` | `write:statuses` | Uses empty response |
| `POST` | `/api/v1/statuses/:id/translate` | `read:statuses` | Uses translation response |

The server returns a status entity for the action routes.

### Find users who interacted with a status

```http
GET /api/v1/statuses/:id/favourited_by
GET /api/v1/statuses/:id/reblogged_by
```

Authentication is optional for public statuses.

Authenticated access uses `read:accounts`.

The response is an array of account entities.

The client parses every account.

## Timeline APIs

The client sends cursor parameters as `min_id`, `since_id`, `max_id`, and `limit`.

The default client limit is 20.

The client parses the response `Link` header.

It reads `max_id` from the next link and `min_id` from the previous link.

It keeps Flake cursor values as strings.

### Supported client timelines

| Client timeline | Method and path | Scope | Special client parameters |
| --- | --- | --- | --- |
| Home | `GET /api/v1/timelines/home` | `read:statuses` | `reply_visibility`, `with_muted` |
| Public local | `GET /api/v1/timelines/public` | Optional `read:statuses` | `local=true` |
| Public and external | `GET /api/v1/timelines/public` | Optional `read:statuses` | No local filter |
| Direct statuses | `GET /api/v1/timelines/direct` | `read:statuses` | `reply_visibility`, `with_muted` |
| List | `GET /api/v1/timelines/list/:list_id` | `read:lists` | `reply_visibility`, `with_muted` |
| Account statuses | `GET /api/v1/accounts/:id/statuses` | Optional `read:statuses` | `pinned`, `only_media`, `with_muted` |
| Pinned account statuses | `GET /api/v1/accounts/:id/statuses` | Optional `read:statuses` | `pinned=true` |
| Account media | `GET /api/v1/accounts/:id/statuses` | Optional `read:statuses` | `only_media=true` |
| Tag | `GET /api/v1/timelines/tag/:tag` | Optional `read:statuses` | `reply_visibility`, `with_muted` |
| Current user favourites | `GET /api/v1/favourites` | `read:favourites` | Cursor parameters |
| Any user favourites | `GET /api/v1/pleroma/accounts/:id/favourites` | Optional `read:favourites` | Cursor parameters |
| Bookmarks | `GET /api/v1/bookmarks` | `read:bookmarks` | `folder_id` |
| Quotes | `GET /api/v1/pleroma/statuses/:id/quotes` | Optional `read:statuses` | Cursor parameters |
| Notifications | `GET /api/v1/notifications` | `read:notifications` | `include_types`, cursors |
| Akkoma bubble | `GET /api/v1/timelines/bubble` | Server-specific | Cursor parameters |

Pleroma adds these useful timeline parameters:

- `with_muted=true`: include statuses from muted users.
- `reply_visibility`: use `all`, `following`, or `self`.
- `only_media=true`: include statuses with media.
- `local=true`: include local statuses.
- `remote=true`: include remote statuses.
- `exclude_visibilities[]`: exclude selected visibility values.

Pleroma does not include blocked users when `with_muted=true` is used.

The client uses `replyVisibility` and `withMuted` in camelCase internally.

`paramsString` converts those names for the server.

The client parses timeline status entities.

It parses notification entities for the notifications timeline.

### Timeline status visibility

Pleroma supports the standard values `public`, `unlisted`, `private`, and `direct`.

It also supports `local` and `list:LIST_ID`.

## Account and Relationship APIs

### Verify credentials

```http
GET /api/v1/accounts/verify_credentials
```

Required scope: `read:accounts`.

The server returns the authenticated account.

The Pleroma response includes owner-only data:

- `pleroma.settings_store`.
- `pleroma.chat_token`.
- `pleroma.notification_settings`.
- `pleroma.unread_conversation_count`.
- `pleroma.unread_notifications_count`.
- `pleroma.follow_requests_count`.
- `pleroma.email`.
- `pleroma.allow_following_move`.

The client parses the account and updates profile settings from the original response.

### Update credentials

```http
PATCH /api/v1/accounts/update_credentials
```

Required scope: `write:accounts`.

Pleroma accepts these additional JSON or form fields:

- `no_rich_text`.
- `hide_followers`.
- `hide_follows`.
- `hide_followers_count`.
- `hide_follows_count`.
- `hide_favorites`.
- `show_role`.
- `default_scope`.
- `pleroma_settings_store`.
- `skip_thread_containment`.
- `allow_following_move`.
- `also_known_as`.
- `pleroma_background_image`.
- `discoverable`.
- `actor_type`.
- `accepts_chat_messages`.
- `language`.
- `avatar_description`.
- `header_description`.

The client has two update paths.

`updateProfile` sends `multipart/form-data`.

`updateProfileJSON` sends a JSON object.

The profile settings store uses `updateProfileJSON`.

The server returns the updated account with owner-only Pleroma fields.

The client parses that account.

The server treats an empty image string as a reset request.

### Client profile setting map

The client reads these server fields and writes the listed request fields:

| Client setting | Read path | Write field | Endpoint |
| --- | --- | --- | --- |
| Default scope | `source.privacy` | `default_scope` | `PATCH /api/v1/accounts/update_credentials` |
| Strip rich content | `source.pleroma.no_rich_text` | `no_rich_text` | `PATCH /api/v1/accounts/update_credentials` |
| Accept chat messages | `pleroma.accepts_chat_messages` | `accepts_chat_messages` | `PATCH /api/v1/accounts/update_credentials` |
| Allow following moves | `pleroma.allow_following_move` | `allow_following_move` | `PATCH /api/v1/accounts/update_credentials` |
| Discoverable | `source.pleroma.discoverable` | `discoverable` | `PATCH /api/v1/accounts/update_credentials` |
| Hide favourites | `pleroma.hide_favorites` | `hide_favorites` | `PATCH /api/v1/accounts/update_credentials` |
| Hide followers | `pleroma.hide_followers` | `hide_followers` | `PATCH /api/v1/accounts/update_credentials` |
| Hide follows | `pleroma.hide_follows` | `hide_follows` | `PATCH /api/v1/accounts/update_credentials` |
| Hide follower count | `pleroma.hide_followers_count` | `hide_followers_count` | `PATCH /api/v1/accounts/update_credentials` |
| Hide following count | `pleroma.hide_follows_count` | `hide_follows_count` | `PATCH /api/v1/accounts/update_credentials` |

### Notification settings

```http
PUT /api/pleroma/notification_settings
```

Required scope: `write:accounts`.

The client sends these values as query parameters:

- `block_from_strangers`.
- `hide_notification_contents`.

The client calls this endpoint through `updateNotificationSettings`.

It sends no request body.

The server returns:

```json
{"status":"success"}
```

The client treats that response as a successful settings update.

### Account lookup

```http
GET /api/v1/accounts/lookup?acct=<acct>
```

Authentication is optional.

The response is an account entity.

The client uses this endpoint to resolve a nickname.

If the endpoint returns `404`, the client treats the supplied nickname as an ID.

It then requests `GET /api/v1/accounts/:id`.

Other lookup errors propagate to the caller.

### Account data

```http
GET /api/v1/accounts/:id
```

Authentication is optional for visible accounts.

Authenticated requests normally use `read:accounts`.

Pleroma accepts a nickname in place of `:id` for this endpoint.

The client parses the account entity.

### Followers and following

```http
GET /api/v1/accounts/:id/followers
GET /api/v1/accounts/:id/following
```

Authentication is optional for visible lists.

Authenticated requests normally use `read:accounts`.

The client sends:

- `max_id`.
- `since_id`.
- `limit`.
- `with_relationships=true`.

The response is an array of account entities.

The client parses every account.

### Relationships

```http
GET /api/v1/accounts/relationships?id[]=<id>
```

Required scope: `read:follows`.

The client can send `with_suspended`.

The server returns relationship objects.

Important fields include:

- `id`.
- `following`.
- `followed_by`.
- `blocking`.
- `blocked_by`.
- `muting`.
- `muting_notifications`.
- `requested`.
- `subscribing`.
- `notifying`.
- `showing_reblogs`.
- `domain_blocking`.
- `note`.
- `mute_expires_at`.
- `block_expires_at`.

### Follow and unfollow

```http
POST /api/v1/accounts/:id/follow
POST /api/v1/accounts/:id/unfollow
```

Required scopes: `follow` and `write:follows`.

The client can send these follow fields:

- `reblogs`.
- `notify`.

Pleroma maps `notify` to subscription behavior.

Pleroma documents the separate subscribe endpoints as deprecated.

The server returns a relationship object.

### Mute and unmute an account

```http
POST /api/v1/accounts/:id/mute
POST /api/v1/accounts/:id/unmute
```

Required scopes: `follow` and `write:mutes`.

The client sends `expires_in` for a timed mute.

The server maps that field to its mute duration.

The server uses zero for no expiration.

The response is a relationship object.

### Block and unblock an account

```http
POST /api/v1/accounts/:id/block
POST /api/v1/accounts/:id/unblock
```

Required scopes: `follow` and `write:blocks`.

The client sends `duration` for a timed block.

The response is a relationship object.

### List mutes and blocks

```http
GET /api/v1/mutes
GET /api/v1/blocks
```

Required scopes:

- Mutes: `follow` and `read:mutes`.
- Blocks: `read:blocks`.

The client sends `max_id` and `with_relationships=true`.

The response is an array of account entities.

The client parses every account.

### Remove a follower

```http
POST /api/v1/accounts/:id/remove_from_followers
```

Required scopes: `follow` and `write:follows`.

The server returns a relationship object.

### Private account note

```http
POST /api/v1/accounts/:id/note
```

Required scopes: `follow` and `write:accounts`.

The client sends this JSON field:

- `comment`.

The server returns a relationship object containing the note.

### Follow requests

```http
GET /api/v1/follow_requests
POST /api/v1/follow_requests/:id/authorize
POST /api/v1/follow_requests/:id/reject
```

The list request requires `follow` and `read:follows`.

The action requests require `follow` and `write:follows`.

The list response is an array of account entities.

The client parses the accounts.

### Search

```http
GET /api/v1/accounts/search?q=<query>&resolve=true
GET /api/v2/search?q=<query>&resolve=<bool>&type=<type>
```

Account search does not require authentication on Pleroma.

Global search can use `read:search` when authentication is used.

The client requests `with_relationships=true` for global search.

The client parses returned accounts and statuses.

Pleroma search can return unlisted statuses.

### Registration

```http
POST /api/v1/accounts
```

Authentication is not required.

The client sends `multipart` form data with these fields:

- `nickname`.
- `locale=en_US`.
- `agreement=true`.
- All remaining registration parameters supplied by the caller.

Pleroma supports these additional registration fields:

- `fullname`.
- `bio`.
- `captcha_solution`.
- `captcha_token`.
- `captcha_answer_data`.
- `token` for an invite.
- `language`.
- `birthday`.

The server can return an access token after successful registration.

It can instead return a pending-registration object.

Pending registration identifiers include:

- `missing_confirmed_email`.
- `awaiting_approval`.
- `manual_login_required`.

### CAPTCHA

```http
GET /api/pleroma/captcha
```

Authentication is not required.

The response is provider-specific JSON.

The guaranteed field is `type`.

The response can also include `token`, `url`, and `seconds_valid`.

### Import follows, blocks, and mutes

The client uploads a file in a multipart field named `list`.

| Method | Path | Scope | Success handling |
| --- | --- | --- | --- |
| `POST` | `/api/pleroma/follow_import` | `follow`, `write:follows` | Returns `response.ok` |
| `POST` | `/api/pleroma/blocks_import` | `follow`, `write:blocks` | Returns `response.ok` |
| `POST` | `/api/pleroma/mutes_import` | `follow`, `write:mutes` | Returns `response.ok` |

The server silently skips accounts that it cannot process.

### Account lifecycle

The client uses these Pleroma endpoints:

| Method | Path | Scope | Request |
| --- | --- | --- | --- |
| `POST` | `/api/pleroma/change_email` | `write:accounts` | Form fields `email`, `password` |
| `POST` | `/api/pleroma/change_password` | `write:accounts` | Form fields `password`, `new_password`, `new_password_confirmation` |
| `POST` | `/api/pleroma/move_account` | `write:accounts` | Form fields `password`, `target_account` |
| `POST` | `/api/pleroma/delete_account` | `write:accounts` | Form field `password` |

The change email, change password, move account, and delete account responses contain either `status=success` or an `error` field.

The move target is a nickname such as `user@example.org`.

Pleroma emits an account move activity after a successful move.

### Account aliases

```http
GET /api/pleroma/aliases
PUT /api/pleroma/aliases
DELETE /api/pleroma/aliases
```

The list request requires `read:accounts`.

The add and delete requests require `write:accounts`.

The client sends this JSON field for add and delete:

- `alias`.

The list response is:

```json
{"aliases":["user@example.org"]}
```

Mutation responses contain `status=success` or an `error` field.

### OAuth session list

```http
GET /api/oauth_tokens.json
DELETE /api/oauth_tokens/:id
```

Authentication is required.

The list response includes session fields such as:

- `app_name`.
- `id`.
- `valid_until`.

The delete request revokes the selected session.

## Pleroma Chats

Pleroma chats are separate from direct-message statuses.

They use ActivityPub `ChatMessage` objects.

They do not appear in normal timelines.

They always remain private between the two actors.

Pleroma supports one chat between a user and a recipient.

### List chats

```http
GET /api/v1/pleroma/chats
```

Required scope: `read:chats`.

This is the older unpaginated route.

Pleroma also exposes the paginated route:

```http
GET /api/v2/pleroma/chats
```

The current client uses the older route.

The client polls this endpoint every five seconds.

The unpaginated response is an array of chat entities.

Each chat contains:

- `id`.
- `account`.
- `unread`.
- `last_message`.
- `updated_at`.
- `pinned`.

The client sorts chats by `updated_at`.

The client uses `unread` as the unread message count.

### Create or get a chat

```http
POST /api/v1/pleroma/chats/by-account-id/:account_id
```

Required scope: `write:chats`.

The path parameter is the recipient account ID.

The endpoint returns the existing chat when one already exists.

The response is a chat entity.

### Get a chat

```http
GET /api/v1/pleroma/chats/:id
```

Required scope: `read:chats`.

The response is a chat entity.

### List chat messages

```http
GET /api/v1/pleroma/chats/:id/messages
```

Required scope: `read:chats`.

The client sends:

- `max_id`.
- `since_id`.
- `limit`.

The default client limit is 20.

The server returns messages from newest to oldest.

The response is an array of chat messages.

### Send a chat message

```http
POST /api/v1/pleroma/chats/:id/messages
```

Required scope: `write:chats`.

The client sends a JSON body with:

- `content`.
- `media_id` when a media attachment exists.

The client sends `idempotency-key` when it has an optimistic message key.

The server copies that header to `idempotency_key` for a short period after creation.

The response contains a chat message.

The server accepts an empty `content` when `media_id` exists.

### Mark chat messages as read

```http
POST /api/v1/pleroma/chats/:id/read
```

Required scope: `write:chats`.

The client sends:

- `last_read_id`.

The server marks all messages through that ID as read.

The response is the updated chat.

### Delete a chat message

```http
DELETE /api/v1/pleroma/chats/:chat_id/messages/:message_id
```

Required scope: `write:chats`.

The response is the deleted chat message.

The server allows the author to delete the federated message.

The chat owner can remove the local message reference.

### Chat message fields

The server ChatMessage schema defines these fields:

- `id`.
- `account_id`.
- `chat_id`.
- `content`.
- `created_at`.
- `emojis`.
- `attachment`.
- `card`.
- `unread`.
- `idempotency_key` when the server includes it.

The client maps `attachment` to `attachments` as a one-item array.

It converts `created_at` to a JavaScript `Date`.

It preserves `emojis`, `content`, `id`, `chat_id`, `account_id`, and `idempotency_key`.

### Chat notifications and streaming

Pleroma can send `pleroma:chat_mention` notifications.

The client must request that type through `include_types[]` to receive it.

The server sends chat updates on the `user:pleroma_chat` stream.

The event name is `pleroma:chat_update`.

The event payload is an updated chat.

The current client parses that payload as a chat update.

The chat store updates its existing chat or inserts a new chat.

## Bookmarks and Bookmark Folders

### List bookmark folders

```http
GET /api/v1/pleroma/bookmark_folders
```

Required scope: `read:bookmarks`.

The response is an array of folders.

Each folder contains:

- `id`.
- `name`.
- `emoji`.
- `emoji_url`.

The server returns `emoji_url` for a custom emoji.

It returns `null` for a Unicode emoji or absent emoji.

The client refreshes folders every four minutes.

### Create a bookmark folder

```http
POST /api/v1/pleroma/bookmark_folders
```

Required scope: `write:bookmarks`.

The client sends:

- `name`.
- `emoji`.

The response is one bookmark folder.

### Update a bookmark folder

```http
PATCH /api/v1/pleroma/bookmark_folders/:id
```

Required scope: `write:bookmarks`.

The client sends optional `name` and `emoji` fields.

The server checks folder ownership.

The response is one bookmark folder.

### Delete a bookmark folder

```http
DELETE /api/v1/pleroma/bookmark_folders/:id
```

Required scope: `write:bookmarks`.

The server checks folder ownership.

The response is the deleted folder.

## Backups

### Create a backup

```http
POST /api/v1/pleroma/backups
```

Required scope: `read:backups`.

The client sends no body.

The server queues a backup and returns the current backup list.

Each backup includes:

- `id`.
- `content_type`.
- `url`.
- `file_size`.
- `processed`.
- `inserted_at`.

The server can return an unprocessed backup while the archive job runs.

### List backups

```http
GET /api/v1/pleroma/backups
```

Required scope: `read:backups`.

The client adds a timestamp query parameter as a cache buster.

The client does not pass an OAuth token to this public API function.

The request still sends same-origin cookies through `promisedRequest`.

The server operation requires `read:backups`.

Integrations should send the bearer token explicitly.

## Scrobbles

Pleroma marks audio scrobbling as deprecated.

### List scrobbles

```http
GET /api/v1/pleroma/accounts/:id/scrobbles
```

Authentication is optional.

Authenticated access uses `read:scrobbles`.

The client requests `limit=1`.

The response is an array of media metadata entities.

Each entity includes:

- `id`.
- `account`.
- `title`.
- `artist`.
- `album`.
- `length`.
- `external_link`.
- `created_at`.

The client stores the first entity as `latestScrobble`.

It does not run a response normalizer for scrobble entities.

The scrobble store stops requesting scrobbles after an error.

### Create a scrobble

```http
POST /api/v1/pleroma/scrobble
```

Required scope: `write:scrobbles`.

The inspected Pleroma-FE source defines the server endpoint but does not call it.

Pleroma accepts:

- `title`.
- `album`.
- `artist`.
- `length`.
- `external_link`.
- `visibility`.

The server returns the created media metadata entity.

## MFA and OAuth Challenges

### MFA account settings

```http
GET /api/pleroma/accounts/mfa
```

Required scope: `read:security`.

The response is:

```json
{"settings":{"enabled":false,"totp":false}}
```

The exact value type for `enabled` follows the server response.

### Set up TOTP

```http
GET /api/pleroma/accounts/mfa/setup/totp
```

Required scope: `write:security`.

The response contains:

- `key`.
- `provisioning_uri`.

### Confirm TOTP

```http
POST /api/pleroma/accounts/mfa/confirm/totp
```

Required scope: `write:security`.

The client sends form fields:

- `password`.
- `code`.

The success response is an empty object.

Errors return HTTP 422 with an `error` field.

### Disable TOTP

```http
DELETE /api/pleroma/accounts/mfa/totp
```

Required scope: `write:security`.

The client sends form field `password`.

The success response is an empty object.

### Generate backup codes

```http
GET /api/pleroma/accounts/mfa/backup_codes
```

Required scope: `write:security`.

The response is:

```json
{"codes":["<code>"]}
```

### OAuth application registration

```http
POST /api/v1/apps
```

The client sends form fields:

- `client_name=PleromaFE`.
- `website=https://pleroma.social`.
- `redirect_uris=<origin>/oauth-callback`.
- `scopes=read write follow push admin`.

The response uses `client_id` and `client_secret`.

The client adds camelCase aliases named `clientId` and `clientSecret`.

### OAuth authorization

The client builds this URL:

```text
<instance>/oauth/authorize?response_type=code&client_id=<id>&redirect_uri=<uri>&scope=read%20write%20follow%20push%20admin
```

The redirect returns an authorization code.

### OAuth token exchange

```http
POST /oauth/token
```

The client supports these grant forms:

| Grant | Form fields |
| --- | --- |
| Password | `client_id`, `client_secret`, `grant_type=password`, `username`, `password` |
| Authorization code | `client_id`, `client_secret`, `grant_type=authorization_code`, `code`, `redirect_uri` |
| Client credentials | `client_id`, `client_secret`, `grant_type=client_credentials`, `redirect_uri` |

Pleroma also supports refresh tokens through `grant_type=refresh_token`.

The current client does not define a refresh-token helper in the inspected OAuth module.

Pleroma token responses can include `id` and `me` in addition to standard token fields.

### OAuth MFA challenge

```http
POST /oauth/mfa/challenge
```

The client sends form fields:

- `client_id`.
- `client_secret`.
- `mfa_token`.
- `code`.
- `challenge_type=totp` or `challenge_type=recovery`.

This endpoint completes an OAuth login challenge.

It is separate from the authenticated account MFA endpoints.

The client has duplicate challenge helpers in `oauth.js` and `mfa.js`.

The active MFA form imports the helpers from `mfa.js`.

The TOTP helper uses a relative URL.

The recovery helper in `mfa.js` prefixes the supplied instance URL.

### Revoke an OAuth token

```http
POST /oauth/revoke
```

The client sends:

- `client_id`.
- `client_secret`.
- `token`.

## Administration APIs

Most admin calls use the authenticated bearer token through the normal request helper.

The server additionally checks staff status and a route-specific privilege.

The client requests the broad OAuth `admin` scope when it registers the application.

Emoji pack OpenAPI operations declare the granular `admin:write` scope.

### Admin user list

```http
GET /api/v1/pleroma/admin/users
```

The client sends these query fields:

- `page`.
- `page_size`.
- `filters` as a comma-separated string.
- `query`.
- `name`.
- `email`.

Supported filter names include:

- `local`.
- `external`.
- `active`.
- `need_approval`.
- `unconfirmed`.
- `deactivated`.
- `is_admin`.
- `is_moderator`.

The response contains:

- `page_size`.
- `count`.
- `users`.

Each admin user object can contain `id`, `nickname`, `roles`, `local`, `tags`, `avatar`, `display_name`, `deactivated`, `confirmation_pending`, and `approval_pending`.

### Admin user actions

The client sends `nicknames` arrays for batch operations.

| Method | Path | Body fields | Server privilege |
| --- | --- | --- | --- |
| `DELETE` | `/api/v1/pleroma/admin/users` | `nicknames` | `users_delete` |
| `PATCH` | `/api/v1/pleroma/admin/users/activate` | `nicknames` | `users_manage_activation_state` |
| `PATCH` | `/api/v1/pleroma/admin/users/deactivate` | `nicknames` | `users_manage_activation_state` |
| `PATCH` | `/api/v1/pleroma/admin/users/approve` | `nicknames` | `users_manage_invites` |
| `PATCH` | `/api/v1/pleroma/admin/users/confirm_email` | `nicknames` | `users_manage_credentials` |
| `PATCH` | `/api/v1/pleroma/admin/users/resend_confirmation_email` | `nicknames` | `users_manage_credentials` |
| `PATCH` | `/api/v1/pleroma/admin/users/force_password_reset` | `nicknames` | `users_manage_credentials` |
| `PATCH` | `/api/v1/pleroma/admin/users/suggest` | `nicknames` | Route uses legacy-compatible path |
| `PATCH` | `/api/v1/pleroma/admin/users/unsuggest` | `nicknames` | Route uses legacy-compatible path |

The client expects `response.users` for activation, approval, confirmation, and suggestion updates.

### Admin tags

```http
PUT /api/pleroma/admin/users/tag
DELETE /api/pleroma/admin/users/tag
```

The server reroutes the legacy prefix to the versioned route.

The client sends:

- `nicknames`.
- `tags`.

The server requires the `users_manage_tags` privilege.

### Admin permission groups

```http
POST /api/pleroma/admin/users/permission_group/:permission_group
DELETE /api/pleroma/admin/users/permission_group/:permission_group
```

The client sends a `nicknames` array.

The current client passes the permission group in the `right` argument.

Supported groups include `admin` and `moderator`.

The server returns a user result or an error object.

The server prevents an administrator from revoking their own administrator status.

### Admin user details and statuses

```http
GET /api/v1/pleroma/admin/users/:nickname
GET /api/v1/pleroma/admin/users/:nickname/statuses
```

The status request accepts:

- `page`.
- `page_size`.
- `godmode`.
- `with_reblogs`.

The response contains `total` and `activities`.

`godmode=true` permits privileged viewing of private statuses.

The server requires `users_read` for user details.

The server requires `messages_read` for user statuses.

### Admin MFA

```http
PUT /api/v1/pleroma/admin/users/disable_mfa
```

The client sends:

- `nickname`.

The server requires `users_manage_credentials`.

### Admin status scope changes

```http
PUT /api/v1/pleroma/admin/statuses/:id
```

The client sends optional JSON fields:

- `sensitive`.
- `visibility`.

The server requires the message deletion privilege for this administrative route.

### Admin reports

```http
PATCH /api/v1/pleroma/admin/reports
```

The client sends:

```json
{"reports":[{"id":"<id>","state":"<state>"}]}
```

The server requires `reports_manage_reports`.

### Admin announcements

The client uses this route family:

```http
GET /api/v1/pleroma/admin/announcements
POST /api/v1/pleroma/admin/announcements
PATCH /api/v1/pleroma/admin/announcements/:id
DELETE /api/v1/pleroma/admin/announcements/:id
```

The client sends these JSON fields:

- `content`.
- `starts_at` as an ISO 8601 date or `null`.
- `ends_at` as an ISO 8601 date or `null`.
- `all_day`.

The server requires the `announcements_manage_announcements` privilege.

### Admin configuration

```http
GET /api/v1/pleroma/admin/config
GET /api/v1/pleroma/admin/config/descriptions
POST /api/v1/pleroma/admin/config
```

The client sends the configuration payload unchanged on update.

The responses contain the database configuration or its descriptions.

The server restricts these routes to administrators.

### Admin frontends

```http
GET /api/v1/pleroma/admin/frontends
POST /api/v1/pleroma/admin/frontends/install
```

The client sends the install payload unchanged.

The responses contain available frontend data or installation results.

### Admin emoji pack list

```http
GET /api/v1/pleroma/emoji/packs
```

Authentication is not required.

The client sends `page` and `page_size`.

The response contains:

- `packs`: a map from pack name to pack data.
- `count`: the number of packs.

The client uses this endpoint when it loads pack management data.

### Admin emoji pack details

```http
GET /api/v1/pleroma/emoji/pack?name=<name>&page=<page>&page_size=<page-size>
```

Authentication is not required.

The response contains:

- `files`: a shortcode-to-filename map.
- `files_count`.
- `pack` metadata.

Pack metadata can include:

- `license`.
- `homepage`.
- `description`.
- `can-download`.
- `share-files`.
- `download-sha256`.

### Create, update, and delete an emoji pack

The client uses:

```http
POST /api/v1/pleroma/emoji/pack?name=<name>
PATCH /api/v1/pleroma/emoji/pack?name=<name>
DELETE /api/v1/pleroma/emoji/pack?name=<name>
```

Required scope: `admin:write`.

The server also requires the emoji management privilege.

The update request contains a `metadata` object.

The metadata can contain `license`, `homepage`, `description`, `fallback-src`, `fallback-src-sha256`, and `share-files`.

The successful create and delete response is the string `ok`.

The update response contains pack metadata.

### Import emoji packs from the filesystem

```http
GET /api/v1/pleroma/emoji/packs/import
```

Required scope: `admin:write`.

The server also requires the emoji management privilege.

The response is an array of imported pack names.

### List remote emoji packs

```http
GET /api/v1/pleroma/emoji/packs/remote?url=<url>&page=<page>&page_size=<page-size>
```

Required scope: `admin:write`.

The client adds `https://` when the instance value does not start with `http`.

The response contains remote pack data.

### Download a remote emoji pack

```http
POST /api/v1/pleroma/emoji/packs/download
```

Required scope: `admin:write`.

The client sends JSON fields:

- `url`.
- `name`.
- `as`.

The server returns the string `ok` on success.

### Download a remote ZIP

```http
POST /api/v1/pleroma/emoji/packs/download_zip
```

Required scope: `admin:write`.

The client sends multipart fields:

- `name`.
- `url` when downloading by URL.
- `file` when uploading a ZIP.

The server returns the string `ok` on success.

### Add an emoji file

```http
POST /api/v1/pleroma/emoji/packs/files?name=<pack-name>
```

Required scope: `admin:write`.

The client sends multipart fields:

- `file`.
- `shortcode` when present.
- `filename` when present.

The server derives missing shortcode and filename values from the uploaded file.

The response is a shortcode-to-filename map.

### Update an emoji file

```http
PATCH /api/v1/pleroma/emoji/packs/files?name=<pack-name>
```

Required scope: `admin:write`.

The client sends JSON fields:

- `shortcode`.
- `new_shortcode`.
- `new_filename`.
- `force`.

The response is the updated file map.

### Delete an emoji file

```http
DELETE /api/v1/pleroma/emoji/packs/files?name=<pack-name>&shortcode=<shortcode>
```

Required scope: `admin:write`.

The response is the updated file map.

### Admin authentication omissions in the current client

These `admin.js` functions do not pass a bearer token to `promisedRequest`:

- `deleteEmojiPack`.
- `downloadRemoteEmojiPackZIP`.
- `addNewEmojiFile`.
- `deleteEmojiFile`.

The helper still sends same-origin cookies.

These calls therefore work only when a supported cookie session authenticates the request.

An OAuth-only integration must add the bearer token.

## Notification APIs

### Read notifications

```http
POST /api/v1/pleroma/notifications/read
```

Required scope: `write:notifications`.

The client sends one of these mutually exclusive form fields:

- `id`: mark one notification as read.
- `max_id`: mark all notifications through that ID as read.

The server returns the string `ok` in the current controller implementation.

The client does not parse the response.

### Get notifications

```http
GET /api/v1/notifications
```

Required scope: `read:notifications`.

The client sends `include_types[]` when notification filters are active.

Pleroma supports these additional types:

- `pleroma:emoji_reaction`.
- `pleroma:chat_mention`.
- `pleroma:report`.

The client parses Pleroma notification fields.

It fetches full reaction details after it receives an emoji reaction notification.

### Dismiss a notification

```http
POST /api/v1/notifications/:id/dismiss
```

Required scope: `write:notifications`.

The client also sends `id` in a JSON body.

The server dismisses the notification.

## Streaming APIs

### WebSocket URL

The client connects to:

```text
/api/v1/streaming?access_token=<token>
```

The client creates a unified connection without an initial stream.

It sends the access token in the query string when a token exists.

Pleroma also supports token authentication through the `sec-websocket-protocol` header.

The current client does not use that header.

### Authentication after connection

After the socket opens, the client sends:

```json
{"type":"pleroma:authenticate","token":"<token>"}
```

The server responds with a `pleroma:respond` event.

The response payload is a JSON string.

The payload contains the client event type and a result.

The client accepts `result=success`.

It also accepts `error=already_authenticated`.

It closes the socket for other authentication errors.

### Subscribe and unsubscribe

The client sends these message shapes:

```json
{"type":"subscribe","stream":"<stream>"}
{"type":"unsubscribe","stream":"<stream>"}
```

It adds `tag` for hashtag streams.

It adds `list` for list streams.

Pleroma supports these relevant streams:

- `user`.
- `user:notifications`.
- `user:pleroma_chat`.
- `public`.
- `public:local`.
- `public:media`.
- `public:local:media`.
- `public:remote`.
- `public:remote:media`.
- `direct`.
- `hashtag` with `tag`.
- `list` with `list`.

Remote streams also accept an `instance` domain.

### Server events consumed by the client

The client handles these standard events:

- `update`: parse a status.
- `status.update`: parse a status.
- `notification`: parse a notification.
- `delete`: pass the deleted ID.

The client handles these Pleroma events:

- `pleroma:chat_update`: parse a chat.
- `pleroma:respond`: process authentication responses.

The chat store subscribes to `pleroma:chat_update`.

The notification store subscribes to `user:notifications`.

The status store subscribes to status updates and deletes.

Pleroma also emits `pleroma:follow_relationships_update`.

The current event parser does not handle that event.

Unknown events produce a warning and no client event.

### Streaming fallback behavior

The notification store polls while the notification socket is disconnected.

It stops polling after the notification stream connects.

It resumes polling after the stream disconnects.

The chat store polls every five seconds even though it subscribes to chat updates.

The streaming store retries unexpected socket closures with increasing delays.

It does not retry normal closure codes 1000 and 1001.

## Remote Interaction

The client builds a browser link for remote interaction:

```text
/main/ostatus?status_id=<status-id>
/main/ostatus?nickname=<nickname>
```

This is a browser route, not a JSON API call.

Pleroma also exposes this unauthenticated API endpoint:

```http
POST /api/v1/pleroma/remote_interaction
```

The request can contain:

- `ap_id` for a remote profile or status ActivityPub ID.
- `profile` for a remote WebFinger profile.

The response contains a redirect `url` or an `error`.

The inspected client uses the browser route instead of this API endpoint.

## Compatibility Notes

### Client-side Pleroma gates

The client gates these features with NodeInfo or instance data:

- Pleroma chat messages.
- Custom emoji reactions.
- Bookmark folders.
- Quoting.
- Expiring blocks.
- Polls.
- Status editing.
- Media proxy behavior.
- Scrobbles.
- Pleroma post formats.

Clients should use feature data before calling optional endpoints.

### Deprecated server features

Pleroma marks scrobbling as deprecated.

Pleroma marks the unpaginated chat list as deprecated.

Pleroma documents `/api/v1/statuses/:id/quotes` as preferred over the Pleroma-prefixed quote route.

Pleroma documents `notify` on follow as preferred over separate subscribe endpoints.

Pleroma documents `/api/v1/pleroma/*` as preferred over `/api/pleroma/*`.

The current client still uses several deprecated or legacy paths.

### Response differences from vanilla Mastodon

Pleroma exposes alternate plain-text status content under `pleroma.content`.

It exposes alternate plain-text spoiler text under `pleroma.spoiler_text`.

It exposes `pleroma.emoji_reactions` counts in each status.

It exposes reacting accounts through a separate reactions endpoint.

It exposes bookmark folder IDs through `pleroma.bookmark_folder`.

It exposes expiration timestamps through `pleroma.expires_at`.

It exposes direct conversation IDs through `pleroma.direct_conversation_id`.

It exposes account settings through owner-only Pleroma fields.

It uses 128-bit string IDs.

It accepts additional timeline filters.

It can return unlisted statuses in global search.

### Current client caveats

The client does not send status expiration even though the server supports `expires_in`.

The client does not send `in_reply_to_conversation_id`.

The client uses `quote_id` instead of the preferred `quoted_status_id`.

The client uses the unpaginated `/api/v1/pleroma/chats` route.

The client does not pass bearer credentials in `fetchScrobbles`.

The client does not pass bearer credentials in several emoji administration calls.

The client treats a false `pleroma.quote_visible` value as true during normalization.

These behaviors describe the inspected Pleroma-FE source.

## File References

Client transport and API calls:

- `pleroma-fe/src/api/helpers.js:5-139`
- `pleroma-fe/src/api/public.js:10-298`
- `pleroma-fe/src/api/user.js:12-930`
- `pleroma-fe/src/api/timelines.js:9-204`
- `pleroma-fe/src/api/chats.js:8-94`
- `pleroma-fe/src/api/admin.js:3-491`
- `pleroma-fe/src/api/oauth.js:3-146`
- `pleroma-fe/src/api/mfa.js:3-45`
- `pleroma-fe/src/api/websocket.js:9-181`

Client capability and response consumers:

- `pleroma-fe/src/boot/after_store.js:89-444`
- `pleroma-fe/src/services/entity_normalizer/entity_normalizer.service.js:18-430`
- `pleroma-fe/src/services/status_poster/status_poster.service.js:14-122`
- `pleroma-fe/src/stores/instance_capabilities.js:6-54`
- `pleroma-fe/src/stores/profile_config.js:45-152`
- `pleroma-fe/src/stores/statuses.js:174-303`
- `pleroma-fe/src/stores/chats.js:18-95`
- `pleroma-fe/src/stores/notifications.js:164-265`
- `pleroma-fe/src/stores/bookmark_folders.js:14-85`
- `pleroma-fe/src/stores/scrobbles.js:12-41`

Server routes and authorization:

- `pleroma/lib/pleroma/web/router.ex:87-109`
- `pleroma/lib/pleroma/web/router.ex:243-261`
- `pleroma/lib/pleroma/web/router.ex:333-560`
- `pleroma/lib/pleroma/web/router.ex:573-706`
- `pleroma/lib/pleroma/web/router.ex:727-742`
- `pleroma/lib/pleroma/web/router.ex:744-953`

Server controllers and views:

- `pleroma/lib/pleroma/web/mastodon_api/controllers/status_controller.ex`
- `pleroma/lib/pleroma/web/mastodon_api/controllers/account_controller.ex`
- `pleroma/lib/pleroma/web/mastodon_api/controllers/notification_controller.ex`
- `pleroma/lib/pleroma/web/mastodon_api/controllers/instance_controller.ex`
- `pleroma/lib/pleroma/web/pleroma_api/controllers/chat_controller.ex`
- `pleroma/lib/pleroma/web/pleroma_api/controllers/emoji_reaction_controller.ex`
- `pleroma/lib/pleroma/web/pleroma_api/controllers/bookmark_folder_controller.ex`
- `pleroma/lib/pleroma/web/pleroma_api/controllers/scrobble_controller.ex`
- `pleroma/lib/pleroma/web/pleroma_api/controllers/backup_controller.ex`
- `pleroma/lib/pleroma/web/pleroma_api/controllers/util_controller.ex`
- `pleroma/lib/pleroma/web/pleroma_api/controllers/two_factor_authentication_controller.ex`
- `pleroma/lib/pleroma/web/pleroma_api/controllers/emoji_pack_controller.ex`
- `pleroma/lib/pleroma/web/pleroma_api/controllers/emoji_file_controller.ex`
- `pleroma/lib/pleroma/web/mastodon_api/views/account_view.ex`
- `pleroma/lib/pleroma/web/mastodon_api/views/status_view.ex`
- `pleroma/lib/pleroma/web/pleroma_api/views/chat_view.ex`
- `pleroma/lib/pleroma/web/pleroma_api/views/emoji_reaction_view.ex`
- `pleroma/lib/pleroma/web/pleroma_api/views/bookmark_folder_view.ex`
- `pleroma/lib/pleroma/web/pleroma_api/views/backup_view.ex`
- `pleroma/lib/pleroma/web/pleroma_api/views/scrobble_view.ex`

Server schemas and documentation:

- `pleroma/lib/pleroma/web/api_spec/schemas/account.ex`
- `pleroma/lib/pleroma/web/api_spec/schemas/status.ex`
- `pleroma/lib/pleroma/web/api_spec/schemas/chat.ex`
- `pleroma/lib/pleroma/web/api_spec/schemas/chat_message.ex`
- `pleroma/lib/pleroma/web/api_spec/operations/status_operation.ex`
- `pleroma/lib/pleroma/web/api_spec/operations/account_operation.ex`
- `pleroma/lib/pleroma/web/api_spec/operations/chat_operation.ex`
- `pleroma/lib/pleroma/web/api_spec/operations/pleroma_bookmark_folder_operation.ex`
- `pleroma/lib/pleroma/web/api_spec/operations/pleroma_scrobble_operation.ex`
- `pleroma/lib/pleroma/web/api_spec/operations/pleroma_backup_operation.ex`
- `pleroma/lib/pleroma/web/api_spec/operations/pleroma_notification_operation.ex`
- `pleroma/lib/pleroma/web/api_spec/operations/pleroma_emoji_pack_operation.ex`
- `pleroma/lib/pleroma/web/api_spec/operations/pleroma_status_operation.ex`
