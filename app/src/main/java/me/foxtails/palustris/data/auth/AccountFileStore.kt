package me.foxtails.palustris.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AccessGrant
import me.foxtails.palustris.domain.AccessScope
import me.foxtails.palustris.domain.AccessStatus
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.NotificationReadSemantics
import me.foxtails.palustris.domain.NotificationUnreadPrecision
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileCapabilities
import me.foxtails.palustris.domain.PrimaryFavouriteCapability
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.SavedPostsCapability
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.Timeline
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores one encrypted session file for each account. */
class AccountFileStore internal constructor(
    context: Context,
    private val suppliedKey: SecretKey?,
) {
    constructor(context: Context) : this(context, null)

    private val accountsDirectory = File(context.noBackupFilesDir, "accounts")

    fun read(accountId: AccountId): Session? {
        val file = fileFor(accountId)
        if (!file.exists()) return null
        val json = readJson(file)
        val storedConnection = Connection(json.getString("origin"), Protocol.valueOf(json.getString("protocol")))
        return Session(
            AccountId(storedConnection, json.getString("localId")),
            json.getString("token"),
            json.optJSONObject("capabilities")?.toCapabilities() ?: ServerCapabilities(),
            json.optJSONObject("access")?.toAccessGrant() ?: AccessGrant(),
            json.optString("pushInstanceName").takeIf { it.isNotBlank() },
        )
    }

    fun write(accountId: AccountId, session: Session, profile: JSONObject = JSONObject()) {
        require(accountId == session.accountId) { "Session account does not match the requested file." }
        writeJson(fileFor(accountId), JSONObject()
            .put("origin", accountId.connection.origin)
            .put("protocol", accountId.connection.protocol.name)
            .put("localId", accountId.localId)
            .put("token", session.token)
            .put("capabilities", session.capabilities.toJson())
            .put("access", session.access.toJson())
            .put("pushInstanceName", session.pushInstanceName)
            .put("profile", JSONObject(profile.toString())))
    }

    fun writePushInstance(accountId: AccountId, instanceName: String) {
        val file = fileFor(accountId)
        val json = readJson(file)
        json.put("pushInstanceName", instanceName)
        writeJson(file, json)
    }

    fun writeProfile(accountId: AccountId, profile: JSONObject) {
        val file = fileFor(accountId)
        val json = readJson(file)
        json.put("profile", JSONObject(profile.toString()))
        writeJson(file, json)
    }

    fun delete(accountId: AccountId) {
        AtomicFile(fileFor(accountId)).delete()
    }

    fun clear() {
        accountsDirectory.deleteRecursively()
    }

    internal fun readJson(file: File): JSONObject {
        val bytes = AtomicFile(file).readFully()
        require(bytes.size > 28) { "Encrypted session file is invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }

    internal fun writeJson(file: File, json: JSONObject) {
        file.parentFile?.mkdirs()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        java.nio.file.Files.move(
            temporary.toPath(),
            file.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
        File("${file.path}.bak").delete()
    }

    private fun fileFor(accountId: AccountId): File {
        val value = "${accountId.connection.origin}\u0000${accountId.localId}"
        val filename = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
        return File(accountsDirectory, "$filename.enc")
    }

    private fun key(): SecretKey {
        suppliedKey?.let { return it }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("palustris.session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("palustris.session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }
}

private fun ServerCapabilities.toJson(): JSONObject = JSONObject()
    .put("timelines", JSONArray(timelines.map { it.name }))
    .put("audiences", JSONArray(audiences.map { it.name }))
    .put("actions", JSONArray(actions.map { it.name }))
    .put("maxPostLength", maxPostLength)
    .put("canPublish", canPublish)
    .put("notifications", notifications.toJson())
    .put("profile", profile.toJson())
    .put("quotes", quotes.name)
    .put("primaryFavourite", JSONObject()
        .put("status", primaryFavourite.status.name)
        .put("mode", primaryFavourite.mode.name))
    .put("savedPosts", savedPosts?.let {
        JSONObject().put("status", it.status.name).put("kind", it.kind.name)
    })
    .put("capabilitiesLastUpdated", capabilitiesLastUpdated)

private fun JSONObject.toCapabilities(): ServerCapabilities = ServerCapabilities(
    timelines = enumSet<Timeline>("timelines"),
    audiences = enumSet<Audience>("audiences"),
    actions = enumSet<PostAction>("actions"),
    maxPostLength = if (isNull("maxPostLength")) null else optInt("maxPostLength"),
    canPublish = optBoolean("canPublish"),
    notifications = optJSONObject("notifications")?.toNotificationCapabilities() ?: NotificationCapabilities(),
    profile = optJSONObject("profile")?.toProfileCapabilities() ?: ProfileCapabilities(),
    quotes = enumOrDefault("quotes", CapabilityStatus.Unknown),
    primaryFavourite = optJSONObject("primaryFavourite")?.let {
        PrimaryFavouriteCapability(
            status = it.enumOrDefault("status", CapabilityStatus.Unknown),
            mode = it.enumOrDefault("mode", PrimaryFavouriteMode.Unavailable),
        )
    } ?: PrimaryFavouriteCapability(),
    savedPosts = optJSONObject("savedPosts")?.let {
        SavedPostsCapability(
            status = it.enumOrDefault("status", CapabilityStatus.Unknown),
            kind = it.enumOrDefault("kind", SavedPostsKind.Bookmarks),
        )
    },
    capabilitiesLastUpdated = optLong("capabilitiesLastUpdated"),
)

private fun ProfileCapabilities.toJson(): JSONObject = JSONObject()
    .put("details", details.name)
    .put("timelines", timelines.name)
    .put("relationships", relationships.name)
    .put("followActions", followActions.name)
    .put("pinnedPosts", pinnedPosts.name)

private fun JSONObject.toProfileCapabilities(): ProfileCapabilities = ProfileCapabilities(
    details = enumOrDefault("details", CapabilityStatus.Unknown),
    timelines = enumOrDefault("timelines", CapabilityStatus.Unknown),
    relationships = enumOrDefault("relationships", CapabilityStatus.Unknown),
    followActions = enumOrDefault("followActions", CapabilityStatus.Unknown),
    pinnedPosts = enumOrDefault("pinnedPosts", CapabilityStatus.Unknown),
)

private fun NotificationCapabilities.toJson(): JSONObject = JSONObject()
    .put("listing", listing.name)
    .put("supportedCategories", JSONArray(supportedCategories.map { it.name }))
    .put("readSemantics", readSemantics.name)
    .put("unreadCountPrecision", unreadCountPrecision.name)
    .put("grouping", grouping.name)
    .put("dismissal", dismissal.name)
    .put("policyManagement", policyManagement.name)
    .put("followRequestActions", followRequestActions.name)
    .put("streaming", streaming.name)
    .put("webPush", webPush.name)

private fun JSONObject.toNotificationCapabilities(): NotificationCapabilities = NotificationCapabilities(
    listing = enumOrDefault("listing", CapabilityStatus.Unknown),
    supportedCategories = enumSet("supportedCategories"),
    readSemantics = enumOrDefault("readSemantics", NotificationReadSemantics.Unknown),
    unreadCountPrecision = enumOrDefault("unreadCountPrecision", NotificationUnreadPrecision.Unknown),
    grouping = enumOrDefault("grouping", CapabilityStatus.Unknown),
    dismissal = enumOrDefault("dismissal", CapabilityStatus.Unknown),
    policyManagement = enumOrDefault("policyManagement", CapabilityStatus.Unknown),
    followRequestActions = enumOrDefault("followRequestActions", CapabilityStatus.Unknown),
    streaming = enumOrDefault("streaming", CapabilityStatus.Unknown),
    webPush = enumOrDefault("webPush", CapabilityStatus.Unknown),
)

private fun AccessGrant.toJson(): JSONObject = JSONObject()
    .put("requested", JSONArray(requested.map { it.name }))
    .put("known", JSONArray(known.map { (scope, status) ->
        JSONObject().put("scope", scope.name).put("status", status.name)
    }))

private fun JSONObject.toAccessGrant(): AccessGrant {
    val known = mutableMapOf<AccessScope, AccessStatus>()
    optJSONArray("known")?.let { entries ->
        for (index in 0 until entries.length()) {
            entries.optJSONObject(index)?.let { entry ->
                runCatching {
                    known[AccessScope.valueOf(entry.getString("scope"))] = AccessStatus.valueOf(entry.getString("status"))
                }
            }
        }
    } ?: optJSONObject("known")?.keys()?.let { keys ->
        while (keys.hasNext()) {
            val key = keys.next()
            runCatching { known[AccessScope.valueOf(key)] = AccessStatus.valueOf(getString(key)) }
        }
    }
    return AccessGrant(requested = enumSet("requested"), known = known)
}

private inline fun <reified T : Enum<T>> JSONObject.enumSet(key: String): Set<T> {
    val values = mutableSetOf<T>()
    val names = optJSONArray(key) ?: return values
    for (index in 0 until names.length()) {
        runCatching { values += enumValueOf<T>(names.getString(index)) }
    }
    return values
}

private inline fun <reified T : Enum<T>> JSONObject.enumOrDefault(key: String, default: T): T =
    runCatching { enumValueOf<T>(optString(key)) }.getOrDefault(default)
