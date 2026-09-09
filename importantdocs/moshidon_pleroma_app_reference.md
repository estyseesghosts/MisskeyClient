# Moshidon and the Pleroma Extended Mastodon API

This document describes how the moshidon Android application uses the extended Mastodon API of Pleroma.
The document is a reference for developers who reimplement Pleroma features in other applications.

All file paths are relative to the `moshidon/mastodon/src/main/java` directory.
`org/joinmastodon/android` is omitted from the file paths.

## 1. Terms and Scope

The application means the moshidon Android application.
Pleroma means the Pleroma family of server software.
This family includes Pleroma, Akkoma, and their forks.
The server means the remote Pleroma instance that the application connects to.

The code base is Java.
The application is a fork of the official Mastodon for Android application.
The application adds Pleroma support on top of the Mastodon API client.

## 2. Server Detection

The application detects Pleroma servers from the instance response.
The detection logic resides in `model/Instance.java`.

The instance response contains a `pleroma` object on Pleroma servers.
The application models this object in `Instance.Pleroma` (`Instance.java:268`).
The method `Instance.isAkkoma()` returns true when the `pleroma` object exists (`Instance.java:101`).

```java
public boolean isAkkoma() {
    return pleroma != null;
}
```

The application treats every server with a `pleroma` object as Akkoma.
The name of the method is not exact.
A plain Pleroma server also returns this object.

The application also inspects the `version` string of the instance.
This string contains `compatible; Pixelfed` on Pixelfed servers.
This string contains `compatible; Iceshrimp` on Iceshrimp servers.
The methods `isPixelfed()` and `isIceshrimp()` check these substrings (`Instance.java:105`).
The method `isIceshrimpJs()` checks for `compatible; Iceshrimp ` with a trailing space (`Instance.java:115`).

Fragments reuse the detection through the `HasAccountID` interface.
The method `isInstanceAkkoma()` delegates to `Instance.isAkkoma()` (`fragments/HasAccountID.java:17`).

## 3. Instance Data From Pleroma

The application loads instance data with two requests.
The first request fetches `/api/v2/instance`.
The second request fetches `/api/v1/instance` when the first request returns HTTP 404.
This fallback logic resides in `AccountSessionManager.loadInstanceInfo()` (`api/session/AccountSessionManager.java:677`).

Pleroma servers do not implement `/api/v2/instance`.
A Pleroma server therefore returns the v1 payload.

The application parses these Pleroma fields from the v1 payload:

- `max_toot_chars` into `Instance.maxTootChars` (`Instance.java:44`).
  The compose screen uses this value as the character limit when the value exceeds zero (`fragments/ComposeFragment.java:225`).
- `poll_limits` into `Instance.PleromaPollLimits` (`Instance.java:51`).
  The application never reads this value.
- `pleroma.metadata.features` into a list of feature names (`Instance.java:274`).
  The application reads two feature names from this list.
- `pleroma.metadata.fields_limits` into `Instance.Pleroma.Metadata.FieldsLimits` (`Instance.java:279`).
  The application never reads this value.
- `configuration.reactions` into `Instance.ReactionsConfiguration` (`Instance.java:247`).
  The configuration contains `max_reactions` and `default_reaction`.

The feature check method is `Instance.hasFeature()` (`Instance.java:124`).
The method reads the `pleroma.metadata.features` list.
The method supports two features:

| Feature | List entry | Use in the application |
| --- | --- | --- |
| BUBBLE_TIMELINE | `bubble_timeline` | Default-on bubble timeline tab |
| MACHINE_TRANSLATION | `akkoma:machine_translation` | None |

No code reads the MACHINE_TRANSLATION feature.
The translation feature section explains this in detail.

## 4. The Bubble Timeline

Pleroma provides the bubble timeline extension.
The bubble timeline shows posts from the known local network of the instance.

The application defines the bubble timeline in `model/TimelineDefinition.java:373`.
The definition overrides two compatibility methods:

