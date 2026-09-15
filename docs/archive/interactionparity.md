# Task

Make all post interaction controls work in every applicable Beeline view.

Work from the current **v0.2.1** codebase.

Before you change code:

1. Confirm that the application version is `0.2.1`.
2. Read `importantdocs/writing_style.md`.
3. Follow that writing style for all plans, notes, logs, and documentation.
4. Inspect the current interaction architecture.
5. Inspect the v0.2.1 share sheet.
6. Inspect existing tests before you add new tests.
7. Identify shared interaction code before you change individual screens.

Do not create separate implementations for each feed.

Reuse the same post actions, state, and protocol abstractions where possible.

This work expands existing functionality.

It must not remove or break existing functionality.

# Required views

Verify all applicable post actions in each of these views:

- Home feed.
- The current user's profile feed.
- Search feed.
- A single post opened from Home.
- A single photo opened from the photo grid.
- Another user's profile feed.
- The current user's own profile.

Do not assume that one post component proves that all views work.

Some views can use different wrappers, state owners, or navigation paths.

Inspect each path.

# Required post actions

A user must be able to perform all applicable actions from every required view.

The actions are:

- Repost.
- Quote.
- Reply.
- Favourite on Mastodon-compatible accounts.
- React on Misskey-compatible accounts.
- Open and use the v0.2.1 share sheet.

Use the existing protocol behavior.

Do not make Mastodon and Misskey use the same network action when their APIs differ.

# Required coverage matrix

Use this matrix as the minimum test scope.

| View | Repost | Quote | Reply | Favourite / React | Share |
|---|---|---|---|---|---|
| Home feed | Required | Required | Required | Required | Required |
| Own profile feed | Required | Required | Required | Required | Required |
| Search feed | Required | Required | Required | Required | Required |
| Single post from Home | Required | Required | Required | Required | Required |
| Single photo from grid | Required | Required | Required | Required | Required |
| Other user's profile | Required | Required | Required | Required | Required |
| Own profile | Required | Required | Required | Required | Required |

Expand this matrix if source inspection finds another user-facing post view.

Do not reduce coverage because two screens look similar.

# Immediate interaction feedback

Every successful user action must have an immediate visible response.

Do not make the user wait for a feed refresh to know that an action occurred.

The visible state must also remain synchronized with server state.

# Favourite

For Mastodon-compatible posts:

- Update the favourite state immediately.
- Show the existing active favourite indication.
- Keep the state after recomposition and navigation.
- Restore the correct state after a refresh.

If the request fails, restore the previous state.

Show suitable failure feedback.

# Reaction

For Misskey-compatible posts:

- Update the reaction state immediately.
- Add the selected reaction to the visible reaction row immediately.
- Update an existing reaction entry correctly when applicable.
- Keep the state after recomposition and navigation.
- Restore server state correctly after a refresh.

Do not wait for a complete timeline reload before the reaction appears.

If the request fails, restore the previous state.

# Repost

When a repost completes:

- Update the repost state immediately.
- Show the existing active repost indication immediately.
- Keep the state synchronized across other visible copies of the post.

When the user removes a repost:

- Update the state immediately.
- Remove the active indication.

Preserve the existing repost confirmation flow.

Do not bypass the v0.2.1 confirmation behavior.

If the network request fails, restore the previous state.

# Reply

The existing reply composer must work from every required view.

After a successful reply, show this toast:

`reply sent`

Do not show the success toast before the server accepts the reply.

The new reply must become visible the next time the user opens an appropriate conversation or comment view.

Do not require the user to clear application data or restart Beeline.

Update or invalidate the required state so subsequent views can obtain the new reply.

# Quote

The existing quote flow must work from every required view.

After a successful quote, show this toast:

`quote sent`

Do not show the success toast before the server accepts the quote.

Update the relevant local state after the operation succeeds.

Do not require a full application restart before the quote state becomes correct.

# Share sheet

Every required view must open the share sheet introduced in v0.2.1.

Do not create a second share sheet for views where the current one is not wired correctly.

Use the existing shared share-sheet implementation.

Verify its actions from posts reached through every required view.

At minimum, verify:

- Follow or unfollow.
- Block.
- Mute.
- Report.
- PM.
- Copy.
- Share.

The post and account passed into the share sheet must match the post that the user selected.

# Shared state

A post can appear in more than one part of Beeline.

An interaction in one view must not create obviously inconsistent state in another view.

For example:

1. Favourite a post in Home.
2. Open the same post.
3. The post must still show the favourite state.

Apply the same principle to:

- Reposts.
- Reactions.
- Replies where visible.
- Other interaction state that Beeline stores locally.

Inspect the existing state architecture before you add another cache or state owner.

Prefer one authoritative post state path.

# Mastodon and Misskey

Test both protocol families.

For Mastodon-compatible accounts, verify at minimum:

- Favourite.
- Repost.
- Quote where the server and current Beeline implementation support it.
- Reply.
- Share sheet.

For Misskey-compatible accounts, verify at minimum:

