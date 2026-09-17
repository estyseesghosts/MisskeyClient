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

Photo Grid detail sizes the photo pager from known attachment dimensions
in compact and wide layouts. The pager keeps full width. The pager keeps
`ContentScale.Fit`. The post body stays below the media.

The height follows the active photo aspect. Square through 4:5 uses the
natural height. Media at or wider than 16:9 uses the natural short height.
The short height prevents vertical letterboxing. Media taller than 4:5
stays capped at the 4:5 viewport. A short viewport clamps the height to
the available space. The calculation reserves 64 dp for the header and
200 dp for post content.

Unknown or invalid dimensions use the square to 5:4 viewport fallback.
Multi-photo posts share one stable height across all pages. The shared
height is the smallest aspect-aware height. The shared height prevents
vertical letterboxing for the active photo. Horizontal letterboxing on
taller pages is the accepted tradeoff.

Sources: `ui/SinglePostScreen.kt`, `ui/photogrid/PhotoPagerSizing.kt`,
`SinglePostScreenTest`.

