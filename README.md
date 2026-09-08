# an unnamed fediverse client for android.

very loosely based on moshidon. made from scratch to work with both the misskey and mastodon apis from the jump. a continuation of the last experiment. 

An early Android client for Misskey-family servers and Mastodon. It currently supports authenticated home timelines, profile detail refresh, profile editing, and profile Posts/Media/Reposts/Replies views through protocol-neutral adapters. `Show more...` displays inline profile details without making a timeline request.

![screenshots](https://files.catbox.moe/lygr0r.webp)

TODO 

- verify live UnifiedPush notifications for Mastodon across the release/device matrix
- verify or explicitly support a compatible Misskey/Sharkey push credential/server combination; current MiAuth secure-endpoint rejection uses REST/foreground fallback
- verify server-version compatibility and the wider device/theme matrix for profiles
- get boosts and quote boosts working for mastodon
- get reposts and quote reposts working for misskey 
- get emoji reactions fully functional for misskey 
- eventually get emoji reactions fully functional over mastodon api (for pleroma/akkoma) 
- inline quote posts (ones that have RE: link at the start, ones that have a link at the end, proper quote posts since i think misskey does those?) 
