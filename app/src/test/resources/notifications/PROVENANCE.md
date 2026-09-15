# Notification Codec Fixtures

Owner: Notification persistence maintainers.

Status: current.

Last reviewed: 2026-09-15.

Stale when: the notification stored format, the codec schema, or a fixture expectation changes.

These are synthetic characterization fixtures. They were assembled from the current
`NotificationJsonCodec` schema and reviewed by hand. They are not captures of a released
app file. Do not describe them as verified released-file captures.

| Fixture | Coverage |
| --- | --- |
| `complete_current_state.json` | All ten top-level keys. One receiving account with two items. Receiving-account identity. Dismissal IDs. Singular and keyed checkpoints. Deliveries, settings, and push registration. |
| `legacy_minimal_state.json` | Absent version. Omitted modern fields. Target-only navigation. Legacy reaction `imageUrl`. |
| `activity_variants.json` | Every current activity discriminant. All four system variants. Unknown activity with a validated server destination. |
| `navigation_variants.json` | In-app destinations for post, profile, poll, and conversation. A validated server destination. |
| `navigation_malformed.json` | An unsafe server URL that falls back to the target, and an invalid URL that drops the destination. |
| `read_states.json` | Read, unread, and unknown. Independent local-seen, server-acknowledged, Android-presented, and Android-dismissed flags. |
| `delivery_variants.json` | Every delivery state. Claims, expiry, attempt count, tags, and IDs. |
| `delivery_duplicate_ids.json` | Two delivery records for one notification. The last record wins. |
| `posts_and_accounts.json` | Recursive quotes, moved accounts, profile fields, emoji maps, attachments, polls, wrapper IDs, and action IDs. |
| `interaction_counts.json` | Missing, null, zero, positive, negative, numeric-string, fractional, overflow, malformed-text, and boolean counts. |
| `unread_states.json` | Manifest. Exact, at-least, present, none, unknown, missing, negative normalization, and an unknown kind. |
| `settings_states.json` | Manifest. Absent settings, empty object, empty categories, unknown categories, valid and out-of-range quiet hours, distributor, preview, and fallback flags. |
| `push_states.json` | Manifest. Every persisted push field, an endpoint-only connected legacy record, a revision default, and unavailable-state retry metadata. |
| `checkpoints.json` | Singular and keyed checkpoints with all cursor fields, completeness, baseline, and capture time. |
| `checkpoint_fallback.json` | Missing completeness, capture time, and baseline decode to their defaults. |
| `malformed_root_shape.json` | Wrong root value types decode to an empty state. |
| `malformed_entries.json` | Invalid item, dismissal, and delivery entries drop one at a time. |
| `malformed_checkpoint.json` | A singular checkpoint without its account or query fails the complete decode. |
| `malformed_push.json` | A push registration without its account or instance fails the complete decode. |
| `malformed_broken.json` | Text that is not JSON. The file store returns no state; the Room store throws. |
| `known_omissions.json` | Post visibility and group actor continuation are not persisted. |

The `unread_states`, `settings_states`, and `push_states` fixtures are case manifests. Each key
holds a full state object for the decoder. They exist because the codec stores one unread,
settings, or push value per state.

The `activity_variants`, `navigation_variants`, `read_states`, `delivery_variants`,
`posts_and_accounts`, and `checkpoints` fixtures are encoder-stable. The `legacy_minimal_state`,
`navigation_malformed`, `delivery_duplicate_ids`, `interaction_counts`, `checkpoint_fallback`,
`known_omissions`, and all `malformed_*` fixtures characterize the decoder only; the encoder
does not reproduce their omitted or rejected fields.

All account names, origins, and endpoints are synthetic. No real notification history,
access token, or push credential appears in these files.
