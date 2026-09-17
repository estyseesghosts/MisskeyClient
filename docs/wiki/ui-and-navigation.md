# UI and Navigation

Status: planned
Owner: UI maintainers
Last reviewed: 2026-09-17
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

## Photo Grid Detail Media

Photo Grid detail sizes the photo pager from the detail viewport in compact
and wide layouts. The pager keeps full width. The height stays between
square and 5:4 vertical. The calculation reserves 64 dp for the header and
200 dp for post content. A short viewport clamps the media to the largest
safe height. The post body stays below the media.

Sources: `ui/SinglePostScreen.kt`, `ui/photogrid/PhotoPagerSizing.kt`,
`SinglePostScreenTest`.