- `isCompatible()` returns true for every server with the `pleroma` object (`TimelineDefinition.java:375`).
  The comment states that some instances omit the `bubble_timeline` feature.
  The application therefore enables the timeline for all Pleroma servers.
- `wantsDefault()` returns true when the feature list contains `bubble_timeline` (`TimelineDefinition.java:382`).
  The application adds the bubble tab by default on these servers.

The fragment `fragments/discover/BubbleTimelineFragment.java` loads the timeline.
The fragment calls the request class `api/requests/timelines/GetBubbleTimeline.java`.

The request uses this endpoint:

- `GET /api/v1/timelines/bubble`
- Query parameters: `max_id`, `limit`, `reply_visibility`

The application passes `reply_visibility` only when the local preference has a value.
The section about reply visibility explains this preference.

## 5. Emoji Reactions

Pleroma extends statuses with emoji reactions.
The application parses two JSON keys for reactions in `model/Status.java`:

- `reactions` into `Status.reactions` (`Status.java:93`)
- `emoji_reactions` into `Status.emojiReactions` (`Status.java:94`)

Older Pleroma versions returned `emoji_reactions`.
Newer Akkoma versions renamed the key to `reactions`.
The method `Status.postprocess()` copies `emojiReactions` into `reactions` (`Status.java:134`).
This mapping supports both key names.

The reaction model is `model/EmojiReaction.java`.
The model contains:

- `count` — the number of users
- `me` — whether the current user reacted
- `name` — the shortcode or the unicode emoji
- `url` and `staticUrl` — image URLs for custom emoji
- `accounts` and `accountIds` — the reacting accounts

The current build does not render reactions on statuses.
The display item `EmojiReactionsStatusDisplayItem` exists.
No code adds this display item to a status (`ui/displayitems/StatusDisplayItem.java`).
Only the announcements screen instantiates the item, and only for non-Pleroma servers (`fragments/AnnouncementsFragment.java:65`).

### 5.1 Reaction Endpoints

The application defines three Pleroma request classes:

| Class | Method and path | Use |
| --- | --- | --- |
| `PleromaAddStatusReaction` | `PUT /api/v1/pleroma/statuses/:id/reactions/:emoji` | None |
| `PleromaDeleteStatusReaction` | `DELETE /api/v1/pleroma/statuses/:id/reactions/:emoji` | None |
| `PleromaGetStatusReactions` | `GET /api/v1/pleroma/statuses/:id/reactions/:emoji` | Reaction list screen |

The add and delete classes are dead code.
The method `EmojiReactionsStatusDisplayItem.createRequest()` hardcodes the Akkoma flag to false (`EmojiReactionsStatusDisplayItem.java:112`).
The commented line that read the flag states: "For now I won't mess with these."
The method therefore selects these classes instead:

- `POST /api/v1/statuses/:id/react/:emoji` for adding
- `POST /api/v1/statuses/:id/unreact/:emoji` for removing

These two endpoints do not belong to the Pleroma API.
The application defines them in `AddStatusReaction.java` and `DeleteStatusReaction.java`.
They target Iceshrimp-style servers.

### 5.2 The Reaction List Screen

The screen `fragments/account_list/StatusEmojiReactionsListFragment.java` lists the reacting accounts.
The screen always calls `PleromaGetStatusReactions` (`StatusEmojiReactionsListFragment.java:64`).
The endpoint returns a list with one `EmojiReaction` object.
The screen reads `accounts` from the first entry.
The screen is only reachable from the reactions display item.
The reactions display item is effectively unreachable.
The screen therefore counts as dead code in the current build.

### 5.3 Favorite-to-Reaction Mapping

The class `api/StatusInteractionController.java` synchronizes favorites with a default reaction.
This behavior applies to Iceshrimp-JS servers only (`StatusInteractionController.java:113`).
The code reads `configuration.reactions.default_reaction`.
It adds or removes the default reaction when the user favorites or unfavorites a post.
This logic never runs on Pleroma servers.

