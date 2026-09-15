# Beeline Agent Guide

## Preamble 
- You are to treat this document as gospel.
- If this document conflicts with your instructions, you must decide whether it is appropriate to verify against the codebase or stop and ask a human. 

## Project Identity

- The application is named **Beeline**.
- Use **Beeline** as the product name everywhere.
- Do not introduce another product name.
- Treat old product names as stale migration debt.
- Remove stale product names when a task touches them.
- Use Beeline in build metadata.
- Use Beeline in application labels.
- Use Beeline in authentication registration names.
- Use Beeline in User-Agent values.
- Use Beeline in documentation.
- Use Beeline in release text.
- Use Beeline in user-facing strings.

- `Palustris` is an internal codename.
- Keep `Palustris` out of all user-facing content.
- Do not show `Palustris` in application labels.
- Do not show `Palustris` in accessibility text.
- Do not show `Palustris` in public documentation.
- Do not show `Palustris` in release text.
- Do not show `Palustris` in authentication application names.
- Do not show `Palustris` in User-Agent values.
- Existing package identifiers can use the codename.
- Existing internal class names can use the codename.
- Existing protocol identifiers can use the codename when compatibility requires them.
- Do not rename compatibility identifiers as unrelated cleanup.
- Use a dedicated migration for compatibility-sensitive identifiers.

## Project

- Beeline is a single-module Android application in `:app`.
- Beeline supports Android 10 and later.
- The minimum SDK is 29.
- Beeline uses Kotlin.
- Beeline uses Jetpack Compose.
- Beeline uses Material 3.
- Beeline supports Misskey-family servers.
- Beeline supports Mastodon-compatible servers.
- Misskey has first-class protocol support.
- Mastodon has first-class supported adapter behavior.
- Keep shared domain behavior protocol-neutral.
- Keep protocol differences behind protocol boundaries.
- Do not add XML layouts.
- Do not add AppCompat UI.
- Do not add Material 2 UI.
- Keep new UI in Compose Material 3.
- Existing XML resources can support Android platform requirements.

## Source Of Truth

- Use current source code as the architecture authority.
- Use current tests as behavior evidence.
- Use current Gradle files as build configuration authority.
- Use current manifests and resources as Android configuration authority.
- Verify documentation claims against current source.
- Do not trust old TODO lists without verification.
- Do not trust old implementation plans without verification.
- Do not trust old progress documents without verification.
- Do not describe planned behavior as implemented behavior.
- Do not describe partial behavior as complete behavior.
- Inspect recent changes before you start substantial work.

## Documentation Requirements

- Treat documentation as maintained engineering work, not as a one-time cleanup.
- Serve three audiences separately: code readers, human contributors and users, and agents.
- Keep useful comments and KDoc close to the code.
- Keep human-facing project documentation separate from agent-facing engineering documentation.
- Keep the agent wiki focused on ownership, invariants, protocol boundaries, persistence contracts, affected tests, and verification limits.
- Do not turn the agent wiki into a copy of source code.
- Do not maintain two independently edited copies of the same architecture information.
- Keep the README short and user-facing.
- Keep stable contributor guidance in the human wiki.
- Keep implementation maps and fragile invariants in the agent wiki.
- Keep generated API or protocol material in reference documentation with a repeatable generator or pinned source.
- Keep short-lived plans and task history out of permanent architecture pages.
- Mark temporary plans as `planned` or `historical` when they are not current behavior.
- Remove or archive a document when it no longer provides current evidence or useful history.

### Stale Documentation

