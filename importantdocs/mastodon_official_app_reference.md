# Mastodon for Android: API Usage Reference

This document describes how the official Mastodon Android app uses the Mastodon REST API.
Use this document as a reference when you reimplement Mastodon features in a third-party app.

Source code examined: `mastodon-android` repository, module `mastodon/src/main/java/org/joinmastodon/android`.
The API client code lives in the `api` package.
The UI code lives in the `fragments` package.

## Terms

- **The app** means the official Mastodon Android app.
- **Mastodon** means the server software.
- **The web client** means the official Mastodon web frontend.
- **Endpoint** means one HTTP route of the API.
- **API version** means the value of `api_versions.mastodon` from `GET /api/v2/instance`.

## 1. Transport Layer

The app uses OkHttp for all HTTP traffic.

Details of the transport layer:

- One background worker thread executes all requests.
- Connect timeout: 60 seconds.
- Write timeout: 60 seconds.
- Read timeout: 60 seconds.
- One request can set a custom total timeout. Example: 500 ms for the default-server catalog.
- All requests use HTTPS.
- User-Agent header: `MastodonAndroid/<app version>`.
- Authorization header: `Bearer <access token>`.
- The app adds the Authorization header when a session or an explicit token exists.
- JSON request bodies use `application/json`.
- Form bodies use `application/x-www-form-urlencoded` with UTF-8 encoding.
- File uploads use `multipart/form-data`.
- The OkHttp disk cache holds 10 MB in the app cache directory.
- Only the donation-campaign request uses the HTTP cache.
- All other requests send `Cache-Control: no-cache, no-store`.
- The app never follows HTTP redirects manually; OkHttp handles them.

### Gson configuration

The app parses JSON with Gson. The parser:

- Does not escape HTML.
- Maps JSON snake_case fields to camelCase Java fields.
- Parses `Instant` fields from ISO-8601 instant or offset date-time strings.
- Parses `LocalDate` fields from ISO-8601 date strings.
- Serializes enums via `@SerializedName` values, or lowercase names by default.

### Response validation

Each model class has a `postprocess` method.
Required fields are marked with `@RequiredField` or `@AllFieldsAreRequired`.
The request layer validates responses after parsing:

- A null response for a typed request is an error.
- A null item inside a list is an error.
- Some requests set `removeUnsupportedItems`. These requests remove invalid items from lists instead of failing.

### Error handling

The app expects Mastodon error responses in this JSON shape:

```json
{ "error": "message" }
```

Validation errors use this extended shape:

```json
{
  "error": "Validation failed",
  "details": { "field": [ { "error": "...", "description": "..." } ] }
}
```

The app parses `details` into per-field errors.
When error JSON is missing, the app falls back to the HTTP status code and status text.

The app also maps local failures:

- `UnknownHostException` becomes "could not reach server".
- `SocketTimeoutException` becomes "connection timed out".
- Malformed JSON or HTTP status 500 or higher becomes "server error".
- HTTP 404 becomes "not found".

In debug builds only, the app reads the `Deprecation` response header.
It shows a toast when the server announces an endpoint deprecation.
Release builds ignore this header.

The app does not read rate-limit response headers.

## 2. Authentication

The app uses the Mastodon OAuth flow.

### App registration

- Endpoint: `POST /api/v1/apps`.
- This request has no Authorization header.
- Body fields: `client_name` ("Mastodon for Android"), `redirect_uris`, `scopes`, `website`.

The app registers a new OAuth application on every login.
The scopes are always: `read write follow push`.
The redirect URI is `<application id>-auth://callback`.

### Authorization request

The app opens `https://<domain>/oauth/authorize` in a Chrome Custom Tab.

Query parameters:

- `response_type=code`
- `client_id`
- `redirect_uri`
- `scope` ("read write follow push")

PKCE behavior:

