package com.rebuildit.prestaflow.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.WorkManager
import com.rebuildit.prestaflow.BuildConfig
import com.rebuildit.prestaflow.core.config.AppEnvironment
import com.rebuildit.prestaflow.core.network.ApiEndpointManager
import com.rebuildit.prestaflow.core.security.EncryptedTokenStorage
import com.rebuildit.prestaflow.core.security.InMemoryTokenProvider
import com.rebuildit.prestaflow.core.security.TokenStorage
import com.rebuildit.prestaflow.data.auth.AuthRepositoryImpl
import com.rebuildit.prestaflow.data.clients.ClientsRepositoryImpl
import com.rebuildit.prestaflow.data.dashboard.DashboardRepositoryImpl
import com.rebuildit.prestaflow.data.local.dao.ClientDao
import com.rebuildit.prestaflow.data.local.dao.DashboardDao
import com.rebuildit.prestaflow.data.local.dao.OrderDao
import com.rebuildit.prestaflow.data.local.dao.PendingSyncDao
import com.rebuildit.prestaflow.data.local.dao.ProductDao
import com.rebuildit.prestaflow.data.local.dao.StockAvailabilityDao
import com.rebuildit.prestaflow.data.local.db.PrestaFlowDatabase
import com.rebuildit.prestaflow.data.orders.OrdersRepositoryImpl
import com.rebuildit.prestaflow.data.products.ProductsRepositoryImpl
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.interceptor.AuthInterceptor
import com.rebuildit.prestaflow.data.remote.interceptor.DefaultHeadersInterceptor
import com.rebuildit.prestaflow.data.remote.interceptor.DynamicBaseUrlInterceptor
import com.rebuildit.prestaflow.data.sync.SyncQueueRepositoryImpl
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.ShopUrlValidator
import com.rebuildit.prestaflow.domain.clients.ClientsRepository
import com.rebuildit.prestaflow.domain.dashboard.DashboardRepository
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.sync.SyncQueueRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
    @OptIn(ExperimentalSerializationApi::class)
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
    fun provideDefaultHeadersInterceptor(): DefaultHeadersInterceptor = DefaultHeadersInterceptor()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        authInterceptor: AuthInterceptor,
        defaultHeadersInterceptor: DefaultHeadersInterceptor
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(defaultHeadersInterceptor)
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
    fun provideRetrofit(
        json: Json,
        client: OkHttpClient,
        endpointManager: ApiEndpointManager
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(endpointManager.getActiveBaseUrl())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun providePrestaFlowApi(retrofit: Retrofit): PrestaFlowApi =
        retrofit.create(PrestaFlowApi::class.java)

    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "prestaflow_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Provides
    @Singleton
    fun provideTokenStorage(storage: EncryptedTokenStorage): TokenStorage = storage

    @Provides
    @Singleton
    fun provideShopUrlValidator(): ShopUrlValidator = ShopUrlValidator()

    @Provides
    @Singleton
    fun provideAuthRepository(impl: AuthRepositoryImpl): AuthRepository = impl

    @Provides
    @Singleton
    fun provideOrdersRepository(impl: OrdersRepositoryImpl): OrdersRepository = impl

    @Provides
    @Singleton
    fun provideProductsRepository(impl: ProductsRepositoryImpl): ProductsRepository = impl

    @Provides
    @Singleton
    fun provideDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository = impl

    @Provides
    @Singleton
    fun provideClientsRepository(impl: ClientsRepositoryImpl): ClientsRepository = impl

    @Provides
    @Singleton
    fun provideSyncQueueRepository(impl: SyncQueueRepositoryImpl): SyncQueueRepository = impl

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): PrestaFlowDatabase = Room.databaseBuilder(
        context,
        PrestaFlowDatabase::class.java,
        "prestaflow.db"
    ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideOrderDao(database: PrestaFlowDatabase): OrderDao = database.orderDao()

    @Provides
    fun provideProductDao(database: PrestaFlowDatabase): ProductDao = database.productDao()

    @Provides
    fun provideDashboardDao(database: PrestaFlowDatabase): DashboardDao = database.dashboardDao()

    @Provides
    fun providePendingSyncDao(database: PrestaFlowDatabase): PendingSyncDao = database.pendingSyncDao()

    @Provides
    fun provideStockAvailabilityDao(database: PrestaFlowDatabase): StockAvailabilityDao =
        database.stockAvailabilityDao()

    @Provides
    fun provideClientDao(database: PrestaFlowDatabase): ClientDao = database.clientDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