### 5.4 Status of the Feature

The reaction feature is broken in the current build.
The request classes for the Pleroma endpoints exist.
The display code that uses them is commented out.
A reimplementation must wire the display item into `StatusDisplayItem.buildItems()`.
A reimplementation must also restore the Akkoma flag in `createRequest()`.
Then the application would call the correct Pleroma endpoints.

## 6. Local-Only Posts

Pleroma uses the visibility value `local` for local-only posts.
The Mastodon API defines only four visibility values.
The application extends the enum `model/StatusPrivacy.java`:

```java
@SerializedName("local")
LOCAL(4); // akkoma
```

The method `Status.postprocess()` maps this visibility to the boolean `Status.localOnly` (`Status.java:133`).

The application treats local-only posts as boostable.
The method `StatusPrivacy.isReblogPermitted()` allows reblogging of local posts (`StatusPrivacy.java:27`).
The footer uses the normal boost icon for local posts (`ui/displayitems/FooterStatusDisplayItem.java:167`).

The compose screen does not offer the local-only option.
The visibility picker lists four values: public, unlisted, followers-only, direct (`fragments/ComposeFragment.java:1111`).
The picker omits `LOCAL`.

The compose screen inherits the local visibility when replying to a local post.
The compose screen keeps the reply visibility (`ComposeFragment.java:1120`).
The application cannot start a local-only post directly.
The Pleroma web client offers this toggle.
A reimplementation must add the `local` option to the visibility picker.

## 7. The reply_visibility Parameter

Pleroma accepts the `reply_visibility` query parameter on timelines.
This parameter filters replies in the timeline.
Allowed values are `self`, `following`, and `all`.

The application stores the value in `AccountLocalPreferences.timelineReplyVisibility` (`api/session/AccountLocalPreferences.java:40`).
The comment on the field states "akkoma-only".

These requests send the parameter:

- `GetPublicTimeline` — used by local, federated, and custom local timelines (`api/requests/timelines/GetPublicTimeline.java:29`)
- `GetListTimeline` — used by list timelines (`api/requests/timelines/GetListTimeline.java:23`)
- `GetBubbleTimeline` — used by the bubble timeline (`api/requests/timelines/GetBubbleTimeline.java:19`)

The application adds the parameter only when the preference is not null.
No settings screen writes this preference.
The strings for the setting exist in `strings_sk.xml`.
The setting UI was removed.
The parameter is therefore never sent in the current build.
A reimplementation must restore the settings UI.
A reimplementation must add the values `self`, `following`, and `all`.

## 8. Quote Posts

Pleroma does not document quote posts in its Mastodon API.
The application enables the quote button for all Pleroma servers.
The overflow menu checks `instance.isAkkoma() || instance.isIceshrimp()` (`ui/displayitems/FooterStatusDisplayItem.java:412`).
On these servers the application bypasses the Mastodon quote approval checks.

The compose screen sends these fields for quotes (`fragments/ComposeFragment.java:858`):

- `quote_approval_policy` — only when the instance supports Mastodon quote authoring
- `quoted_status_id` — the ID of the quoted status

The request model is `api/requests/statuses/CreateStatus.java:30`.
The field `quoted_status_id` is a Mastodon parameter.
The Pleroma API does not define this parameter.
A reimplementation must check the quote implementation of the target server.
Some Pleroma forks accept the Mastodon parameter.
The parameter format may differ on other servers.

The Mastodon path uses `Instance.supportsQuotePostAuthoring()`.
This method returns true when the Mastodon API version is at least 7 (`Instance.java:96`).
Pleroma never reports this version.
The Pleroma path therefore always uses the bypass.

## 9. Account Data

Pleroma adds the `fqn` field to account objects.
The `fqn` field contains the fully qualified username.
The application models this field in `model/Account.java:142`:

```java
public @Nullable String fqn;
```

The method `Account.getFullyQualifiedName()` prefers the `fqn` value (`Account.java:211`).
The method falls back to building the name from the `acct` field and the URL host.
The method `Account.postprocess()` populates the fallback when `fqn` is null (`Account.java:177`).

The application does not model other Pleroma account extensions.
These missing fields include `pleroma_settings_store`, `is_admin`, `is_moderator`, and `also_known_as`.

## 10. Content Types

Pleroma accepts the `content_type` parameter when creating statuses.
Pleroma supports these types: `text/plain`, `text/markdown`, `text/bbcode`, and `text/html`.
Akkoma adds `text/x.misskeymarkdown` for Misskey Markdown.

The application defines the enum `model/ContentType.java`.
The enum contains all five types plus an unspecified value.
The method `supportedByInstance()` permits BBCode and Misskey Markdown only on Pleroma servers (`ContentType.java:36`).

The application stores two local preferences (`api/session/AccountLocalPreferences.java:34`):

- `defaultContentType` — the preferred content type
- `contentTypesEnabled` — whether to use content types

The application never reads these preferences.
No compose or edit request sends `content_type`.
The `CreateStatus.Request` model does not contain the field.
The feature is dead code in the current build.
A reimplementation must add `content_type` to the create and edit requests.

The Pleroma web client offers this choice in the composer.
The web client stores the default content type in the Pleroma settings store.

## 11. Machine Translation

Akkoma provides a machine translation endpoint.
The application defines the request class `api/requests/statuses/AkkomaTranslateStatus.java`.
The class uses this endpoint:

- `GET /api/v1/statuses/:id/translations/:lang`

The response model `model/AkkomaTranslation.java` contains `text` and `detectedLanguage`.
The model converts itself to the shared `Translation` model.
The provider string is "Akkoma".

No code calls this request class.
The translation menu always calls the Mastodon v2 endpoint.
The method `BaseStatusListFragment.togglePostTranslation()` uses `TranslateStatus` (`fragments/BaseStatusListFragment.java:963`).
That class uses this endpoint:

- `POST /api/v1/statuses/:id/translate` with the JSON body `{"lang": "..."}`

The translation menu appears when `Status.isEligibleForTranslation()` returns true (`model/Status.java:227`).
The eligibility check does not test the instance type.
On Pleroma servers without the Mastodon endpoint, translation fails.

The instance check `Instance.hasFeature(MACHINE_TRANSLATION)` reads the `akkoma:machine_translation` feature.
No code calls this check.
A reimplementation must select `AkkomaTranslateStatus` when the feature exists.
A reimplementation must fall back to `TranslateStatus` otherwise.

## 12. Compose Previews

Akkoma provides a status preview endpoint.
The application models the flag `Status.preview` (`model/Status.java:90`).
The comment states the flag exists "for akkoma compose previews".
The footer disables interactions on preview statuses (`ui/displayitems/FooterStatusDisplayItem.java:264`).

Nothing sets the `preview` flag.
The preview request class and the preview screen do not exist in the code base.
The feature is dead code.
A reimplementation needs the preview endpoint from the Pleroma API:
`POST /api/v1/statuses` with the query parameter `preview=true`.

## 13. Streaming

The application parses the streaming URL from the instance configuration.
The field is `Instance.Configuration.URLsConfiguration.streaming` (`Instance.java:216`).
Pleroma returns this URL in the `configuration.urls` object.

The application never connects to the streaming endpoint.
The code base contains no websocket or server-sent events client.
The application uses polling and push notifications instead.
A reimplementation that wants live updates must read the `streaming` field.
Pleroma accepts the standard Mastodon streaming API on that URL.

## 14. Notifications

The notification types of the application do not include Pleroma types.
The enum `model/NotificationType.java` lists these types:

- follow, follow_request, mention, reblog, favourite, poll, status, update
- severed_relationships, moderation_warning, quote, quoted_update