- Treat stale documentation as a maintenance defect.
- Treat a document as stale when its behavior, status, ownership, file paths, or verification claims no longer match the repository.
- Treat a document as stale when it duplicates a newer authoritative document without a clear maintenance purpose.
- Do not cite stale documentation as evidence for implementation decisions.
- Do not leave known stale documentation in an active documentation path while presenting a replacement as current.
- Update a stale document when it still explains current behavior or a required contributor process.
- Move a stale document to an explicitly marked archive when it provides useful historical context.
- Delete a stale document when it has no current or historical value.
- Prefer deletion over archiving when an old document would confuse readers.
- Use Git history and task logs for history instead of retaining obsolete technical guidance.
- Review all documents affected by a code change for stale claims before completing the change.
- During each documentation audit, classify every reviewed document as `current`, `planned`, `historical`, `reference`, or `stale`.
- Give every `planned` document an owner, review date, and condition that makes it stale.
- Remove stale status claims, obsolete file manifests, and references to missing documents before publishing a page.
- If cleanup is deferred, record the intended update, archive, or deletion without treating the document as current.

- Use current source code for implemented runtime behavior.
- Use current tests for intended and verified behavior.
- Use Gradle files for local build configuration.
- Use CI workflows for hosted build, test, lint, instrumentation, and release behavior.
- Use `AGENTS.md` for repository operation and non-negotiable constraints.
- Use `logs/` for historical work, unresolved risks, and verification limits.
- Do not use a plan, roadmap, progress note, or old report as proof of current behavior.
- Do not describe planned behavior as implemented behavior.
- Do not describe partial behavior as complete behavior.
- Verify every documentation claim against its authority before publication.

- Give each maintained page an owner, status, last-reviewed date, and links to authoritative code and tests.
- State when runtime, device, or live-server evidence is unavailable.
- Use `source verified`, `test verified`, `device verified`, `live verified`, and `unverified` precisely.
- Do not treat mocked HTTP tests as proof of live-server behavior.
- Do not treat Compose tests as proof of physical-device rendering.
- Do not treat release assembly as proof of signed release publication.
- Update documentation in the same implementation slice when behavior, ownership, architecture, persistence, protocol support, build steps, release steps, test requirements, or important invariants change.
- Do not edit documentation for a private implementation change that leaves documented behavior unchanged.
- Review documentation during every feature change and perform a full source-to-documentation audit before release.

- Add a comment when the reason is not clear from the code.
- Use comments for compatibility behavior, lifecycle and concurrency invariants, security boundaries, protocol quirks, non-obvious fallbacks, bounds, and performance tradeoffs.
- Use KDoc when an API contract is not clear from its type signature.
- Do not comment obvious syntax, assignments, or simple delegation.
- Keep comments near the code that enforces the rule.
- Update or remove comments when source behavior changes.

- Before changing a documented boundary, record the current owner, callers, invariants, and tests.
- Mark refactor notes as temporary until the new boundary is stable.
- Add characterization tests before risky decomposition.
- Update the agent ownership map after a stable boundary changes.
- Update human documentation when user or contributor understanding changes.
- Remove temporary documentation after extracting any still-valid requirement.
- Include documentation review in the definition of done.

## Agent Operation

- Work directly on the requested task.
- Do not use subagents by default.
- Do not delegate work to another agent by default.
- Use a subagent only when the user explicitly requests one.
- Do not create a subagent because the task is large.
- Divide large tasks into implementation slices instead.

- Read the relevant source before you make changes.
- Trace the current behavior before you replace it.
- Identify the current owner of each behavior.
- Identify the affected protocol boundaries.
- Identify the affected persisted state.
- Identify the affected UI state.
- Identify the affected tests.

- Do not stop at an implementation idea.
- Implement the requested behavior unless the user requests only analysis.
- Verify each completed implementation slice.
- Record unresolved problems before you continue.

## Long-Horizon Work

- Treat repository state as authoritative.
- Treat conversation context as disposable.
- Do not use conversation history as the only record of unfinished work.
- Keep permanent rules in `AGENTS.md`.
- Keep the current truth of a long task in `docs/agents/tasks/<task>.md`.
- Keep verified history in Git commits.
- Rewrite the task-state file at each slice boundary.
- Do not append to the task-state file.
- Recover state from files and Git, not from memory.
- Use `/resume` to recover task state.
- Use `/checkpoint` at each slice boundary.

