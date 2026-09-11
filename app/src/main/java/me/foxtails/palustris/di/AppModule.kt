package me.foxtails.palustris.di

import android.content.Context
import androidx.room.Room
import java.io.File
import java.time.Clock
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
import me.foxtails.palustris.data.preferences.EncryptedPostPreferencesRepository
import me.foxtails.palustris.data.preferences.FileEmojiPickerPreferencesRepository
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.auth.EncryptedDraftStore
import me.foxtails.palustris.data.auth.MastodonAuth
import me.foxtails.palustris.data.auth.MisskeyAuth
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.misskey.HttpClientPool
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationStore
import me.foxtails.palustris.data.notifications.NotificationSyncController
import me.foxtails.palustris.data.notifications.NotificationSyncIntents
import me.foxtails.palustris.data.notifications.NotificationSyncOrchestrator
import me.foxtails.palustris.data.notifications.work.NotificationDeliveryScheduler
import me.foxtails.palustris.data.notifications.work.NotificationWorkScheduler
import me.foxtails.palustris.data.notifications.ForegroundNotificationStreamController
import me.foxtails.palustris.data.notifications.NotificationStreamController
import me.foxtails.palustris.data.notifications.AndroidNotificationPermissionController
import me.foxtails.palustris.data.notifications.AndroidNotificationPresenter
import me.foxtails.palustris.data.notifications.NotificationPermissionController
import me.foxtails.palustris.data.notifications.NotificationPresenter
import me.foxtails.palustris.data.notifications.RoomNotificationStore
import me.foxtails.palustris.data.notifications.push.PushRegistrationManager
import me.foxtails.palustris.data.notifications.push.AndroidUnifiedPushConnector
import me.foxtails.palustris.data.notifications.push.UnifiedPushConnector
import me.foxtails.palustris.data.notifications.push.UnifiedPushRegistrationManager
import me.foxtails.palustris.data.notifications.db.NotificationDatabase
import me.foxtails.palustris.data.notifications.db.NOTIFICATION_MIGRATIONS
import me.foxtails.palustris.data.directmessages.DirectMessageDatabase
import me.foxtails.palustris.data.directmessages.DirectMessageStore
import me.foxtails.palustris.data.directmessages.RoomDirectMessageStore
import me.foxtails.palustris.data.emoji.EmojiCacheDatabase
import me.foxtails.palustris.data.emoji.EmojiAssetStore
import me.foxtails.palustris.data.emoji.RoomEmojiCatalogRepository
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiCatalogRepository
import me.foxtails.palustris.domain.EmojiPickerPreferencesRepository
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PostPreferencesRepository
import org.json.JSONObject

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideMediaImageLoader(@ApplicationContext context: Context): MediaImageLoader =
        MediaImageLoader.get(context)

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
    fun provideEmojiCatalogClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideEmojiPickerPreferencesRepository(
        repository: FileEmojiPickerPreferencesRepository,
    ): EmojiPickerPreferencesRepository = repository

    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SessionStore = EncryptedSessionStore(context)

    @Provides
    @Singleton
    fun provideDraftStore(@ApplicationContext context: Context): DraftStore = EncryptedDraftStore(context)

    @Provides
    @Singleton
    fun providePostPreferencesRepository(
        @ApplicationContext context: Context,
    ): PostPreferencesRepository = EncryptedPostPreferencesRepository(context)

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

    @Provides
    @Singleton
    fun provideDirectMessageDatabase(@ApplicationContext context: Context): DirectMessageDatabase =
        Room.databaseBuilder(
            context,
            DirectMessageDatabase::class.java,
            File(context.noBackupFilesDir, "directmessages.db").absolutePath,
        ).build()

    @Provides
    @Singleton
    fun provideEmojiCacheDatabase(@ApplicationContext context: Context): EmojiCacheDatabase {
        return EmojiCacheDatabase.get(context)
    }

    @Provides
    @Singleton
    fun provideEmojiAssetStore(@ApplicationContext context: Context): EmojiAssetStore =
        EmojiAssetStore.get(context)

    @Provides
    @Singleton
    fun provideEmojiCatalogRepository(repository: RoomEmojiCatalogRepository): EmojiCatalogRepository = repository

    @Provides
    @Singleton
    fun provideDirectMessageStore(store: RoomDirectMessageStore): DirectMessageStore = store
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
    @Singleton
    fun provideNotificationSyncIntents(
        coordinator: NotificationSyncOrchestrator,
    ): NotificationSyncIntents = coordinator

    @Provides
    @Singleton
    fun provideNotificationStreamController(
        controller: ForegroundNotificationStreamController,
    ): NotificationStreamController = controller

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Module
@InstallIn(SingletonComponent::class)
object NotificationPresentationModule {
    @Provides
    @Singleton
    fun provideNotificationDeliveryScheduler(
        scheduler: NotificationWorkScheduler,
    ): NotificationDeliveryScheduler = scheduler

    @Provides
    @Singleton
    fun provideUnifiedPushConnector(
        @ApplicationContext context: Context,
    ): UnifiedPushConnector = AndroidUnifiedPushConnector(context)

    @Provides
    @Singleton
    fun provideNotificationPresenter(presenter: AndroidNotificationPresenter): NotificationPresenter = presenter

    @Provides
    @Singleton
    fun provideNotificationPermissionController(
        controller: AndroidNotificationPermissionController,
    ): NotificationPermissionController = controller

    @Provides
    @Singleton
    fun providePushRegistrationManager(
        manager: UnifiedPushRegistrationManager,
    ): PushRegistrationManager = manager
}