Pleroma sends additional types.
Pleroma sends `pleroma:emoji_reaction` for reaction notifications.
Pleroma sends `move` for account migrations.

The Gson parser uses the default enum adapter (`api/MastodonAPIController.java:58`).
The default adapter throws an error for unknown enum values.
One unknown notification therefore fails the whole notifications response.
The notifications list shows an error toast on Pleroma servers with such notifications.

The display code also throws for unknown types.
The class `ui/displayitems/NotificationHeaderStatusDisplayItem.java` switches on the type (`NotificationHeaderStatusDisplayItem.java:88`).
The default branch throws `IllegalStateException`.
A reimplementation must add the Pleroma types to the enum.
A reimplementation must add display cases for each new type.

## 15. Web Page Links

The application builds links to the server web client.
The link format differs between Pleroma and Mastodon.

- The local timeline links to `/main/public` on Pleroma (`fragments/discover/LocalTimelineFragment.java:62`).
  Mastodon uses `/public/local`.
- The bubble timeline links to `/main/bubble` on Pleroma (`fragments/discover/BubbleTimelineFragment.java:66`).
- The announcements screen links to `/announcements` on Pleroma (`fragments/AnnouncementsFragment.java:109`).
  Pleroma does not implement announcements.

The account screen does not build a media tab link.
The comment in `fragments/AccountTimelineFragment.java:183` explains why.
The web URLs differ per server family (`#media` on Pleroma, `/media` on Mastodon).
The application returns the plain profile URL instead.

## 16. Version Gating and Its Risks

The application gates Mastodon features by the `apiVersions` map.
This map exists only in the v2 instance payload (`model/InstanceV2.java:17`).
Pleroma servers return the v1 payload.
The method `InstanceV1.getApiVersion()` returns 0 for every API (`InstanceV1.java:93`).

The Mastodon API version gates these features:

- Notifications grouping uses version 2 (`api/CacheController.java:229`).
  The application calls `GetNotificationsV1` on Pleroma servers.
- The unread notifications count uses version 2 (`fragments/HomeFragment.java:323`).
  The application recomputes the count from notifications on Pleroma servers.
- Quote post authoring uses version 7 (`Instance.java:96`).
  The application bypasses the check on Pleroma servers with the `isAkkoma()` test.

These bypasses are deliberate.
A reimplementation must not use the `apiVersions` map for Pleroma features.
The map is empty or absent on Pleroma servers.

Other version risks:

- The `emoji_reactions` key of statuses was renamed to `reactions` in newer Akkoma versions.
  The application reads both keys.
- The `bubble_timeline` feature entry is missing on some Pleroma servers.
  The application enables the bubble timeline for all Pleroma servers.
- The `akkoma:machine_translation` feature exists even when the admin disables translation.
  A reimplementation must handle translation errors gracefully.

## 17. Feature Comparison With the Pleroma Web Client

This section lists Pleroma web client features that the application lacks.

### 17.1 Chats

The Pleroma web client supports chats.
The Pleroma chat API resides under `/api/v1/pleroma/chats`.
The application has no chat code.
A reimplementation must add the chat API client and the chat UI.

### 17.2 Scrobbles

The Pleroma web client shows scrobbles on profiles.
The scrobble API resides under `/api/v1/pleroma/scrobble`.
The application has no scrobble code.

### 17.3 Emoji Packs

The Pleroma web client manages emoji packs.
The API resides under `/api/pleroma/emoji/packs`.
The application reads the `category` field of custom emojis (`model/Emoji.java:34`).
The application does not manage emoji packs.

### 17.4 The Pleroma Settings Store

The Pleroma web client stores user settings in `pleroma_settings_store`.
This store keeps preferences across devices.
The application uses only the standard Mastodon preferences.
The application stores extra preferences locally.
These local preferences do not synchronize between devices.

### 17.5 Local-Only Toggle

