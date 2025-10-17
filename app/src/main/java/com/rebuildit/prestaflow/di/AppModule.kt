package com.rebuildit.prestaflow.di

import com.rebuildit.prestaflow.core.config.AppEnvironment
import com.rebuildit.prestaflow.BuildConfig
import com.rebuildit.prestaflow.core.security.InMemoryTokenProvider
import com.rebuildit.prestaflow.data.remote.interceptor.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import java.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEnvironment(): AppEnvironment = AppEnvironment.fromBuildConfig()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
        prettyPrint = BuildConfig.DEBUG
    }

    @Provides
    @Singleton
    fun provideTokenProvider(): InMemoryTokenProvider = InMemoryTokenProvider()

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenProvider: InMemoryTokenProvider): AuthInterceptor =
        AuthInterceptor(tokenProvider)

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    val logging = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                    addInterceptor(logging)
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(json: Json, client: OkHttpClient, environment: AppEnvironment): Retrofit =
        Retrofit.Builder()
            .baseUrl(environment.apiBaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