- The server supports PKCE when API version is 3 or higher.
- The app adds `code_challenge` and `code_challenge_method=S256` only on those servers.
- The code verifier is 32 random bytes, Base64 URL-safe encoded without padding.

### Token exchange

- Endpoint: `POST /oauth/token` (no `/api` prefix).
- Grant type: `authorization_code`.
- Body: `grant_type`, `client_id`, `client_secret`, `code`, `redirect_uri`.
- The app adds `code_verifier` only when it used PKCE.

After the token exchange, the app calls `GET /api/v1/accounts/verify_credentials`.
It stores the account object together with the token.

### Client credentials grant

The signup flow uses a second grant:

- Endpoint: `POST /oauth/token`.
- Grant type: `client_credentials`.
- Body: `grant_type`, `client_id`, `client_secret`, `scope`.
- The app gets a public token. This token permits `POST /api/v1/accounts`.

### Logout

- Endpoint: `POST /oauth/revoke`.
- Body: `client_id`, `client_secret`, `token`.
- The app removes local data even when revocation fails.

### Signup

- Endpoint: `POST /api/v1/accounts`.
- Body: `username`, `email`, `password`, `locale`, `reason`, `time_zone`, `invite_code`, `date_of_birth`, `agreement=true`.

The `reason` field appears when the instance requires an approval reason.
The `date_of_birth` field appears when the instance enforces a minimum age.
The `invite_code` field appears when the user follows an invite link.

Email confirmation:

- Endpoint: `POST /api/v1/emails/confirmations`.
- The app sends an empty JSON body.
- The app enforces a resend delay on its own.

Invite links:

- The app calls `GET` on the invite path itself, for example `/invite/abc123`.
- This request uses no `/api` prefix.
- It sets the `Accept: application/json` header.
- The response contains an `invite_code` field.

### Account sessions

- One account session key is `<domain>_<account id>`.
- The app supports many logged-in accounts.
- Session data lives in SQLite (`accounts.db`).
- Each session keeps: token, own account, OAuth app, domain, push keys, push subscription, activation info, preferences, legacy filters.

Refresh policy:

- The app refreshes its own account every 24 hours at most.
- It refreshes instance info every 24 hours at most.
- It refreshes legacy filters every hour when server-side filters are unavailable.

## 3. Instance Information and API Version Detection

The app calls `GET /api/v2/instance` first.
When that returns HTTP 404, the app falls back to `GET /api/v1/instance`.
The fallback supports Mastodon 3.x and other servers.

The `Instance` model stores these configuration values:

- `title`, `description`, `version`, `languages`, `rules` (with translations).
- `configuration.statuses`: max characters, max media attachments, characters reserved per URL.
- `configuration.media_attachments`: supported MIME types, image size limit, image matrix limit, video limits.
- `configuration.polls`: max options, max characters per option, min and max expiration.
- `configuration.urls`: streaming, status, about, privacy policy, terms of service.
- `configuration.timelines_access`: access values for live, hashtag, and trending link feeds.
- `configuration.vapid`: public key for push.
- `registrations`: enabled, approval required, reason required, minimum age.
- `contact`: email and account.
- `api_versions`: map of software name to version number.
- Non-standard fork field: `maxTootChars` (maximum post characters from other server software).

API version values:

- `GET /api/v1/instance` fallback returns API version 0.
- The app reads `api_versions.mastodon` from the v2 instance response.

The API version controls feature availability. See section 4.

### Other instance endpoints

- `GET /api/v1/custom_emojis` loads emojis for the composer picker.
- `GET /api/v1/instance/extended_description` loads the "about" page content.

The app caches instance data and emojis in SQLite.
It reloads both after an app update.

## 4. Feature Gating by API Version

The app checks `api_versions.mastodon` before it uses newer endpoints or features.

