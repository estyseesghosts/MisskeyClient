# UI and Navigation

Status: planned  
Owner: UI maintainers  
Last reviewed: 2026-09-16  
Stale when: A destination, layout policy, restoration rule, or accessibility requirement changes.

Sources: `AGENTS.md`, `ui/`, Compose tests, and instrumented tests.

## Purpose

<!-- Explain the visible application structure for contributors. -->

## Entries

<!-- Add compact and wide layouts, destinations, Photo Grid, profiles, threads, restoration, and accessibility. -->

## Profiles

A profile shows a category row. The row order is Featured, Posts, Replies,
Media, Reposts, Liked, and Show more. The Posts, Replies, Media, and Reposts
feeds are protocol-neutral profile timelines.

The Featured tab leads only when the profile has more than one pinned post. A
single pinned post appears at the top of the Posts feed with no Featured tab.
Pinned posts do not appear in the other profile feeds. Featured renders the
pinned posts the profile already loaded, so it makes no new request.

The Liked tab shows Mastodon favourites or Misskey reactions. It appears for
the signed-in account on either protocol and for another Misskey account. It
does not appear for another Mastodon account, because Mastodon exposes
favourites only to the signed-in account.

Sources: `ui/profile/ProfileCategory.kt`, `ui/profile/ProfileTimelineList.kt`,
`ui/profile/ProfileViewModel.kt`, `data/mastodon/MastodonProfileService.kt`,
`data/misskey/MisskeyProfileService.kt`, `ProfileScreenTest`.

