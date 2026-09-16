package me.foxtails.palustris.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.foxtails.palustris.ui.UiStrings

@Module
@InstallIn(SingletonComponent::class)
object UiStringsModule {
    @Provides
    fun provideUiStrings(@ApplicationContext context: Context): UiStrings = UiStrings.from(context)
}
