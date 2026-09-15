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

- Slice zh-CN — added 44 keys to values-zh-rCN (142 keys, 24.2 percent). Dropped composer_new_post (toot jargon), settings_colour_style_system (wrong meaning), post_action_remove_bookmark (clashes with file convention). Commit b759a8c was the prior slice; this slice commits next.

# Current slice

Slice zh-TW: app/src/main/res/values-zh-rTW/strings.xml.

# Files involved

- app/src/main/res/values/strings.xml (read-only source)
- app/src/main/res/values-zh-rTW/strings.xml

# Verification

- None yet.

# Next

Build the reference-match pipeline and run the zh-CN slice.

# Blockers

- ru-RU and in-ID have no Beeline locale files. New locale files need AppLanguage, locale-config, and test-mapping code changes, which are out of scope.
- Gradle LocalizationResourceTest may stay blocked by the locked app build R.jar.

# Last safe commit

b759a8c Extend Japanese strings from reference applications.
