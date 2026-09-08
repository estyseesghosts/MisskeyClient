# Notifications compatibility notes

Updated September 8, 2026 for the notification implementation work. This is a
conservative implementation matrix, not a promise that every moving server
release supports every notification feature.

## Baseline contracts

| Concern | Misskey-family baseline | Mastodon baseline | App behavior |
|---|---|---|---|
| Listing | `POST /api/i/notifications`; `sinceId`/`untilId`; requests must send `markAsRead=false` | `GET /api/v1/notifications`; type filters and absolute `Link` continuations | Both adapters use account/query-bound opaque cursors; Mastodon v1 is the compatibility path and v2 is opt-in grouping |
| Read acknowledgement | Account-wide `POST /api/notifications/mark-all-as-read` | Notification timeline marker through `/api/v1/markers` | Modeled as explicit acknowledgement; no fetch implicitly marks the inbox read |
| Unread knowledge | User-level unread fields vary by server version; individual notification records do not guarantee a read flag | Timeline markers and server-specific support vary | `Exact`, `LowerBound`, `Boolean`, `None`, and `Unknown` are distinct |
| Grouping | Optional `i/notifications-grouped`, with Misskey-specific paging | Optional `/api/v2/notifications`; older servers use v1 | Group identity is account-scoped and never replaces notification identity |
| Push | `sw/register`, update, and unregister lifecycle | `/api/v1/push/subscription`; requires the separate `push` OAuth scope | UnifiedPush connector 3.3.5 delivers Web Push endpoints to an account-scoped manager; both adapters register through `PushSubscriptionSpec` |

The Misskey listing and read-all behavior is based on the endpoint sources linked
from the roadmap. Mastodon application registration and push scope behavior are
based on the official application and push method documentation.

## Synthetic coverage

The shared contract fixture suite currently covers:

- actorless and unknown Mastodon notifications;
- actorless and custom-emoji Misskey reactions;
- canonical post targets and account-scoped notification identity;
- rejected non-HTTPS or credential-bearing destinations;
- notification capability resolution across server, access, and client states;
- old encrypted sessions and pending-login migration;
- Mastodon app-cache scope compatibility and permission-upgrade identity safety.

These fixtures intentionally use inline JSON so no access tokens, private posts,
or distributor credentials are stored in the repository or logs.

## Open Milestone 0 evidence

The authenticated UnifiedPush round trip is still not verified in this
workspace. The app now includes the official connector, an Android `PushService`,
encrypted connector key handling, a stable opaque instance per account, endpoint
rotation, server subscription registration, payload-safe catch-up hints, and
logout cleanup. Device delivery, distributor behavior, server-version behavior,
and process-death recovery remain live-test gates.

## Milestone 2 implementation evidence

The REST adapters now cover the planned notification foundation:

- Misskey uses `markAsRead=false` for every listing, supports include-type filters,
  `sinceId`/`untilId` traversal, explicit unread lookup, account-wide read-all,
  follow-request actions, actorless/system records, and Misskey-specific grouped
  reaction/renote shapes.
- Mastodon supports v1 type filters, previous/next Link continuations, v2 grouped
  notifications with actor/status expansion, unread-count lookup, follow-request
  actions, safe optional-status mapping, and the quote-specific `quoted_update`
  activity.
- Adapter cursors carry the receiving account, query fingerprint, API variant, and
  direction. Continuations are validated for origin, route, credentials, and
  repeated-cursor termination before a bearer token is attached.
- Synthetic contract coverage is in
  `NotificationAdapterContractTest`; malformed optional records remain renderable,
  required notification identity remains diagnosed, and grouped rows retain a
  separate account-scoped group identity.

Live authenticated server delivery and device presentation remain intentionally
unverified until the user-provided Sunup distributor and test accounts are used.

## UnifiedPush implementation evidence

- `org.unifiedpush.android:connector:3.3.5` is pinned in the version catalog;
  no FCM dependency or fallback was added.
- Sunup is selected when it is installed and available. If no distributor is
  available, settings expose a visible setup state instead of claiming delivery.
- The connector callback never presents arbitrary push text. It rejects
  credential-bearing payload shapes and schedules authenticated REST catch-up;
  the repository and delivery planner remain the only source of alert content.
- Foreground WebSocket streams are lifecycle-bound; background delivery uses
  UnifiedPush and account-unique WorkManager reconciliation.
- The Mastodon adapter uses v1 push subscription endpoints even when inbox
  listing uses v2 grouped notifications; these are separate Mastodon APIs.

References: [Mastodon applications](https://docs.joinmastodon.org/methods/apps/),
[Mastodon push subscriptions](https://docs.joinmastodon.org/methods/push/),
[Misskey API permissions](https://github.com/misskey-dev/misskey-hub/blob/main/src/docs/api/index.md),
and the endpoint sources linked in [the notifications roadmap](roadmaps/notifications.md).
