package com.fieldtrack.app.di

import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.data.remote.AuthInterceptor
import com.fieldtrack.app.data.remote.TokenAuthenticator
import com.fieldtrack.app.util.ConfigValidator
import com.fieldtrack.app.data.remote.api.AuthApi
import com.fieldtrack.app.data.remote.api.ExpenseApi
import com.fieldtrack.app.data.remote.api.LocationApi
import com.fieldtrack.app.data.remote.api.ReportApi
import com.fieldtrack.app.data.remote.api.StorageApi
import com.fieldtrack.app.data.remote.api.UserApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

val networkModule = module {

    single {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    single { AuthInterceptor(get()) }

    single { TokenAuthenticator(get()) }

    single {
        OkHttpClient.Builder()
            .addInterceptor(get<AuthInterceptor>())
            .addInterceptor(get<HttpLoggingInterceptor>())
            .authenticator(get<TokenAuthenticator>())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // If SUPABASE_URL is missing, use a safe placeholder so the app doesn't crash on startup.
    // Actual API calls will fail gracefully with network errors instead of a fatal crash.
    single {
        val baseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://placeholder.invalid/" }
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Expose config validity so UI can check and show an error screen
    single<ConfigValidator> { ConfigValidator() }

    single { get<Retrofit>().create(AuthApi::class.java) }
    single { get<Retrofit>().create(UserApi::class.java) }
    single { get<Retrofit>().create(LocationApi::class.java) }
    single { get<Retrofit>().create(ReportApi::class.java) }
    single { get<Retrofit>().create(ExpenseApi::class.java) }
    single { get<Retrofit>().create(StorageApi::class.java) }
    single { get<Retrofit>().create(com.fieldtrack.app.data.remote.api.AttendanceApi::class.java) }
}
