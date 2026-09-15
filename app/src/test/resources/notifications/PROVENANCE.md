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

All account names, origins, and endpoints are synthetic. No real notification history,
access token, or push credential appears in these files.
