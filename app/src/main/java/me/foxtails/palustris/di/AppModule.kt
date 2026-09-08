package me.foxtails.palustris.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.AppRegistrationCache
import me.foxtails.palustris.data.auth.AuthGateway
import me.foxtails.palustris.data.auth.DetectingAuthGateway
import me.foxtails.palustris.data.auth.EncryptedSessionStore
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.auth.EncryptedDraftStore
import me.foxtails.palustris.data.auth.MastodonAuth
import me.foxtails.palustris.data.auth.MisskeyAuth
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.misskey.HttpClientPool
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationStore
import me.foxtails.palustris.data.notifications.NotificationSyncController
import me.foxtails.palustris.data.notifications.NotificationSyncOrchestrator
import me.foxtails.palustris.data.notifications.RoomNotificationStore
import me.foxtails.palustris.data.notifications.db.NotificationDatabase
import me.foxtails.palustris.data.notifications.db.NOTIFICATION_MIGRATIONS
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import org.json.JSONObject

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClientPool(): HttpClientPool = HttpClientPool()

    @Provides
    @Singleton
    fun provideAppRegistrationCache(): AppRegistrationCache = AppRegistrationCache()

    @Provides
    @Singleton
    fun provideMisskeyAuth(clientPool: HttpClientPool): MisskeyAuth = MisskeyAuth(clientPool)

    @Provides
    @Singleton
    fun provideMastodonAuth(clientPool: HttpClientPool, cache: AppRegistrationCache): MastodonAuth =
        MastodonAuth(clientPool, cache)

    @Provides
    @Singleton
    fun provideAuthGateway(
        misskey: MisskeyAuth,
        mastodon: MastodonAuth,
        clientPool: HttpClientPool,
    ): AuthGateway = DetectingAuthGateway(misskey, mastodon) { origin ->
        val probe = MisskeyApi(clientPool.clientFor(Connection(origin, Protocol.MISSKEY)))
        JSONObject(probe.post(origin, "meta").body).optString("version").isNotBlank()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SessionStore = EncryptedSessionStore(context)

    @Provides
    @Singleton
    fun provideDraftStore(@ApplicationContext context: Context): DraftStore = EncryptedDraftStore(context)

    @Provides
    @Singleton
    fun provideNotificationDatabase(@ApplicationContext context: Context): NotificationDatabase =
        Room.databaseBuilder(context, NotificationDatabase::class.java, "notifications.db")
            .addMigrations(*NOTIFICATION_MIGRATIONS)
            .build()

    @Provides
    @Singleton
    fun provideRoomNotificationStore(
        database: NotificationDatabase,
        importer: me.foxtails.palustris.data.notifications.LegacyNotificationFileImporter,
    ): RoomNotificationStore = RoomNotificationStore(database, importer)

    @Provides
    @Singleton
    fun provideNotificationStore(store: RoomNotificationStore): NotificationStore = store
}

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {
    @Provides
    @Singleton
    fun provideSocialSourceFactory(clientPool: HttpClientPool): SocialSourceFactory = SocialSourceFactory(clientPool)
}

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun provideAccountNotificationSyncController(
        coordinator: NotificationSyncOrchestrator,
    ): NotificationSyncController = coordinator

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
