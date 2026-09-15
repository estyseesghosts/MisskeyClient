# Objective

Add missing localized keys to Beeline locale files from mastodon-android and Aria references. No new wording. Only strings files change.

# Invariants

- Keep every existing translation in each locale file.
- Add only keys with an exact English match in at least one reference.
- Keep placeholders identical to the default catalog.
- Keep resource kinds identical to the default catalog (strings only, no new plurals).
- Never touch code, es-419, or locales outside the slice list.
- Skip platform-specific words (boost, renote, note, toot and equivalents).

# Decisions

- Prefer the mastodon-android match when both references match the same English text.
- Skip Likes collection keys (reference maps Likes to a count label), Comments (reference maps it to an annotation term), and Direct audience (reference term is protocol jargon).
- Check the Post mapping per language and skip it when the reference word is verb-like.
- Leave unmapped keys in English fallback.

# Completed

- Slice zh-CN — added 44 keys to values-zh-rCN (142 keys, 24.2 percent). Dropped composer_new_post (toot jargon), settings_colour_style_system (wrong meaning), post_action_remove_bookmark (clashes with file convention). Commit f26358b.
- Slice zh-TW — added 37 keys to values-zh-rTW (142 keys, 24.2 percent). Dropped notifications_settings_quotes and composer_new_post (toot jargon), settings_colour_style_system (wrong meaning), notification_activity_follow (clashes with file convention). Stripped a newline artifact from the drafts reference. Commit 850034c.
- Slice de-DE — added 64 keys to values-de (137 keys, 23.3 percent). Dropped notification_activity_follow (sentence fragment) and post_share_clip_label (verb with wrong meaning). Commit ef1f111.
- Slice fr-FR — added 60 keys to values-fr (133 keys, 21.5 percent). Dropped post_share_clip_label (verb with wrong meaning), dialog_discard (wrong destructive meaning), notifications_dismiss and settings_error_dismiss (wrong reject meaning). Commit 8f456a4.
- Slice es-ES — added 17 keys to values-es-rES (167 keys, 27.8 percent). Dropped setup_welcome_back (malformed inclusive form in reference) and post_action_remove_bookmark (clashes with file convention). Never touched values-b+es+419. Commit e141818.
- Slice pt-BR — added 15 keys to values-pt-rBR (139 keys, 23.8 percent, mastodon-android reference only). Dropped settings_error_dismiss (wrong fire-from-job meaning). Commit b250340.
- Slice pt-PT — added 25 keys to values-pt-rPT (149 keys, 25.4 percent). Dropped post_share_clip_label and single_post_title (verb with wrong meaning), photo_grid_add_hashtag and post_share_unmute and notification_activity_follow (clash with file convention). Commits next.

# Current slice

Done. ru-RU and in-ID stay blocked (no Beeline locale files; new files need code changes).

# Files involved

- app/src/main/res/values-pt-rPT/strings.xml

# Verification

- None yet.

# Next

Build the reference-match pipeline and run the zh-CN slice.

# Blockers

- ru-RU and in-ID have no Beeline locale files. New locale files need AppLanguage, locale-config, and test-mapping code changes, which are out of scope.
- Gradle LocalizationResourceTest may stay blocked by the locked app build R.jar.

# Last safe commit

b759a8c Extend Japanese strings from reference applications.
