package me.foxtails.palustris.di

import android.content.Context
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
import me.foxtails.palustris.data.auth.EncryptedSessionStore
import me.foxtails.palustris.data.auth.MastodonAuth
import me.foxtails.palustris.data.auth.MisskeyAuth
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.misskey.HttpClientPool
import me.foxtails.palustris.ui.AccountSyncCoordinator

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
    fun provideAuthGateway(auth: MisskeyAuth): AuthGateway = auth
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SessionStore = EncryptedSessionStore(context)
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
    fun provideAccountSyncCoordinator(): AccountSyncCoordinator = AccountSyncCoordinator()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
