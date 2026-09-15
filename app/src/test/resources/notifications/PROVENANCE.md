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
| `complete_current_state.json` | All ten top-level keys. Two receiving protocols. Multiple items. Receiving-account identity. Dismissal IDs. Singular and keyed checkpoints. Deliveries, settings, and push registration. |
| `legacy_minimal_state.json` | Absent version. Omitted modern fields. Target-only navigation. Legacy reaction `imageUrl`. |
| `activity_variants.json` | Every current activity discriminant. All four system variants. Unknown activity with a validated server destination. |
| `navigation_variants.json` | In-app destinations for post, profile, poll, and conversation. A validated server destination. |
| `navigation_malformed.json` | An unsafe server URL that falls back to the target, and an invalid URL that drops the destination. |
| `read_states.json` | Read, unread, and unknown. Independent local-seen, server-acknowledged, Android-presented, and Android-dismissed flags. |
| `delivery_variants.json` | Every delivery state. Claims, expiry, attempt count, tags, and IDs. |
| `delivery_duplicate_ids.json` | Two delivery records for one notification. The last record wins. |

The `activity_variants`, `navigation_variants`, `read_states`, and `delivery_variants` fixtures
are encoder-stable. The `legacy_minimal_state`, `navigation_malformed`, and
`delivery_duplicate_ids` fixtures characterize the decoder only; the encoder does not reproduce
their omitted or rejected fields.

All account names, origins, and endpoints are synthetic. No real notification history,
access token, or push credential appears in these files.