### Task Size Check

- Decide whether the request is safe for one execution before you edit.
- Stop and write a slice plan when the request has several architectural changes.
- Stop and write a slice plan when the request has several unrelated behaviors.
- Stop and write a slice plan when the request has several verification stages.
- Record this statement: `This task is larger than one safe implementation slice.`
- Do not try to finish everything while context permits.

### Slice Definition

- Define a slice by behavior, not by file count.
- Give each slice one purpose.
- Make each slice reviewable on its own.
- Leave the repository working.
- Give each slice one verification method.
- Give each slice one commit.

### Slice Checkpoint

Use this order at every slice boundary:

1. Implement.
2. Test.
3. Inspect the diff.
4. Update the task-state file.
5. Commit.
6. Start the next slice.

- Update the task-state file before you move on.
- Record the last safe commit in the task-state file.

### State Recovery

Read these items at the start of a session, after compaction, and when you are unsure:

1. `AGENTS.md`.
2. The active task-state file in `docs/agents/tasks/`.
3. `git status`.
4. Recent relevant commits.
5. The current diff.

- Rebuild the TODO list from these items.
- Treat TODO lists as execution aids, not as the durable record.
- Stop implementation and reconstruct state when context is incomplete.

## Implementation Slices

- Divide coding work into small coherent slices.
- Give each slice one clear purpose.
- Make each slice independently understandable.
- Make each slice independently verifiable.
- Include required tests in the same slice.
- Include required migrations in the same slice.
- Include required contract changes in the same slice.
- Do not mix unrelated cleanup into a slice.

- Complete one slice before you start the next slice.
- Run the relevant verification for the slice.
- Review the slice for regression risk.
- Update the task logs.
- Commit the completed slice.
- Start the next slice only after the commit.

- Use commits as the permanent work record.
- Make one commit for each completed slice.
- Do not combine several independent slices into one commit.
- Do not leave completed slices uncommitted.
- Do not squash slice commits unless the user requests it.
- Use a clear imperative commit message.
- Stage only files that belong to the slice.
- Preserve unrelated worktree changes.
- Never discard user changes to make a commit clean.

- Do not commit a knowingly broken slice.
- Fix the slice before you commit it.
- Divide the slice again if it became too large.
- Record external blockers in `logs/BUGS.txt`.

## Risk Review

- Consider the effects of a change before implementation.
- Do not consider only the requested happy path.
- Identify likely failure states.
- Identify likely regression states.
- Identify interaction with existing features.
- Identify behavior during partial server support.
- Identify behavior during network failure.
- Identify behavior after process recreation.
- Identify behavior after account switching.
- Identify behavior after session replacement.
- Identify behavior with stale cached data.
- Identify behavior with empty data.
- Identify behavior with malformed remote data.

- Review protocol differences before shared model changes.
- Review storage effects before model changes.
- Review migration effects before stored-format changes.
- Review account isolation before state changes.
- Review pagination before timeline changes.
- Review navigation restoration before navigation changes.
- Review notification delivery before notification changes.
- Review background work before account lifecycle changes.
- Review authentication before origin or session changes.
- Review localization before user-facing text changes.
- Review accessibility before interaction changes.
- Review compact and wide layouts before layout changes.
- Review old Android behavior before platform-specific changes.
- Review performance before adding work to feed rendering.

- Add tests for important failure states.
- Do not add only happy-path tests.
- Record risks that cannot be verified locally.
- Include unresolved risks in the final handoff.

## Maintainability

- Optimize the project for long-term maintenance.
- Do not optimize only for the current patch.
- Keep each file responsible for a small coherent area.
- Keep each class responsible for a clear concept.
- Keep each function responsible for a clear operation.
- Keep state ownership explicit.
- Keep dependencies directional.
- Keep protocol boundaries visible.

