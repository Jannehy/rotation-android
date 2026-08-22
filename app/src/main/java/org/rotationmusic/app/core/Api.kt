package org.rotationmusic.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to a Rotation server.
 *
 * Authentication is the server's session cookie, kept in memory here and
 * handed to Coil as well, so artwork loads with the same session.
 */
class Api(val base: HttpUrl) : Source {

    override val isDemo = false

    private val cookies = mutableMapOf<String, Cookie>()

    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
            list.forEach { cookies[it.name] = it }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.values.toList()
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    companion object {
        /** Accepts what people actually type: a host, a host:port, a full URL. */
        fun normalize(input: String): HttpUrl? {
            var text = input.trim()
            if (text.isEmpty()) return null
            val hadScheme = text.startsWith("http://") || text.startsWith("https://")
            if (!hadScheme) text = "http://$text"
            val url = text.toHttpUrlOrNull() ?: return null
            val builder = url.newBuilder().encodedPath("/").query(null).fragment(null)
            // A bare host means the default port; a typed one is kept as it is.
            val hadPort = input.substringAfterLast(']').contains(':')
            if (!hadPort && !hadScheme) builder.port(8770)
            return builder.build()
        }
    }

    // -- plumbing ---------------------------------------------------------

    private fun url(vararg segments: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val builder = base.newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private suspend inline fun <reified T> send(request: Request): T = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw SourceException.Unreachable(e.message)
        }
        response.use {
            val body = it.body?.string().orEmpty()
            when {
                it.isSuccessful -> try {
                    json.decodeFromString<T>(body)
                } catch (e: Exception) {
                    throw SourceException.NotRotation
                }
                it.code == 401 -> throw SourceException.Unauthorized
                it.code == 403 -> throw SourceException.Forbidden
                else -> throw SourceException.Server("HTTP ${it.code}")
            }
        }
    }

    private suspend inline fun <reified T> get(
        vararg segments: String, query: Map<String, String> = emptyMap(),
    ): T = send(Request.Builder().url(url(*segments, query = query)).build())

    private suspend inline fun <reified T> post(
        vararg segments: String, body: Map<String, Any> = emptyMap(),
    ): T {
        val payload = buildString {
            append("{")
            append(body.entries.joinToString(",") { (key, value) ->
                val encoded = when (value) {
                    is Boolean -> value.toString()
                    is Number -> value.toString()
                    else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
                }
                "\"$key\":$encoded"
            })
            append("}")
        }
        return send(
            Request.Builder()
                .url(url(*segments))
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
        )
    }

    // -- endpoints --------------------------------------------------------

    /** Checks that something Rotation-shaped answers, before asking for a password. */
    suspend fun probe(): VersionInfo {
        val info: VersionInfo = get("api", "version")
        if (info.name != "Rotation") throw SourceException.NotRotation
        return info
    }

    suspend fun login(username: String, password: String): Person {
        val response: LoginResponse =
            post("api", "login", body = mapOf("username" to username, "password" to password))
        return response.user
    }

    suspend fun logout() {
        runCatching { post<OkResponse>("api", "logout") }
    }

    override suspend fun me(): MeInfo = get("api", "me")

    override suspend fun overview(period: Period, user: String?): Overview =
        get("api", "overview", query = query(period, user))

    override suspend fun recent(limit: Int, user: String?): RecentPlays {
        val query = mutableMapOf("limit" to limit.toString())
        user?.let { query["user"] = it }
        return get("api", "recent", query = query)
    }

    override suspend fun wrapped(year: Int?, user: String?): Wrapped {
        val query = mutableMapOf<String, String>()
        year?.let { query["year"] = it.toString() }
        user?.let { query["user"] = it }
        return get("api", "wrapped", query = query)
    }

    override suspend fun friends(): FriendLists = get("api", "friends")

    override suspend fun addFriend(userId: String): Boolean {
        val response: FriendResponse = post("api", "friends", body = mapOf("user_id" to userId))
        return response.friends
    }

    override suspend fun removeFriend(userId: String, withdraw: Boolean) {
        val target = url("api", "friends", userId,
            query = if (withdraw) mapOf("withdraw" to "1") else emptyMap())
        send<OkResponse>(Request.Builder().url(target).delete().build())
    }

    override suspend fun compare(userId: String, period: Period): Comparison =
        get("api", "compare", query = mapOf("user" to userId, "range" to period.key))

    override suspend fun setDiscoverable(value: Boolean) {
        post<OkResponse>("api", "settings", body = mapOf("discoverable" to value))
    }

    override suspend fun detail(
        kind: String, id: String, range: String, user: String?,
    ): ItemDetail {
        val query = mutableMapOf("range" to range)
        user?.let { query["user"] = it }
        return get("api", kind, id, query = query)
    }

    override fun streamUrl(trackId: String?): String? {
        if (trackId.isNullOrEmpty()) return null
        return url("api", "stream", trackId).toString()
    }

    override suspend fun playlists(): List<ManagedPlaylist> =
        get<ManagedPlaylists>("api", "playlists").playlists

    override suspend fun writePlaylist(range: String, language: String): PlaylistWritten =
        post("api", "playlists", body = mapOf("range" to range, "lang" to language))

    override suspend fun dropPlaylist(range: String) {
        send<OkResponse>(Request.Builder().url(url("api", "playlists", range)).delete().build())
    }

    override fun artworkUrl(art: String?, size: Int): String? {
        if (art.isNullOrEmpty()) return null
        return url("api", "art", art, query = mapOf("size" to size.toString())).toString()
    }

    /** Coil needs the session cookie for artwork, and it uses its own client. */
    fun cookieHeader(): String =
        cookies.values.joinToString("; ") { "${it.name}=${it.value}" }

    private fun query(period: Period, user: String?): Map<String, String> {
        val query = mutableMapOf("range" to period.key)
        user?.let { query["user"] = it }
        return query
    }
}
