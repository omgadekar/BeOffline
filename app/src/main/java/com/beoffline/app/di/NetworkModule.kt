package com.beoffline.app.di

import com.beoffline.app.BuildConfig
import com.beoffline.app.accountability.ApiService
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(FirebaseAuthInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideApiService(client: OkHttpClient): ApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.ACCOUNTABILITY_API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}

/**
 * Attaches the Firebase ID token as a Bearer header. getIdToken(false) serves
 * from cache and only refreshes when expired, so the runBlocking hop is cheap
 * on OkHttp's worker thread. No signed-in user → request goes out without a
 * token and the server answers 401.
 */
private class FirebaseAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return chain.proceed(chain.request())
        val token = try {
            runBlocking { user.getIdToken(false).await().token }
        } catch (_: Exception) {
            null
        }
        val request = if (token != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