- Do not make a large file larger only because it already contains related code.
- Existing complexity does not justify new complexity.
- Existing duplication does not justify new duplication.
- Existing mixed responsibilities do not define the preferred architecture.

- Treat large coordinator files as decomposition targets.
- Do not use a large coordinator file as the default location for new behavior.
- Extract a focused owner when a file gains another major responsibility.
- Extract reusable UI from screen coordinators.
- Extract state models from large presentation files.
- Extract navigation policy from feature UI.
- Extract protocol behavior from shared UI.
- Extract persistence behavior from ViewModels.
- Extract formatting logic when several screens use it.

- Do not create generic helper files without a clear domain.
- Do not create dumping-ground utility classes.
- Do not create broad `Utils` objects.
- Do not create broad `Managers` without defined ownership.
- Do not move complexity into a new file without improving boundaries.

- Prefer feature packages when a feature has several related files.
- Keep screen UI near its feature.
- Keep feature state near its feature.
- Keep feature ViewModels near their feature.
- Keep feature-specific presentation helpers near their feature.

- Prefer composition over one large configurable component.
- Prefer small state holders over one global mutable state object.
- Prefer explicit dependencies over hidden global access.
- Prefer clear domain types over loosely related primitive values.
- Prefer one canonical implementation over copied behavior.

- Refactor when a requested feature exposes an unsafe boundary.
- Keep refactors limited to the boundary required by the task.
- Do not perform unrelated architecture rewrites.
- Preserve behavior during structural refactors.
- Add characterization tests before risky decomposition.

## Architecture

- `domain/` owns protocol-neutral models and contracts.
- `data/` owns data access and persistence implementations.
- `data/misskey/` owns Misskey transport and mapping behavior.
- `data/mastodon/` owns Mastodon transport and mapping behavior.
- `data/auth/` owns authentication and session storage.
- `data/notifications/` owns notification data behavior.
- `data/directmessages/` owns direct-message data behavior.
- `data/preferences/` owns persisted application preferences.
- `data/media/` owns media data behavior.
- `data/emoji/` owns emoji data behavior.
- `ui/` owns Compose presentation.
- Feature-specific UI packages own feature presentation.
- Hilt provides application dependencies through dependency injection.

- `SocialSource` is the shared social source contract.
- Protocol adapters implement shared source contracts.
- `SocialSourceFactory` creates sources for authenticated sessions.
- `AccountSourceRegistry` associates sources with accounts.
- `AccountManager` owns account and session state.

- Keep account ownership outside Compose functions.
- Keep transport behavior outside Compose functions.
- Keep protocol JSON outside Compose functions.
- Keep persistent storage outside Compose functions.

- Use dedicated ViewModels for substantial independent feature state.
- Do not make one ViewModel own unrelated destinations.
- Do not tie background synchronization to a screen lifetime.

## Protocol Boundary

- Keep shared domain models protocol-neutral.
- Do not put Misskey JSON structures into shared models.
- Do not put Mastodon JSON structures into shared models.
- Map protocol data at the adapter boundary.
- Normalize failures before they reach generic UI.

- Use `ServerCapabilities` for feature availability.
- Use capability probes for protocol support.
- Do not hard-code server software checks in UI.
- Do not add protocol branches to generic Compose UI.
- Do not add protocol branches to generic ViewModels.
- Keep protocol branches in adapters.
- Keep protocol branches in authentication.
- Keep protocol branches in source creation.
- Keep protocol branches in capability detection.

- Keep unknown capability state separate from unsupported state.
- Keep denied access separate from unsupported behavior.
- Keep temporary failure separate from unsupported behavior.
- Do not mark a feature unsupported after one failed request.

