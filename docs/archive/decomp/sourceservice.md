**Explore: Identify protocol-service extraction boundaries**
- **Scope:** `data/misskey/MisskeySource.kt`, `data/mastodon/MastodonSource.kt`, and their existing protocol-specific services and tests.
- **Trace:** Follow thread loading and timeline loading from each `SocialSource` method through requests, pagination, continuation, mapping, and error handling.
- **Questions:**
  - Which thread and timeline logic can move behind protocol-specific services without changing the `SocialSource` contract?
  - Which limits, cursors, continuation rules, and cancellation behavior must remain exact?
  - Which logic must stay in the source facade because it defines capabilities or shared source behavior?
- **Evidence:** Report exact methods, endpoints, service dependencies, constants, tests, and a move-only extraction boundary for each protocol. Do not implement changes.
