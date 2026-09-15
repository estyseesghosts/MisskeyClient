**Explore: Map safe boundaries inside `NotificationRepository.kt`**
- **Scope:** `data/notifications/NotificationRepository.kt`, its stores, models, push callers, and notification repository tests.
- **Trace:** Follow page ingestion, merge rules, cursors, read state, delivery state, generation tokens, persistence, and JSON encode/decode.
- **Questions:**
  - Which code is pure transformation logic that can move to reducers?
  - Which code must remain inside the synchronized repository boundary?
  - Which serialization functions can move without changing stored JSON compatibility?
- **Evidence:** Report exact symbols, synchronization points, persistent fields, generation checks, and tests needed to lock current behavior. Do not implement changes.
