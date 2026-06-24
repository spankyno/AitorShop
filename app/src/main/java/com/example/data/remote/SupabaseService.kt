package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
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
    /** Timestamp de última modificación (epoch ms). Usado como vector de
     *  versión LWW: el registro con mayor updatedAt gana en conflicto. */
    @Json(name = "updatedAt") val updatedAt: Long,
    @Json(name = "isDeleted") val isDeleted: Boolean
)

// ─────────────────────────────────────────────────────────────────────────────
// MEJORA 1 — API key retirada de cada @Header individual y centralizada en el
// interceptor ApiKeyInterceptor. Así nunca viaja como parámetro en la llamada
// (ni en la firma del método ni en logcat si el logging filtrado está activo).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Interceptor que inyecta la API key de Supabase en TODAS las peticiones
 * salientes. Al hacerlo aquí, la clave nunca aparece en la firma de la
 * interfaz Retrofit ni en los argumentos de cada llamada, lo que impide
 * que se filtre por logging accidental o por inspección del call-stack.
 */
internal class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request().newBuilder()
            .header("apikey", apiKey)
            // Content-Type por defecto para peticiones con cuerpo JSON
            .header("Content-Type", "application/json")
            .build()
        return chain.proceed(request)
    }
}

/**
 * Interceptor de logging que NUNCA vuelca las cabeceras en DEBUG.
 * Solo registra la línea de petición y el código de respuesta, evitando
 * que el token Bearer o la apikey aparezcan en logcat.
 */
private fun buildSafeLoggingInterceptor(): HttpLoggingInterceptor =
    HttpLoggingInterceptor().apply {
        // Level.BASIC: solo método + URL + código HTTP, sin cabeceras ni cuerpo
        level = if (BuildConfig.DEBUG)
            HttpLoggingInterceptor.Level.BASIC
        else
            HttpLoggingInterceptor.Level.NONE
    }

// ─────────────────────────────────────────────────────────────────────────────
// La interfaz ya NO recibe @Header("apikey") ni @Header("Authorization") como
// parámetros. Los headers se inyectan de forma centralizada por los
// interceptores registrados en el OkHttpClient.
// ─────────────────────────────────────────────────────────────────────────────
interface SupabaseApi {
    @GET("rest/v1/shopping_items")
    suspend fun getItems(
        @Query("listId") listIdFilter: String,
        @Query("isDeleted") deletedFilter: String = "eq.false"
    ): List<SupabaseShoppingItemDto>

    @POST("rest/v1/shopping_items")
    @Headers("Prefer: resolution=merge-duplicates")
    suspend fun upsertItems(
        @Body items: List<SupabaseShoppingItemDto>
    ): Response<Unit>

    @PATCH("rest/v1/shopping_items")
    suspend fun updateItem(
        @Query("id") idFilter: String,
        @Body fields: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    @DELETE("rest/v1/shopping_items")
    suspend fun deleteItem(
        @Query("id") idFilter: String
    ): Response<Unit>
}

interface SupabaseAuthApi {
    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body request: AuthRequest
    ): Response<AuthResponse>

    @POST("auth/v1/token")
    suspend fun signInWithPassword(
        @Query("grant_type") grantType: String = "password",
        @Body request: AuthRequest
    ): Response<AuthResponse>

    @POST("auth/v1/token")
    suspend fun signInWithIdToken(
        @Query("grant_type") grantType: String = "id_token",
        @Body request: IdTokenAuthRequest
    ): Response<AuthResponse>

    @POST("auth/v1/token")
    suspend fun refreshToken(
        @Query("grant_type") grantType: String = "refresh_token",
        @Body request: RefreshTokenRequest
    ): Response<AuthResponse>
}

object SupabaseClient {

    private var customAuthenticator: okhttp3.Authenticator? = null

    fun setAuthenticator(authenticator: okhttp3.Authenticator) {
        customAuthenticator = authenticator
    }

    internal val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun buildOkHttpClient(apiKey: String): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            // API key inyectada de forma centralizada — NUNCA en los parámetros
            .addInterceptor(ApiKeyInterceptor(apiKey))
            // Logging seguro: solo BASIC en debug, sin volcar headers ni body
            .addInterceptor(buildSafeLoggingInterceptor())
            .authenticator { route, response ->
                customAuthenticator?.authenticate(route, response)
            }
            .build()
    }

    private fun resolveBaseUrl(): String? {
        val baseUrl = BuildConfig.SUPABASE_URL
        if (baseUrl.isBlank()
            || baseUrl.contains("your-project")
            || baseUrl.contains("dummy")
            || !baseUrl.startsWith("http")
        ) return null
        return if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    }

    fun getApi(): SupabaseApi? {
        val apiKey = getApiKey().takeIf { it.isNotBlank() } ?: return null
        val formattedUrl = resolveBaseUrl() ?: return null
        return try {
            Retrofit.Builder()
                .baseUrl(formattedUrl)
                .client(buildOkHttpClient(apiKey))
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(SupabaseApi::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getAuthApi(): SupabaseAuthApi? {
        val apiKey = getApiKey().takeIf { it.isNotBlank() } ?: return null
        val formattedUrl = resolveBaseUrl() ?: return null
        return try {
            Retrofit.Builder()
                .baseUrl(formattedUrl)
                .client(buildOkHttpClient(apiKey))
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(SupabaseAuthApi::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getApiKey(): String {
        val key = BuildConfig.SUPABASE_KEY
        return if (key.isBlank() || key.contains("your-supabase") || key.contains("dummy")) ""
        else key
    }
}

// ─── DTOs de autenticación ───────────────────────────────────────────────────

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