| API version gate | Feature the app unlocks |
| --- | --- |
| 2 or higher | Grouped notifications endpoints (`/api/v2/notifications`, `/api/v2/notifications/unread_count`) |
| 3 or higher | OAuth PKCE with S256 |
| 4 or higher | RFC 9420 Web Push encryption format (`subscription.standard`) |
| 6 or higher | Featured accounts (endorsements) tab on profiles |
| 7 or higher | Quote post authoring, quote approval policies |
| 8 or higher | Profile endpoints (`GET/PUT /api/v1/profile`, delete avatar, delete header) |
| 10 or higher | Collections API |

Observed server values for `api_versions.mastodon`:

| Mastodon release | API version value |
| --- | --- |
| 4.2.x and older | absent |
| 4.3.0 | 2 |
| 4.3.6 | 4 |
| 4.4.x | 6 |
| 4.5.x | 7 |
| 4.6.0 | 10 |
| 4.7.1 | 11 |

These values come from the Mastodon source file `lib/mastodon/version.rb`.
Patch releases can change the value.
Treat the exact release mapping as approximate.

Other checks the app performs:

- Timeline access: the app reads `configuration.timelines_access.live_feeds.local`.
  A value of `disabled` hides the local timeline. Account roles can override this.
- Admin notifications: the app checks account role permissions.
  Permissions `administrator`, `manage_users`, and `manage_reports` unlock admin notification controls.
- The push manager uses the VAPID public key from the instance configuration.

## 5. Pagination

The app uses two pagination styles.

### ID-based pagination with Link headers

Most list endpoints use `HeaderPaginationRequest`.
The app parses the `Link` response header for `rel="next"` and `rel="prev"` URLs.
It extracts `max_id` from the next URL.

List requests send these query parameters:

- `limit` — page size.
- `max_id` — older items.
- `min_id` — newer items.
- `since_id` — for top refresh on home, list, and public timelines.

Timeline refresh behavior:

- The app stores the newest seen post ID.
- On refresh, it requests `since_id` equal to the newest ID.
- It prepends new posts, without a gap fill.

### Offset-based pagination

Trending statuses and search use numeric offsets.
Example: `GET /api/v1/trends/statuses?limit=20&offset=20`.

### Batch requests

The app has a `BatchRequest` wrapper.
It runs several requests at once and returns one combined callback.
One failure fails the whole batch.
The profile tab loads the timeline, featured content, and account info this way.

## 6. Endpoint Catalog

The app requests only the endpoints below.
`default prefix` is `/api/v1`.
Entries list the prefix when the app overrides it.

### OAuth and account registration

| Method | Path | Prefix | Notes |
| --- | --- | --- | --- |
| POST | `/apps` | | Register OAuth app. |
| POST | `/oauth/token` | none | `authorization_code` and `client_credentials` grants. |
| POST | `/oauth/revoke` | none | Logout. |
| GET | `/oauth/authorize` | none | Opened in a browser tab. |
| POST | `/accounts` | | Signup. |
| POST | `/emails/confirmations` | | Resend confirmation email. Empty body. |
| GET | `/<invite path>` | none | Checks invite links. Custom `Accept: application/json` header. |