- Keep cursors opaque above adapters.
- Do not construct protocol pagination URLs in UI.
- Do not construct protocol pagination URLs in generic ViewModels.
- Bind cursors to their account and query.
- Bind cursors to their protocol variant.
- Preserve transport order during pagination.
- Do not compare opaque identifiers numerically.
- Do not compare opaque identifiers lexically.
- Do not infer time from opaque identifiers.

## Authentication And Accounts

- Detect the protocol before protocol-specific authentication.
- Store servers as validated HTTPS origins.
- Reject origins with credentials.
- Reject origins with paths.
- Reject origins with queries.
- Reject origins with fragments.

- Bind each account to its connection origin.
- Bind each account to its protocol.
- Bind each account to its local account identifier.
- Scope registration credentials to their connection origin.
- Scope access grants to their account session.
- Scope tokens to their account session.
- Scope capabilities to their account session.

- Preserve existing sessions when the user adds an account.
- Preserve unrelated sessions during reauthentication.
- Verify an account before session replacement.
- Reject stale authentication callbacks.
- Stop account streams before account removal.
- Disable account push before account removal.
- Remove account-scoped local state during account removal.
- Treat remote cleanup as best effort.
- Do not block local sign-out because a server is unavailable.

## Network Security

- Validate origins before authenticated requests.
- Validate pagination origins before authenticated requests.
- Validate entity origins before account actions.
- Reject foreign authenticated pagination URLs.
- Reject authenticated URLs with credentials.
- Reject authenticated URLs with fragments.
- Keep authenticated redirects disabled unless a reviewed protocol flow requires them.
- Do not send credentials to an unvalidated origin.

## Storage And Privacy

- Keep account secrets in no-backup storage.
- Keep sessions account-scoped.
- Encrypt stored session secrets.
- Use Android Keystore protection for session encryption.
- Keep pending authentication data encrypted.
- Keep drafts account-scoped.
- Keep notification data separate from authentication secrets.
- Keep preferences scoped correctly.
- Delete account-scoped data during account removal.

- Never log access tokens.
- Never log client secrets.
- Never log authorization codes.
- Never log push keys.
- Never log authorization headers.
- Never log complete API response bodies.
- Redact secrets from diagnostic URLs.
- Redact credentials from diagnostics.
- Redact long opaque identifiers when they can contain sensitive data.

## Direct Messages

- Beeline has direct-message inbox and conversation behavior.
- Do not describe direct messages as a placeholder feature.
- Keep direct-message domain contracts protocol-neutral.
- Keep protocol-specific direct-message behavior in adapters.
- Use capability or source support to determine availability.
- Do not assume native Misskey chat semantics.
- Do not present federated direct posts as encrypted messaging.
- Do not claim private messages are secure or encrypted.

## Interaction Data

- Keep post interaction counts protocol-neutral.
- Use `PostInteractionCounts` for normalized counts.
- Preserve unknown counts as unknown.
- Do not convert missing counts to zero without evidence.
- Keep favourites distinct from emoji reactions.
- Keep total reposts distinct from quote reposts.
- Preserve protocol differences during mapping.
- Update optimistic counts with their related post action.
- Reconcile optimistic values with server responses.

## Photo Grid

- Photo Grid belongs to the Search destination.
- Photo Grid has independent feed state.
- Do not reuse Home feed state for Photo Grid.
- Keep Photo Grid timeline selection independent from Home.
- Keep saved Photo Grid hashtags independent from Home.
- Use server capabilities for available Photo Grid timelines.
- Do not show unsupported timeline types.
- Keep Photo Grid media filtering explicit.
- Preserve Photo Grid navigation state across supported restoration paths.

## Preferences And Settings

- Keep persisted settings outside Compose functions.
- Use repository-owned preference state.
- Keep application-wide preferences separate from account-specific preferences.
- Keep post preferences separate from application display preferences.
- Apply settings through defined policy owners.
- Do not duplicate preference state in unrelated ViewModels.
- Make migrations explicit when stored preference formats change.
- Preserve safe defaults when a new preference has no stored value.

