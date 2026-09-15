**Explore: Separate screen code from shared post UI**
- **Scope:** `ui/Screens.kt`, `ui/HomeFeed.kt`, and direct callers of shared composables defined in those files.
- **Trace:** Follow Search, composer, drafts, `HomeFeed`, `PostRow`, post body, metadata, interaction controls, and `AccountAvatar` through all call sites.
- **Questions:**
  - Which declarations are feature-specific, and which are shared UI?
  - Which moves require visibility or import changes only?
  - Which shared components have behavior or test tags that must remain unchanged?
- **Evidence:** Report exact symbols, all important call sites, relevant tests, and a minimal move-only file split. Do not implement changes.
