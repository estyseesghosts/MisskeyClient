# Beeline - an opinionated fediverse metaclient. 

![screenshots](https://files.catbox.moe/5zem98.webp)

## manifesto - readme starts below

my fedi pet peeve for a long time has been how disjointed and disconnected it feels to use different services across an interconnected network 

i dont necessarily think my approach is the correct one either. at least for myself, what i want out of this is an app i can use to both browse and post my photos, and an app i can use to keep up with the text post side of things. different apps can, and should, do different things - but on a network like this i think they work better together. 

using mastodon for this purpose feels awkward, using pixelfed feels too clunky and inflexible, and while misskey checks all the boxes, i don't like the UX of the other mobile apps. i have strong opinions about interfaces, and especially in an era where we are used to being trapped in algorithmic hell, i want to build a gate out. one that feels familiar and less overwhelming, without the crazy map-your-whole-life-to-sell-you-Algorithm. 

i might talk shit but i truly mean love to the mastodon and pixelfed projects - of which i have philosophical differences with, but have been built by hand over the course of years. i made this shit with codex in under a week. i would also like to shoutout the maintainer of moshidon and the developers of pixelix - the aforementioned projects are all why i'm making this right now. 

## readme

so this is beeline - a metaclient for the fediverse, built in kotlin and compose for android. made from the start with modern tooling and interfaces, meant to look and feel even better to use than the big guys' apps. 

why call it a metaclient? what even is that? 
it was made from the start to work with multiple applications, that work in different ways, to make them feel like a cohesive part of a social ecosystem. to abstract away the instances and the software they run to make the fediverse feel like one thing you can interact with and observe, whichever way you want to. 

this is nowhere near the end. i would still call this app an unfinished prototype, but basic reading functionality works. i plan to add pixelfed support soon. the ui has weird quirks which i still have to document (mostly by design, although there is at least one visual bug.) the app currently supports mastodon-compatible servers (with glitch/pleroma extensions) and misskey (including compatible forks like sharkey). 

native misskey chats are not implemented and are currently not planned - private message posts from mastodon are reimplemented here for misskey. native misskey chats are bound only to the local instance, and are not globally interoperable. private messages are NOT secure or encrypted at this point in time, in any way. you should only use them for sharing memes. 

this project is open source and licenced under the gpl v3. see license.md. please take inspiration from it. the fediverse is already something beautiful. it feels like the last safe space away from corporate social media. reddit, twitter, instagram and tiktok, facebook, using them anymore just feels like rotting. i think we as a society deserve better than that. 

## todo

uploading media attachments is not yet supported. additionally, for misskey, we should support drive in a basic way. 

content warnings are not fully implemented. reporting is not implemented. blocking and muting are not yet fully implemented. 

comments are missing entirely. 

misskey antennas are not yet supported. viewing your followed hashtags or lists on mastodon is not supported yet either. 

possibly a hell of a lot more. i haven't fully thoroughly tested this.