### Own account

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/accounts/verify_credentials` | Session refresh. |
| PATCH | `/accounts/update_credentials` | Multipart. See profile editing. |
| GET | `/preferences` | Reads `posting:default:*` and `reading:expand:*` keys. |
| GET | `/profile` | Profile tab settings. API version 8 or higher. |
| PUT | `/profile` | Body: `show_media`, `show_media_replies`, `show_featured`. |
| DELETE | `/profile/avatar` | |
| DELETE | `/profile/header` | |

Profile editing with `PATCH /api/v1/accounts/update_credentials`:

- Multipart parts: `display_name`, `note`, `avatar`, `header`, `fields_attributes[i][name]`, `fields_attributes[i][value]`.
- Empty profile fields are cleared by sending one empty `fields_attributes[0]` pair.
- Boolean parts: `discoverable`, `indexable`.
- The app resizes the avatar to 400 px, square cropped.
- The app resizes the header to 1500 x 500 pixels.
- The app sends a JSON body for defaults: `locked`, `discoverable`, `indexable`, `source.privacy`, `source.language`, `source.quote_policy`.

### Accounts

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/accounts` | Query: repeated `id[]`. |
| GET | `/accounts/:id` | |
| GET | `/accounts/:id/statuses` | Query: `max_id`, `min_id`, `limit`, `pinned`, `only_media`, `exclude_replies`, `exclude_reblogs`, `tagged`. |
| GET | `/accounts/:id/followers` | Query: `max_id`, `limit`. |
| GET | `/accounts/:id/following` | Query: `max_id`, `limit`. |
| GET | `/accounts/:id/featured_tags` | |
| GET | `/accounts/:id/lists` | Lists that contain the account. |
| GET | `/accounts/:id/endorsements` | Query: `limit`, `max_id`. |
| GET | `/accounts/relationships` | Query: repeated `id[]`, `with_suspended=true`. |
| GET | `/accounts/familiar_followers` | Query: repeated `id[]`. |
| GET | `/accounts/search` | Query: `q`, `limit`, `offset`, `resolve`, `following`. |
| POST | `/accounts/:id/follow` | Body: `reblogs`, `notify`, `ref`. |
| POST | `/accounts/:id/unfollow` | Empty JSON body. |
| POST | `/accounts/:id/block` | Empty JSON body. |
| POST | `/accounts/:id/unblock` | Empty JSON body. |
| POST | `/accounts/:id/mute` | Empty JSON body. |
| POST | `/accounts/:id/unmute` | Empty JSON body. |
| POST | `/accounts/:id/pin` | Endorse. Empty JSON body. |
| POST | `/accounts/:id/unpin` | Remove endorsement. Empty JSON body. |
| POST | `/accounts/:id/note` | Body: `comment`. |
| POST | `/accounts/:id/remove_from_followers` | |
| POST | `/follow_requests/:id/authorize` | |
| POST | `/follow_requests/:id/reject` | |
| GET | `/domain_blocks/preview` | Query: `domain`. |
| POST | `/domain_blocks` | Body: `domain`. |
| DELETE | `/domain_blocks` | Body: `domain`. |
| POST | `/featured_tags` | Body: `name`. |
| DELETE | `/featured_tags/:id` | |
| GET | `/followed_tags` | Query: `max_id`, `limit`. |
| GET | `/tags/:tag` | |
| POST | `/tags/:tag/follow` | |
| POST | `/tags/:tag/unfollow` | |
| GET | `/suggestions` | Prefix `/api/v2`. Query: `limit`. |

The follow request body sets `reblogs` and `notify`.
The `ref` parameter passes an attribution string.