The Pleroma web client offers a local-only toggle in the composer.
The application cannot start a local-only post.
See the section about local-only posts.

### 17.6 Content Type Selection

The Pleroma web client offers a content type choice in the composer.
The application defines the types but does not send the parameter.
See the section about content types.

### 17.7 Profile Field Limits

The Pleroma web client enforces the field limits from `pleroma.metadata.fields_limits`.
The application parses the limits but does not enforce them.
A reimplementation must validate field names and values.

### 17.8 Poll Limits

The Pleroma web client enforces `poll_limits`.
The application parses the limits but does not enforce them.

### 17.9 Emoji Reaction Notifications

The Pleroma web client shows reaction notifications.
The application does not model the `pleroma:emoji_reaction` type.
See the section about notifications.

### 17.10 Account Migration

The Pleroma web client shows account moves.
Pleroma sends the `move` notification type.
The application does not model this type.

### 17.11 Streaming

The Pleroma web client uses the streaming API.
The application uses polling.
See the section about streaming.

### 17.12 Announcements

The application requests announcements on every server (`fragments/HomeTabFragment.java:313`).
Pleroma does not implement the announcements API.
The request fails and shows an error toast on Pleroma servers.
A reimplementation must gate the request by instance type.

### 17.13 Followed Hashtags

The application requests followed hashtags on every server (`fragments/HomeTabFragment.java:301`).
Pleroma does not implement this Mastodon API.
The request fails with an error toast on Pleroma servers.

## 18. Summary of Pleroma Endpoints

The table lists the Pleroma endpoints that the application defines.

| Endpoint | Method | Use | State |
| --- | --- | --- | --- |
| `/api/v1/pleroma/statuses/:id/reactions/:emoji` | PUT | Add reaction | Dead code |
| `/api/v1/pleroma/statuses/:id/reactions/:emoji` | DELETE | Remove reaction | Dead code |
| `/api/v1/pleroma/statuses/:id/reactions/:emoji` | GET | List reacting accounts | Wired but unreachable |
| `/api/v1/statuses/:id/translations/:lang` | GET | Akkoma machine translation | Dead code |
| `/api/v1/timelines/bubble` | GET | Bubble timeline | Wired and used |

The table lists the Mastodon endpoints that the application uses for Pleroma servers.

| Endpoint | Method | Use |
| --- | --- | --- |
| `/api/v1/instance` | GET | Instance detection and Pleroma fields |
| `/api/v1/timelines/public` | GET | Local and federated timelines, with `reply_visibility` |
| `/api/v1/timelines/list/:id` | GET | List timelines, with `reply_visibility` |
| `/api/v1/statuses` | POST | New posts, with `quoted_status_id` for quotes |
| `/api/v1/statuses/:id/translate` | POST | Translation (may fail on Pleroma) |
| `/api/v1/statuses/:id/react/:emoji` | POST | Reaction add (wrong server family) |
| `/api/v1/statuses/:id/unreact/:emoji` | POST | Reaction remove (wrong server family) |

## 19. Sources

The most important files for a reimplementation:

- `model/Instance.java` — Pleroma field models and detection
- `model/Status.java` — local-only mapping and reaction keys
- `model/StatusPrivacy.java` — the `local` visibility
- `model/ContentType.java` — the Pleroma content types
- `model/TimelineDefinition.java` — the bubble timeline definition
- `api/requests/timelines/GetBubbleTimeline.java` — the bubble timeline request
- `api/requests/statuses/PleromaAddStatusReaction.java` — add reaction request
- `api/requests/statuses/PleromaDeleteStatusReaction.java` — remove reaction request
- `api/requests/statuses/PleromaGetStatusReactions.java` — reaction list request
- `api/requests/statuses/AkkomaTranslateStatus.java` — machine translation request
- `ui/displayitems/EmojiReactionsStatusDisplayItem.java` — reactions UI, currently disabled
- `api/session/AccountLocalPreferences.java` — Pleroma local preferences
