package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfileCapabilities
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.SourceError
import org.json.JSONObject

/** Self-profile reads and updates for one authenticated Mastodon session. */
class MastodonSelfProfileService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val authenticatedAccountId: AccountId,
) {
    suspend fun load(capabilities: EditableProfileCapabilities): EditableProfile {
        if (authenticatedAccountId.connection.origin != origin) {
            throw SourceError.Unsupported("profile.editable.load")
        }
        val body = if (capabilities.read == CapabilityStatus.Supported) {
            api.get(origin, "v1/profile", token).body
        } else {
            api.get(origin, "v1/accounts/verify_credentials", token).body
        }
        val json = JSONObject(body)
        requireMatchingAccount(json)
        return if (capabilities.read == CapabilityStatus.Supported) {
            MastodonMapper.editableProfile(json, origin)
        } else {
            MastodonMapper.legacyEditableProfile(json, origin)
        }
    }

    suspend fun update(
        patch: EditableProfilePatch,
        capabilities: EditableProfileCapabilities,
    ): EditableProfile {
        validatePatch(patch, capabilities)
        val fields = buildList {
            patch.displayName?.let { add("display_name" to it) }
            patch.biography?.let { add("note" to it) }
            patch.fields?.forEachIndexed { index, field ->
                add("fields_attributes[$index][name]" to field.name)
                add("fields_attributes[$index][value]" to field.value)
            }
            patch.avatarDescription?.let { add("avatar_description" to it) }
            patch.headerDescription?.let { add("header_description" to it) }
            patch.locked?.let { add("locked" to it.toString()) }
            patch.bot?.let { add("bot" to it.toString()) }
            patch.hideCollections?.let { add("hide_collections" to it.toString()) }
            patch.discoverable?.let { add("discoverable" to it.toString()) }
            patch.indexable?.let { add("indexable" to it.toString()) }
            patch.showMedia?.let { add("show_media" to it.toString()) }
            patch.showMediaReplies?.let { add("show_media_replies" to it.toString()) }
            patch.showFeatured?.let { add("show_featured" to it.toString()) }
            patch.attributionDomains?.forEach { domain -> add("attribution_domains[]" to domain) }
        }
        val response = if (capabilities.update == CapabilityStatus.Supported) {
            api.patchForm(origin, "api/v1/profile", fields, token)
        } else {
            api.patchForm(origin, "api/v1/accounts/update_credentials", fields, token)
        }
        val json = JSONObject(response.body)
        requireMatchingAccount(json)
        return if (capabilities.update == CapabilityStatus.Supported) {
            MastodonMapper.editableProfile(json, origin)
        } else {
            MastodonMapper.legacyEditableProfile(json, origin)
        }
    }

    private fun requireMatchingAccount(json: JSONObject) {
        val id = json.optString("id").takeIf(String::isNotBlank)
            ?: throw SourceError.AccountMismatch
        if (id != authenticatedAccountId.localId) throw SourceError.AccountMismatch
    }

    private fun validatePatch(patch: EditableProfilePatch, capabilities: EditableProfileCapabilities) {
        val advanced = listOfNotNull(
            patch.fields, patch.locked, patch.bot, patch.hideCollections, patch.discoverable,
            patch.indexable, patch.showMedia, patch.showMediaReplies, patch.showFeatured,
            patch.attributionDomains,
        )
        if (advanced.isNotEmpty()) {
            requireCapability(capabilities.advancedSettings, "profile.editable.advanced")
        }
        if (patch.avatarDescription != null || patch.headerDescription != null) {
            requireCapability(capabilities.imageDescriptions, "profile.editable.imageDescriptions")
        }
    }

    private fun requireCapability(status: CapabilityStatus, feature: String) {
        if (status != CapabilityStatus.Supported) throw SourceError.Unsupported(feature)
    }
}