### Statuses

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/statuses` | Header: `Idempotency-Key` with a new UUID. |
| PUT | `/statuses/:id` | Edit. Same body, no idempotency key. |
| DELETE | `/statuses/:id` | |
| GET | `/statuses/:id` | |
| GET | `/statuses` | Query: repeated `id[]`. |
| GET | `/statuses/:id/context` | Reads `Mastodon-Async-Refresh` header. |
| GET | `/statuses/:id/history` | |
| GET | `/statuses/:id/source` | |
| GET | `/statuses/:id/favourited_by` | Query: `max_id`, `limit`. |
| GET | `/statuses/:id/reblogged_by` | Query: `max_id`, `limit`. |
| GET | `/statuses/:id/quotes` | Query: `max_id`, `limit`. |
| POST | `/statuses/:id/favourite` | Empty JSON body. |
| POST | `/statuses/:id/unfavourite` | Empty JSON body. |
| POST | `/statuses/:id/reblog` | Empty JSON body. |
| POST | `/statuses/:id/unreblog` | Empty JSON body. |
| POST | `/statuses/:id/bookmark` | Empty JSON body. |
| POST | `/statuses/:id/unbookmark` | Empty JSON body. |
| POST | `/statuses/:id/pin` | Empty JSON body. |
| POST | `/statuses/:id/unpin` | Empty JSON body. |
| POST | `/statuses/:id/mute` | Mute conversation. Empty JSON body. |
| POST | `/statuses/:id/unmute` | Unmute conversation. Empty JSON body. |
| POST | `/statuses/:id/quotes/:quote_id/revoke` | |
| PUT | `/statuses/:id/interaction_policy` | Body: `quote_approval_policy`. |
| POST | `/statuses/:id/translate` | Body: `lang`. |
| GET | `/bookmarks` | Query: `max_id`, `limit`. |
| GET | `/favourites` | Query: `max_id`, `limit`. |

Create status body fields:

- `status` — post text.
- `media_ids` — list of uploaded attachment IDs.
- `media_attributes` — list of `{id, description, focus}`. Used when editing.
- `poll` — object with `options`, `expires_in`, `multiple`, `hide_totals`.
- `in_reply_to_id`
- `sensitive`
- `spoiler_text`
- `visibility`
- `scheduled_at` — field exists in the request model, but the UI never sets it.
- `language`
- `quote_approval_policy`
- `quoted_status_id`

The `focus` string in `media_attributes` carries the focal point.
The app stores the focal point in the format the API expects.

Interaction counters:

- The app updates favorite, reblog, and bookmark counters optimistically.
- It cancels a pending toggle request when the user taps again.
- The server response replaces the local counter on success.
- On error, the app reverts the optimistic update and shows a toast.

Edit history:

- The app fakes missing fields in the history response (`uri`, `id`, `visibility`, `mentions`, `tags`).
- This keeps the old-version display code working with partial edit records.

### Media

| Method | Path | Prefix | Notes |
| --- | --- | --- | --- |
| POST | `/media` | `/api/v2` | Multipart: `file`, optional `description`. |
| PUT | `/media/:id` | | Body: `description`. |
| GET | `/media/:id` | | Processing poll. |

Upload flow:

- The app uploads one attachment at a time.
- Images resize client-side to the instance `image_matrix_limit`.
- Without a configured limit, the app uses 2,073,600 pixels.
- PNG files stay PNG. Other images become JPEG at quality 97.
- The app sends a description at upload time when the user typed one early.
- The app sends `PUT /media/:id` for each alt-text change.
- Before publishing, the app saves pending alt texts with `PUT /media/:id`.
- When the upload response has an empty `url`, the attachment still processes.
- The app polls `GET /api/v1/media/:id` every second until `url` exists.
- An HTTP 206 response during polling also means processing is not done.

### Polls

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/polls/:id/votes` | Body: `choices` array of indexes. |