- Reaction.
- Repost or renote behavior.
- Quote.
- Reply.
- Share sheet.

Do not hide protocol errors by disabling controls.

If an action is genuinely unsupported by a server, handle that case explicitly.

# Automated tests come first

Complete automated testing before live device testing starts.

Do not use live device testing as a replacement for automated tests.

Add tests that cover the shared interaction behavior.

Add regression tests for screen-specific wiring where necessary.

Tests must cover:

- Correct post identifier.
- Correct account.
- Correct protocol path.
- Repost.
- Undo repost.
- Quote.
- Reply.
- Mastodon favourite.
- Favourite removal.
- Misskey reaction.
- Reaction changes or removal where supported.
- Share-sheet opening.
- Immediate local state changes.
- Failed request rollback.
- Success feedback.
- Navigation paths into single-post views.
- Photo-grid post interactions.

Do not add meaningless tests that only verify that a button exists.

Tests must verify behavior.

# Automated test order

Run tests in this order:

1. Unit tests for action and state logic.
2. Protocol contract tests.
3. UI or Compose tests for interaction wiring.
4. Regression tests for existing behavior.
5. Full available automated test suite.

Fix test or implementation failures before live testing starts.

Do not continue to physical-device validation with known automated test failures.

# Live device testing

After all automated tests pass, perform stringent testing on the supplied physical Android device.

The device contains a current copy of Beeline.

Test with:

- One Mastodon-compatible account.
- One Misskey-compatible account.

Authenticate both accounts as required.

Do not test only the screens that changed.

Exercise the complete interaction matrix.

# Mastodon live test pass

For each required view:

1. Find a suitable post.
2. Repost it.
3. Verify the immediate visual state.
4. Remove the repost where practical.
5. Verify the immediate visual state.
6. Create a quote where supported.
7. Verify the `quote sent` toast.
8. Reply.
9. Verify the `reply sent` toast.
10. Favourite the post.
11. Verify the immediate favourite indication.
12. Remove the favourite.
13. Verify the state changes.
14. Open the share sheet.
15. Verify the share-sheet actions that can safely be exercised.

Also leave and re-enter relevant views.

Confirm that state remains correct.

# Misskey live test pass

Repeat the same coverage with the Misskey-compatible account.

For reactions:

1. Add a reaction.
2. Verify that it appears immediately in the reaction row.
3. Change or remove the reaction where supported.
4. Verify the immediate state.
5. Leave the view.
6. Return to the post.
7. Confirm that the server state and displayed state agree.

Test quotes, replies, reposts, and sharing in the same manner.

# Reply persistence test

For both protocol families:

1. Open a post.
2. Write a reply.
3. Submit it.
4. Verify `reply sent`.
5. Leave the post.
6. Return to a view that shows the conversation.
7. Verify that the new reply appears.

Test more than one entry path where necessary.

# Visual validation

Live testing must verify visual response as well as network success.

Look for:

- Buttons that do nothing.
- Delayed state changes.
- Wrong selected states.
- Duplicate reactions.
- Missing reactions.
- Repost state that does not update.
- Controls that disappear after interaction.
- Toasts behind another surface.
- Incorrect post actions in photo-grid views.
- Incorrect post passed to a share sheet.
- Recomposition that resets interaction state.
- Layout shifts after state changes.
- Interaction indicators that differ between feeds.

A successful API request is not sufficient if the user interface does not show the result correctly.

# Regression requirements

Absolutely nothing should regress.

This work adds completeness to existing post interaction functionality.

Preserve:

- Existing feeds.
- Feed selection.
- Photo grid.
- Profiles.
- Search.
- Post detail.
- Media.
- Emoji picker behavior.
- Existing gestures.
- Hashtag bubbles.
- Truncated links.
- Navigation.
- Composer behavior.
- Notifications.
- Account switching.
- Settings.
- v0.2.1 share-sheet behavior.
- Existing Mastodon support.
- Existing Misskey/Sharkey support.

Run the full regression suite after the interaction work passes its own tests.

# Architecture requirements

Do not repair each screen with copied callback logic.

Find the common action path.

Reuse it.

A screen should supply the correct post and account to shared action behavior.

Keep protocol logic out of presentation code where the existing architecture permits this.

Do not add more responsibility to an already oversized screen file only because that is the fastest local fix.

If a shared interaction boundary is missing, create a focused one.

Do not perform unrelated refactoring.

# Completion criteria

Do not mark this task complete until all conditions are true:

- Every required interaction works in every required view.
- Mastodon live testing passes.
- Misskey live testing passes.
- Automated tests pass first.
- The full regression suite passes.
- Favourite state changes immediately.
- Reaction state changes immediately.
- Reactions appear immediately in the reaction row.
- Repost state changes immediately.
- Successful replies show `reply sent`.
- Successful quotes show `quote sent`.
- New replies appear in later appropriate views.
- The v0.2.1 share sheet works from every required view.
- No existing Beeline feature regresses.

Record the tests that you ran.

Record the live-device paths that you verified.

Do not claim completion from compile-time success alone.