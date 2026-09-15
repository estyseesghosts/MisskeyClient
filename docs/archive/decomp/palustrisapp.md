**Explore: Map safe extraction boundaries in `PalustrisApp.kt`**
- **Scope:** `ui/PalustrisApp.kt`, `ui/AppShellState.kt`, and direct UI helpers called by `PalustrisApp`.
- **Trace:** Follow navigation state, saveable state, `BackHandler`, effects, sheets, dialogs, composer state, selected-post state, and destination rendering.
- **Questions:**
  - Which declarations can move without changing state lifetime or composition order?
  - Which state and effects must stay in `PalustrisApp` during the first extraction pass?
  - Which extraction boundaries could change Back handling, saved state, or modal behavior?
- **Evidence:** Report exact file paths, symbols, state keys, effect sites, and tests that protect each proposed boundary. Do not implement changes.