### Timelines

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/timelines/home` | Query: `max_id`, `min_id`, `since_id`, `limit`. |
| GET | `/timelines/public` | Query: `local`, `remote`, `max_id`, `min_id`, `since_id`, `limit`. |
| GET | `/timelines/tag/:hashtag` | Query: `max_id`, `min_id`, `limit`. |
| GET | `/timelines/list/:id` | Query: `max_id`, `min_id`, `since_id`, `limit`. |

The app does not use the streaming API. It loads on demand only.

### Notifications

| Method | Path | Prefix | Notes |
| --- | --- | --- | --- |
| GET | `/notifications` | | Legacy. Query: `max_id`, `limit`, repeated `types[]`, repeated `exclude_types[]`, `account_id`. |
| GET | `/notifications` | `/api/v2` | Grouped. See below. |
| GET | `/notifications/unread_count` | `/api/v2` | Query: `types[]`, `grouped_types[]`, `exclude_types[]`. |
| GET | `/notifications/:id` | | |
| GET | `/notifications/requests` | | Query: `max_id`. |
| POST | `/notifications/requests/:id/accept` | | |
| POST | `/notifications/requests/:id/dismiss` | | |
| GET | `/notifications/policy` | | |
| PUT | `/notifications/policy` | | Body: the four filter booleans. |
| POST | `/push/subscription` | | Register. |
| PUT | `/push/subscription` | | Update alerts and policy. |

Grouped notifications query (API version 2 or higher):

- `max_id`, `limit`
- `types[]` when a filter is active
- `grouped_types[]` — the app groups favorites, reblogs, and follows.
- `supported_types[]` — the app sends every notification type it knows.
- `exclude_types[]` — hides disabled admin notifications.
- `account_id` plus `include_filtered=true` — used for the "notifications from this account" view.

Notification types the app sends in `supported_types[]`:

`follow`, `follow_request`, `mention`, `reblog`, `favourite`, `poll`, `status`, `update`, `severed_relationships`, `moderation_warning`, `quote`, `quoted_update`, `admin.sign_up`, `admin.report`, `added_to_collection`, `collection_update`.

The app falls back to the v1 endpoint when the API version is below 2.
On the fallback path, it converts plain notifications into group view models.

Notification policy:

- Fields: `filter_new_accounts`, `filter_not_followers`, `filter_not_following`, `filter_private_mentions`.
- The policy response includes `summary.pending_requests_count`.
- The app shows a "filtered notifications" row when pending requests exist.
- Admin roles see extra checkboxes for admin report and signup notifications.
- Admin checkbox values are local preferences. They become `exclude_types[]` in requests.

Follow requests:

- The app accepts or rejects follow requests inline from the notification.
- The app has no dedicated follow requests list screen.

Unread badge:

- API version 2 or higher: `GET /api/v2/notifications/unread_count`.
- Older servers: the app loads 40 notifications and compares IDs with the read marker.

### Markers

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/markers` | Query: repeated `timeline[]=home`, `timeline[]=notifications`. |
| POST | `/markers` | Body: `home.last_read_id` and/or `notifications.last_read_id`. |

Marker logic:

- The app keeps the notifications marker in local preferences.
- When the server marker moved backwards, a save likely failed.
- The app then re-sends the local marker value.
- ID comparison uses a length-then-lexicographic comparator.

### Filters

| Method | Path | Prefix | Notes |
| --- | --- | --- | --- |
| GET | `/filters` | | Legacy filters for older servers. |
| GET | `/filters` | `/api/v2` | |
| POST | `/filters` | `/api/v2` | Body: `title`, `context[]`, `filter_action`, `expires_in`, `keywords_attributes[]`. |
| PUT | `/filters/:id` | `/api/v2` | Same body. Deleted words use `_destroy`. |
| DELETE | `/filters/:id` | `/api/v2` | |

The app supports two filter actions: `hide` and `warn`.
`expires_in` holds seconds until expiry.

Client-side filtering:

- When the server does not support filters, the app fetches legacy v1 filters each hour.
- The app then removes matching statuses locally.
- When a status carries `filtered` results, the app knows the server applied filters.
- The app still removes statuses whose active filter action is `hide`.
- Filter contexts used: `home`, `notifications`.

### Search

| Method | Path | Prefix | Notes |
| --- | --- | --- | --- |
| GET | `/search` | `/api/v2` | Query: `q`, `type`, `resolve`, `max_id`, `offset`, `limit`. |

Search usage:

- Explore tab searches statuses with `resolve` when the query is a URL.
- Account search: `type=accounts`.
- Hashtag autocomplete: `type=hashtags`.
- Account autocomplete in the composer: `type=accounts`.
- Status search uses `max_id` pagination.
- Account and hashtag searches use `offset` pagination.

The app stores recent searches locally. It stores up to 20 items.

