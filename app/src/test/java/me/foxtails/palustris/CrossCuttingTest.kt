package me.foxtails.palustris

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.auth.AccountFileStore
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.data.auth.AppRegistration
import me.foxtails.palustris.data.auth.AppRegistrationCache
import me.foxtails.palustris.data.auth.EncryptedSessionStore
import me.foxtails.palustris.data.auth.PendingLogin
import me.foxtails.palustris.data.auth.toAccount
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.misskey.HttpClientPool
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccessGrant
import me.foxtails.palustris.domain.AccessScope
import me.foxtails.palustris.domain.AccessStatus
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.ProfileCapabilities
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.requiresSignIn
import me.foxtails.palustris.ui.sourceErrorMessage
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrossCuttingTest {
    @Test
    fun accountsOnOneOriginShareHttpClientAndRegistrationCache() = runBlocking {
        val origin = "https://example.org"
        val pool = HttpClientPool()
        val connection = Connection(origin, Protocol.MASTODON)
        assertSame(pool.clientFor(connection), pool.clientFor(connection))

        val cache = AppRegistrationCache()
        var registrationsCreated = 0
        val first = cache.getOrPut(origin) {
            registrationsCreated++
            AppRegistration("client-id", "client-secret")
        }
        val second = cache.getOrPut(origin) {
            registrationsCreated++
            AppRegistration("unexpected", "unexpected")
        }
        assertSame(first, second)
        assertEquals(1, registrationsCreated)
        assertNotNull(cache.get(origin))
    }

    @Test
    fun legacySessionMigratesToPerAccountFileAndIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = SecretKeySpec(ByteArray(16) { 7 }, "AES")
        val store = EncryptedSessionStore(context, key)
        store.clear()
        val origin = "https://example.org"
        val user = JSONObject("""{"id":"legacy-user","username":"legacy","name":"Legacy"}""")
        AccountFileStore(context, key).writeJson(
            File(context.noBackupFilesDir, "session.enc"),
            JSONObject()
                .put("session", JSONObject().put("origin", origin).put("token", "legacy-token").put("user", user))
                .put("pending", JSONObject().put("origin", origin).put("id", "legacy-pending").put("createdAt", 1L)),
        )

        val accountId = AccountId(Connection(origin, Protocol.MISSKEY), "legacy-user")
        val migrated = EncryptedSessionStore(context, key)
        assertEquals("legacy-token", migrated.read(accountId)?.token)
        assertEquals(accountId, migrated.readIndex().activeAccountId)
        assertEquals("legacy-pending", migrated.readPending()?.id)
        assertEquals(AccessStatus.Unknown, migrated.read(accountId)?.access?.status(AccessScope.Push))
        val upgrade = PendingLogin(
            origin = origin,
            id = "upgrade-pending",
            createdAt = System.currentTimeMillis(),
            requestedAccess = setOf(AccessScope.NotificationsRead, AccessScope.Push),
            replacingAccountId = accountId,
        )
        migrated.writePending(upgrade)
        assertEquals(accountId, migrated.readPending()?.replacingAccountId)
        assertEquals(upgrade.requestedAccess, migrated.readPending()?.requestedAccess)
        assertFalse(File(context.noBackupFilesDir, "session.enc").exists())
    }

    @Test
    fun notificationCapabilitiesAndAccessSurviveEncryptedSessionRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = SecretKeySpec(ByteArray(16) { 8 }, "AES")
        val store = AccountFileStore(context, key)
        store.clear()
        val accountId = AccountId(Connection("https://capabilities.example", Protocol.MASTODON), "account")
        val access = AccessGrant(
            requested = setOf(AccessScope.NotificationsRead, AccessScope.Push),
            known = mapOf(
                AccessScope.NotificationsRead to AccessStatus.Granted,
                AccessScope.Push to AccessStatus.Unknown,
            ),
        )
        val capabilities = ServerCapabilities(
            profile = ProfileCapabilities(
                details = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                timelines = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                relationships = me.foxtails.palustris.domain.CapabilityStatus.Unknown,
                followActions = me.foxtails.palustris.domain.CapabilityStatus.Denied,
                pinnedPosts = me.foxtails.palustris.domain.CapabilityStatus.Unsupported,
            ),
            notifications = me.foxtails.palustris.domain.NotificationCapabilities(
                listing = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                supportedCategories = setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions),
                readSemantics = me.foxtails.palustris.domain.NotificationReadSemantics.TimelineMarker,
                unreadCountPrecision = me.foxtails.palustris.domain.NotificationUnreadPrecision.Exact,
            ),
        )
        val session = Session(accountId, "session-token", capabilities, access)

        store.write(accountId, session)
        val restored = store.read(accountId) ?: error("Session was not restored")

        assertEquals(session.capabilities, restored.capabilities)
        assertEquals(session.access, restored.access)
    }

    @Test
    fun richSelfProfileMetadataSurvivesAccountIndexRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = SecretKeySpec(ByteArray(16) { 9 }, "AES")
        val store = EncryptedSessionStore(context, key)
        store.clear()
        val account = Account(
            id = AccountId(Connection("https://profile.example", Protocol.MASTODON), "self"),
            displayName = "Self",
            handle = "@self@profile.example",
            avatarUrl = "https://profile.example/avatar.png",
            biography = "Biography",
            profileFields = listOf(ProfileField("Site", "https://profile.example")),
            bannerUrl = "https://profile.example/banner.png",
            followersCount = 12,
            followingCount = 8,
            postsCount = 44,
            locked = true,
            bot = false,
        )

        store.writeIndex(AccountIndex(accounts = listOf(
            AccountRef(
                accountId = account.id,
                handle = account.handle,
                avatarUrl = account.avatarUrl,
                displayName = account.displayName,
                biography = account.biography,
                profileFields = account.profileFields,
                bannerUrl = account.bannerUrl,
                followersCount = account.followersCount,
                followingCount = account.followingCount,
                postsCount = account.postsCount,
                locked = account.locked,
                bot = account.bot,
            ),
        ), activeAccountId = account.id))

        assertEquals(account, store.readIndex().accounts.single().toAccount())
    }

    @Test
    fun appRegistrationCacheRejectsRegistrationMissingRequiredPushScope() = runBlocking {
        val cache = AppRegistrationCache()
        val origin = "https://mastodon.example"
        cache.put(origin, AppRegistration("client", "secret", setOf("read", "write"), scopesKnown = true))

        assertEquals(null, cache.get(origin, setOf("read", "write", "push")))
        assertEquals("client", cache.get(origin, setOf("read", "write"))?.clientId)
    }
    @Test
    fun sameOriginAccountsReceiveIndependentCapabilities() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val cache = CapabilityCache()
            val adminId = AccountId(Connection(origin, Protocol.MISSKEY), "admin")
            val regularId = AccountId(Connection(origin, Protocol.MISSKEY), "regular")
            val admin = source(origin, adminId, cache, setOf(Timeline.Home, Timeline.Local))
            val regular = source(origin, regularId, cache, setOf(Timeline.Home))
            enqueueTimeline(server, "admin-post")
            enqueueTimeline(server, "regular-post")

            admin.timeline(Timeline.Home)
            regular.timeline(Timeline.Home)

            assertTrue(Timeline.Local in admin.capabilities.timelines)
            assertTrue(Timeline.Local !in regular.capabilities.timelines)
        }
    }

    @Test
    fun everySourceErrorHasHumanReadableUiMessage() {
        val errors = listOf(
            me.foxtails.palustris.domain.SourceError.Unauthorized,
            me.foxtails.palustris.domain.SourceError.RateLimited,
            me.foxtails.palustris.domain.SourceError.Unsupported("search"),
            me.foxtails.palustris.domain.SourceError.NetworkUnavailable,
            me.foxtails.palustris.domain.SourceError.ServerError(null),
        )
        errors.forEach { assertTrue(sourceErrorMessage(it).isNotBlank()) }
        assertTrue(requiresSignIn(me.foxtails.palustris.domain.SourceError.Unauthorized))
    }

    @Test
    fun mastodonDeepLinkResolvesToMainActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("palustris://auth/mastodon?code=code&state=state"))
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val resolved = context.packageManager.resolveActivity(intent, 0)
        assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
    }

    private fun source(
        origin: String,
        accountId: AccountId,
        cache: CapabilityCache,
        timelines: Set<Timeline>,
    ): MisskeySource = MisskeySource(
        origin = origin,
        token = accountId.localId,
        api = MisskeyApi(),
        accountId = accountId,
        capabilityProbe = object : CapabilityProbe {
            override suspend fun probeCapabilities(connection: Connection) =
                ServerCapabilities(timelines = timelines, capabilitiesLastUpdated = System.currentTimeMillis())
        },
        capabilityCache = cache,
    )

    private fun enqueueTimeline(server: MockWebServer, id: String) {
        server.enqueue(MockResponse().setBody("""[{"id":"$id","createdAt":"2026-09-06T10:00:00Z","user":{"id":"u","username":"u","name":"User"},"text":"Text","visibility":"home"}]"""))
    }
}