## Moderation

- Keep moderation contracts protocol-neutral.
- Keep moderation transport inside adapters.
- Use moderation capabilities for feature support.
- Keep blocked users separate from muted users.
- Keep muted hashtags separate from account mute data.
- Keep unsupported moderation types explicit.
- Validate account origins before moderation actions.
- Refresh affected state after successful moderation changes.

## Notifications

- Keep notification ingestion separate from presentation.
- Use the notification repository as the merge point for notification data.
- Bind writes to the correct account generation.
- Reject stale account writes.
- Preserve local seen state during refresh.
- Preserve server acknowledgement state during refresh.
- Preserve Android presentation state during refresh.
- Keep notification identity separate from group identity.
- Keep account identity separate from notification identity.
- Keep local dismissal separate from server acknowledgement.

- Do not alert for initial baseline imports.
- Use adapter unread state when available.
- Preserve unread precision.
- Do not infer exact unread counts from one loaded page.
- Use actor account identifiers for actor actions.
- Do not use notification identifiers as account identifiers.

## Synchronization And Delivery

- Keep REST reconciliation as the freshness authority where designed.
- Keep foreground streams owned by their stream controller.
- Reconcile after incomplete stream events.
- Use bounded retry behavior.
- Treat unsupported stream behavior as a terminal stream state.
- Use WorkManager for supported background work.
- Re-read account state when background work starts.
- Use unique work for account-scoped jobs.
- Keep push registration account-scoped.
- Validate push callback ownership.
- Validate session revisions.
- Validate endpoint generations.
- Keep notification presentation policy separate from ingestion.

## Compose And UI

- Keep screen state ownership clear.
- Prefer stateless presentation components.
- Hoist state when a parent owns the behavior.
- Do not make reusable components depend on protocol types.
- Do not make reusable components depend on account managers.

- Keep floating controls independent from scroll content.
- Keep rows behind floating controls transparent where the design requires it.
- Keep final scroll items reachable above floating controls.
- Put obstruction clearance inside scroll content.
- Do not inset an entire full-screen viewport only to reserve a floating control.
- Preserve IME positioning for search and input controls.
- Preserve wide-layout behavior.
- Test compact layouts.
- Test wide layouts.
- Test system font scaling.
- Test long translated text where relevant.

## Coding Rules

- Follow official Kotlin coding conventions.
- Prefer imports over fully qualified names.
- Avoid wildcard imports.
- Keep related declarations together.
- Use one technical name for one concept.
- Preserve exact code identifiers in technical documentation.
- Keep comments short.
- Explain only non-obvious behavior in comments.

- Do not copy protocol logic between adapters.
- Do not copy substantial UI behavior between screens.
- Extract shared behavior when it has one stable meaning.
- Do not abstract two pieces of code only because they look similar.
- Use clear names instead of explanatory comments where possible.

- Preserve unrelated worktree changes.
- Do not revert user changes.
- Do not reformat unrelated files.
- Do not modify unrelated code only to make the diff cleaner.

## Writing Style

- Read the project writing-style file before you write.
- Follow that style in all agent-written text.
- Use Simplified Technical English.
- Use approved simple words where possible.
- Use technical nouns when necessary.
- Give one term one meaning.
- Do not change terms only to avoid repetition.

- Use active voice.
- Use short sentences.
- Give one main instruction in each sentence.
- Use simple verb forms.
- Do not omit necessary words.
- Do not use contractions.
- Use one name for one thing.
- Avoid long noun groups.
- Use American English spelling.

- Use the same style in task logs.
- Use the same style in documentation.
- Use the same style in plans.
- Use the same style in comments.
- Use the same style in commit messages where practical.
- Use the same style in final handoff text.
- Use the same style in user-facing copy unless product tone requires another style.

- Do not change exact code identifiers to fit the writing style.
- Do not change protocol names to fit the writing style.
- Do not change official API field names to fit the writing style.