### Trends

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/trends` | Query: `limit=10`. |
| GET | `/trends/links` | Query: `limit=40`. |
| GET | `/trends/statuses` | Query: `limit`, `offset`. |

### Lists

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/lists` | |
| POST | `/lists` | Body: `title`, `replies_policy`, `exclusive`. |
| PUT | `/lists/:id` | Body: `title`, `replies_policy`, `exclusive`. |
| DELETE | `/lists/:id` | |
| GET | `/lists/:id/accounts` | Query: `max_id`, `limit`. |
| POST | `/lists/:id/accounts` | Form body: repeated `account_ids[]`. |
| DELETE | `/lists/:id/accounts` | Form body: repeated `account_ids[]`. |

The app caches the list of lists in SQLite for offline display.

### Reports

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/reports` | Body: `account_id`, `status_ids[]`, `collection_ids[]`, `rule_ids[]`, `comment`, `forward`, `category`. |

The report flow:

- The user selects rule IDs from the instance rules.
- The report can include a status or a collection item.
- The app sends a category (report reason enum) and a comment.
- A switch controls `forward` to the remote server.

### Async refreshes

| Method | Path | Prefix | Notes |
| --- | --- | --- | --- |
| GET | `/async_refreshes/:id` | `/api/v1_alpha` | Poll for refresh results. |

Thread usage:

- `GET /statuses/:id/context` can return a `Mastodon-Async-Refresh` structured header.
- The header carries `id`, `retry`, and `result_count`.
- The app polls the async refresh until the status is `finished`.
- The app shows a snackbar with partial counts during polling.
- One refresh ID can feed several callbacks at once.

### Collections (API version 10 or higher)

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/accounts/:id/collections` | Query: `offset`, `limit`. |
| GET | `/collections/:id` | |
| POST | `/collections/:id/items/:item_id/revoke` | |

The app uses collections for the profile "featured" tab.
The owner can remove a collection item. This revokes the inclusion.
Reports can reference a collection ID.

## 7. Push Notifications

The app uses Web Push through Firebase Cloud Messaging (FCM).

### Registration

1. The app reads the VAPID public key from instance configuration.
2. The app requests an FCM token with a sender-specific subtype.
3. The subtype is `wp:https://<domain>/#<push account id>`.
4. A random push account ID separates subscriptions per account.
5. The app generates an EC P-256 key pair and a 16-byte auth secret.
6. It registers with `POST /api/v1/push/subscription`.

Registration body:

- `subscription.endpoint` — `https://fcm.googleapis.com/fcm/send/<token>`.
- `subscription.keys.p256dh` — raw 65-byte public key, Base64 URL-safe.
- `subscription.keys.auth` — auth secret, Base64 URL-safe.
- `subscription.standard` — true when API version is 4 or higher.
- `data.alerts` — alert booleans for `follow`, `favourite`, `reblog`, `mention`, `poll`, `status`, `quote`.
- `policy` — `all`, `followed`, `follower`, or `none`.

The `standard` flag selects RFC 9420 message encryption.
Older servers use the older draft-03 encryption scheme.
The app keeps both decryptors.

### Alerts update

- Endpoint: `PUT /api/v1/push/subscription`.
- Body: `data.alerts` and `policy`.
- HTTP 404 means the subscription vanished. The app registers again.

### Token refresh

- The app refreshes the FCM token every 30 days.
- App updates force re-registration.
- When the quote alert did not exist in an old token version, the app copies the mention value.

### Decryption

The app implements ECDH key agreement and AES-128-GCM decryption for both encryption formats.
The plaintext is JSON with `notification_type`, `title`, `body`, `icon`, `notification_id`, `access_token`, `preferred_locale`.
The app uses `access_token` to identify the account.
The server only sends a notification payload; the app maps the type to a local string.

## 8. Local Caching

The app caches limited data in SQLite per account.

Cached data:

- Home timeline posts (for instant startup display).
- Notifications, including their accounts and statuses (two variants: all and mentions-only).
- Lists of lists.
- Recent searches (20 items).

Cache read rules:

- The cache answers only when the requested page size matches exactly.
- The app marks a gap after posts that were cached mid-feed.
- On cache miss, the app requests the network and stores the result.
- A pull-to-refresh bypasses the cache.

