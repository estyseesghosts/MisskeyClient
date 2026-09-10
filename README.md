# MisskeyClient

An Android fediverse client for Misskey-family servers and Mastodon. It is made from scratch with protocol-neutral domain models and adapters.

MisskeyClient currently supports authenticated home timelines, profile detail refresh, profile editing, saved posts, direct-message surfaces, notifications, notification synchronization and delivery, and profile Posts/Media/Reposts/Replies views. `Show more...` displays inline profile details without making a timeline request.

![screenshots](https://files.catbox.moe/lygr0r.webp)

## Verification TODO

- verify live UnifiedPush notifications for Mastodon across the release/device matrix
- verify or explicitly support a compatible Misskey/Sharkey push credential/server combination; current MiAuth secure-endpoint rejection uses REST/foreground fallback
- verify server-version compatibility and the wider device/theme matrix for profiles
- get boosts and quote boosts working for mastodon
- get reposts and quote reposts working for misskey 
- get emoji reactions fully functional for misskey 
- eventually get emoji reactions fully functional over mastodon api (for pleroma/akkoma) 
- inline quote posts (ones that have RE: link at the start, ones that have a link at the end, proper quote posts since i think misskey does those?) 