## Task Logs

- Treat `logs/` as an audit trail, not as agent memory.
- Keep the current truth in `docs/agents/tasks/`.
- Keep verified history in Git commits.

- Create `logs/YYMMDD-HHMMSS.txt` when a coding task starts.
- Record the task goal in the task log.
- Record the planned slices in the task log.
- Record affected files in the task log.
- Record important risks in the task log.

- Track active long work in `docs/agents/tasks/<task>.md`.
- Rewrite the task-state file at each slice boundary.
- Do not leave completed slices in the task-state file.

- Append completed results to `logs/DONE.txt`.
- Keep each result concise.
- Record the related verification.
- Record the related commit when useful.

- Record failures in `logs/BUGS.txt`.
- Record unresolved concerns in `logs/BUGS.txt`.
- Record external blockers in `logs/BUGS.txt`.
- Remove obsolete bug entries when the issue is resolved.

- Keep all logs free of secrets.
- Do not put complete server responses in logs.
- Do not put credentials in logs.
- Do not put access tokens in logs.

- If the user forbids source changes, do not edit source code.
- In that case, edit only the required task logs.
- Do not use a log change to hide an unauthorized source change.

## Verification

- Verify each implementation slice before you commit it.
- Run the smallest relevant test set first.
- Run focused unit tests for changed behavior.
- Run adapter contract tests after adapter changes.
- Run persistence tests after storage changes.
- Run account lifecycle tests after session changes.
- Run Compose tests after presentation changes.
- Run instrumented tests for Android-only behavior.

- Run lint after authentication changes.
- Run lint after storage changes.
- Run lint after adapter changes.
- Run lint after contract changes.
- Run lint after notification changes.
- Run lint after security-sensitive changes.

- Use the Gradle wrapper for every Gradle command.
- Add `--no-daemon --console=plain` to every agent Gradle command.
- Use `./gradlew --no-daemon --console=plain` in Unix shell examples.
- Use `gradlew.bat --no-daemon --console=plain` in Windows shell examples.
- Do not run bare `gradlew` without `--no-daemon` in agent work.
- Set `GRADLE_OPTS=-Dorg.gradle.daemon=false` as a safety net for agent environments.
- Set an explicit timeout for every Gradle tool call.
- Close standard input for non-interactive Gradle calls.

- Run `./gradlew --no-daemon --console=plain test assembleRelease` before you declare a coding task complete.
- Run additional required checks for the affected feature.
- Fix failures caused by the current slice.
- Do not hide failing tests.
- Do not disable tests only to complete a task.

- Do not treat mocked HTTP tests as proof of live server behavior.
- Do not treat Compose tests as proof of physical device rendering.
- Do not treat authentication tests as proof of browser callback behavior.
- Do not treat local notification tests as proof of real push delivery.
- Record unverified live-server checks in the final handoff.
- Record unverified device checks in the final handoff.

## Dependency Changes

- Do not add a dependency when existing platform APIs are sufficient.
- Check maintenance status before you add a dependency.
- Check Android version support before you add a dependency.
- Check license compatibility before you add a dependency.
- Record the reason for each new dependency in `logs/DONE.txt`.
- Keep dependency scope as narrow as possible.
- Do not add AppCompat.
- Do not add Material 2.

## Completion

- Confirm that each requested behavior is implemented.
- Confirm that each completed slice has a commit.
- Confirm that required tests pass.
- Confirm that task logs are current.
- Confirm that no secrets entered the diff.
- Confirm that unrelated user changes remain intact.
- Confirm that protocol boundaries remain intact.
- Confirm that new files have clear responsibilities.
- Confirm that existing files did not gain unrelated responsibilities.
- Confirm that user-facing product text says Beeline.
- Confirm that the internal codename did not enter user-facing content.
- Report remaining risks.
- Report checks that you could not perform.