## 9. External Catalog Endpoints

The signup server picker uses `api.joinmastodon.org`, not the instance API.

| URL | Purpose |
| --- | --- |
| `https://api.joinmastodon.org/categories?language=` | Server categories. |
| `https://api.joinmastodon.org/servers?language=&category=&registrations=all` | Server list. |
| `https://api.joinmastodon.org/default-servers` | Default server. 500 ms timeout. |
| `https://api.joinmastodon.org/v1/donations/campaigns/active?platform=android&locale=&seed=&source=` | Donation campaign. Cacheable. |

The app shows donation campaigns only on `mastodon.social` and `mastodon.online`.
Accounts must be 28 days old or older.
A seeded hash of the full username selects experiment groups.

## 10. What the App Does Not Use

The app ignores several parts of the Mastodon API.

- **Streaming API**: the app never opens the WebSocket endpoint.
  It refreshes timelines on view changes and on pull-to-refresh only.
- **Announcements**: the app never calls `/api/v1/announcements`.
- **Scheduled statuses**: the app can send `scheduled_at`, but no UI sets it.
  There is no scheduled posts list.
- **Rate limits**: the app does not read rate-limit headers or throttle itself.
- **Conversations API**: not used.
- **Directory API**: not used.
- **oEmbed API**: not used.
- **Admin API**: not used. The app only reads admin notifications.
- **Annual reports (Wrapstodon)**: not used.
- **Suggestions removal**: `DELETE /api/v1/suggestions/:account_id` is not used.
- **`exclude_direct` on account statuses**: not used.

## 11. Feature Gaps Compared to the Web Client

The web client offers features the app does not implement.
This list helps you plan a third-party client.

| Feature | Web client | The app |
| --- | --- | --- |
| Live timeline updates | Streaming API | None. Manual refresh only. |
| Announcements | Read, dismiss, react | None. |
| Scheduled posts page | List, edit, delete | Schedule value only, no list. |
| Follow requests screen | Dedicated page | Inline accept/reject from the notification. |
| Blocked and muted accounts lists | Full management | Actions only, no lists. |
| Domain block management | Full list and search | Block/unblock from account menus only. |
| Filters | Same v2 API | Same v2 API, but only hide and warn actions. |
| Directory | Browse by category | None. |
| Explore feed | Trending posts, hashtags, news, accounts | Same, via REST endpoints. |
| Quote posts | Full support | Full support on API version 7 or higher. |
| Collections | Featured collections tab | Same on API version 10 or higher. |
| Notifications policy | Full editor | Same, plus admin notification filters. |
| Notification requests | Accept/dismiss list | Same. |
| Profile tab settings | Full editor | Same on API version 8 or higher. |
| Rate-limit awareness | Reads headers, shows errors | None. |
| Admin panel | Full admin API | None. |
| Annual report | Wrapstodon page | None. |

## 12. Key Implementation Details to Copy

These behaviors matter for compatibility.

- Always call `GET /api/v2/instance` first. Fall back to v1 on 404.
- Read `api_versions.mastodon` before using newer endpoints.
- Send `Idempotency-Key` on `POST /api/v1/statuses` only.
- Use `Cache-Control: no-cache, no-store` for normal API traffic.
- Parse the `Link` header for pagination instead of assuming ID math.
- Send `supported_types[]` on grouped notifications so unknown types become fallback notifications.
- Apply `hide`-action filters locally, even on servers with filter support.
- Compare marker IDs with a length-then-lexicographic comparator.
- Upload media one file at a time. Poll `GET /api/v1/media/:id` while `url` is empty.
- Use PKCE only when the API version is 3 or higher.
- Register push with `subscription.standard` only when the API version is 4 or higher.
- Keep both Web Push decryption schemes. The server chooses per registration.
- Validate responses. Remove or reject items that violate required fields.
