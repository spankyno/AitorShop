package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class SupabaseShoppingItemDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "quantity") val quantity: Double,
    @Json(name = "unit") val unit: String,
    @Json(name = "price") val price: Double,
    @Json(name = "category") val category: String,
    @Json(name = "isPurchased") val isPurchased: Boolean,
    @Json(name = "listId") val listId: String,
    @Json(name = "createdAt") val createdAt: Long,
    @Json(name = "isDeleted") val isDeleted: Boolean
)

interface SupabaseApi {
    @GET("rest/v1/shopping_items")
    suspend fun getItems(
        @Query("listId") listIdFilter: String, // format: "eq.LIST_CODE"
        @Query("isDeleted") deletedFilter: String = "eq.false",
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String
    ): List<SupabaseShoppingItemDto>

    @POST("rest/v1/shopping_items")
    @Headers("Prefer: resolution=merge-duplicates")
    suspend fun upsertItems(
        @Body items: List<SupabaseShoppingItemDto>,
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String
    ): Response<Unit>

    @PATCH("rest/v1/shopping_items")
    suspend fun updateItem(
        @Query("id") idFilter: String, // format: "eq.UUID"
        @Body fields: Map<String, @JvmSuppressWildcards Any>,
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String
    ): Response<Unit>

    @DELETE("rest/v1/shopping_items")
    suspend fun deleteItem(
        @Query("id") idFilter: String, // format: "eq.UUID"
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String
    ): Response<Unit>
}

object SupabaseClient {

    private var customAuthenticator: okhttp3.Authenticator? = null

    fun setAuthenticator(authenticator: okhttp3.Authenticator) {
        customAuthenticator = authenticator
    }

    internal val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .authenticator { route, response ->
            customAuthenticator?.authenticate(route, response)
        }
        .build()

    // Dynamically retrieve URL to prevent crash with invalid URLs during fallback
    fun getApi(): SupabaseApi? {
        val baseUrl = BuildConfig.SUPABASE_URL
        if (baseUrl.isBlank() || baseUrl.contains("your-project") || baseUrl.contains("dummy") || !baseUrl.startsWith("http")) {
            return null
        }
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return try {
            Retrofit.Builder()
                .baseUrl(formattedUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(SupabaseApi::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getAuthApi(): SupabaseAuthApi? {
        val baseUrl = BuildConfig.SUPABASE_URL
        if (baseUrl.isBlank() || baseUrl.contains("your-project") || baseUrl.contains("dummy") || !baseUrl.startsWith("http")) {
            return null
        }
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return try {
            Retrofit.Builder()
                .baseUrl(formattedUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(SupabaseAuthApi::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getApiKey(): String {
        val key = BuildConfig.SUPABASE_KEY
        if (key.isBlank() || key.contains("your-supabase") || key.contains("dummy")) {
            return ""
        }
        return key
    }
}

@JsonClass(generateAdapter = true)
data class AuthRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class IdTokenAuthRequest(
    @Json(name = "provider") val provider: String,
    @Json(name = "id_token") val idToken: String
)

@JsonClass(generateAdapter = true)
data class SupabaseUser(
    @Json(name = "id") val id: String,
    @Json(name = "email") val email: String?
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "refresh_token") val refreshToken: String?,
    @Json(name = "user") val user: SupabaseUser?
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

interface SupabaseAuthApi {
    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body request: AuthRequest,
        @Header("apikey") apiKey: String
    ): Response<AuthResponse>

    @POST("auth/v1/token")
    suspend fun signInWithPassword(
        @Query("grant_type") grantType: String = "password",
        @Body request: AuthRequest,
        @Header("apikey") apiKey: String
    ): Response<AuthResponse>

    @POST("auth/v1/token")
    suspend fun signInWithIdToken(
        @Query("grant_type") grantType: String = "id_token",
        @Body request: IdTokenAuthRequest,
        @Header("apikey") apiKey: String
    ): Response<AuthResponse>

    @POST("auth/v1/token")
    suspend fun refreshToken(
        @Query("grant_type") grantType: String = "refresh_token",
        @Body request: RefreshTokenRequest,
        @Header("apikey") apiKey: String
    ): Response<AuthResponse>
}
