package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.source
import org.json.JSONObject
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ServerAddress {
    fun normalize(input: String): String {
        val value = input.trim()
        val url = (if ("://" in value) value else "https://$value").toHttpUrlOrNull()
        require(url != null && url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty() &&
            url.encodedPath == "/" && url.query == null && url.fragment == null) {
            "Enter an instance domain, such as misskey.io, using HTTPS."
        }
        return url.toString().removeSuffix("/")
    }
}

class ApiFailure(val status: Int, val code: String? = null) : IOException(
    "Server request failed ($status${code?.let { ":$it" }.orEmpty()})",
)

/** No redirects: an authenticated request must never forward its token to another host. */
class MisskeyApi(private val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false)
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(40, TimeUnit.SECONDS).build()) {
    suspend fun post(origin: String, endpoint: String, body: JSONObject = JSONObject()): HttpResponse =
        execute(Request.Builder().url("$origin/api/$endpoint")
            .header("Accept", "application/json")
            .header("User-Agent", "Palustris/0.1 (Android)")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build())

    suspend fun postForm(
        origin: String,
        endpoint: String,
        fields: Map<String, String>,
        bearerToken: String? = null,
    ): HttpResponse = postForm(origin, endpoint, fields.entries.map { it.key to it.value }, bearerToken)

    suspend fun postForm(
        origin: String,
        endpoint: String,
        fields: List<Pair<String, String>>,
        bearerToken: String? = null,
    ): HttpResponse = postForm(endpointUrl = "$origin/$endpoint", fields = fields, bearerToken = bearerToken)

    /** Uses a fully built URL so callers can keep opaque path/query values encoded safely. */
    suspend fun postForm(
        endpointUrl: HttpUrl,
        fields: List<Pair<String, String>>,
        bearerToken: String? = null,
    ): HttpResponse = postForm(endpointUrl.toString(), fields, bearerToken)

    private suspend fun postForm(
        endpointUrl: String,
        fields: List<Pair<String, String>>,
        bearerToken: String?,
    ): HttpResponse =
        execute(Request.Builder().url(endpointUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "Palustris/0.1 (Android)")
            .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
            .post(FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build())
            .build())

    suspend fun patchForm(
        origin: String,
        endpoint: String,
        fields: List<Pair<String, String>>,
        bearerToken: String? = null,
    ): HttpResponse = execute(Request.Builder().url("$origin/$endpoint")
        .header("Accept", "application/json")
        .header("User-Agent", "Palustris/0.1 (Android)")
        .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
        .patch(FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build())
        .build())

    suspend fun putForm(
        origin: String,
        endpoint: String,
        fields: List<Pair<String, String>>,
        bearerToken: String? = null,
    ): HttpResponse = execute(Request.Builder().url("$origin/$endpoint")
        .header("Accept", "application/json")
        .header("User-Agent", "Palustris/0.1 (Android)")
        .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
        .put(FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build())
        .build())

    suspend fun delete(
        origin: String,
        endpoint: String,
        bearerToken: String? = null,
    ): HttpResponse = execute(Request.Builder().url("$origin/$endpoint")
        .header("Accept", "application/json")
        .header("User-Agent", "Palustris/0.1 (Android)")
        .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
        .delete()
        .build())

    suspend fun postMultipart(
        origin: String,
        endpoint: String,
        file: InputStream,
        mimeType: String,
        fileName: String = "upload",
        bearerToken: String? = null,
    ): HttpResponse = execute(Request.Builder().url("$origin/$endpoint")
        .header("Accept", "application/json")
        .header("User-Agent", "Palustris/0.1 (Android)")
        .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
        .post(MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, file.readBytes().toRequestBody(mimeType.toMediaType()))
            .build())
        .build())

    /** PATCH multipart for avatar and header bytes; application-owned streams close after the request. */
    suspend fun patchMultipart(
        origin: String,
        endpoint: String,
        fields: List<Pair<String, String>> = emptyList(),
        files: List<MultipartFileBody> = emptyList(),
        bearerToken: String? = null,
    ): HttpResponse {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (name, value) -> addFormDataPart(name, value) }
            files.forEach { part -> addFormDataPart(part.fieldName, part.fileName, part.body()) }
        }.build()
        return execute(Request.Builder().url("$origin/$endpoint")
            .header("Accept", "application/json")
            .header("User-Agent", "Palustris/0.1 (Android)")
            .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
            .patch(body)
            .build())
    }

    suspend fun get(origin: String, endpoint: String, bearerToken: String? = null): HttpResponse =
        execute(Request.Builder().url("$origin/api/$endpoint")
            .header("Accept", "application/json")
            .header("User-Agent", "Palustris/0.1 (Android)")
            .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
            .get().build())

    suspend fun getUrl(url: String, bearerToken: String? = null): HttpResponse =
        execute(Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Palustris/0.1 (Android)")
            .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
            .get().build())

    fun webSocket(origin: String, path: String, headers: Map<String, String> = emptyMap(), listener: WebSocketListener): WebSocket {
        val base = origin.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid stream origin")
        require(base.scheme == "https" && base.username.isEmpty() && base.password.isEmpty() && base.host.isNotBlank()) {
            "Stream origin must be HTTPS without credentials"
        }
        val url = base.newBuilder()
            // OkHttp's WebSocket factory upgrades an HTTPS request itself. HttpUrl only
            // accepts HTTP(S) schemes, so keeping HTTPS here also preserves validation.
            .scheme("https")
            .encodedPath(path)
            .query(null)
            .fragment(null)
            .build()
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        return client.newWebSocket(request, listener)
    }

    private suspend fun execute(request: Request): HttpResponse = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val text = it.body?.string().orEmpty()
                            if (!it.isSuccessful) {
                                val code = runCatching { JSONObject(text).optJSONObject("error")?.optString("code") }.getOrNull()
                                throw ApiFailure(it.code, code)
                            }
                            continuation.resume(HttpResponse(text, it.headers))
                        } catch (e: Exception) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                    }
                }
            })
        }
    }
}

/** One file part for a multipart patch; the stream is application-owned and closes after the request. */
class MultipartFileBody(
    val fieldName: String,
    val fileName: String?,
    private val mimeType: String,
    private val stream: InputStream,
) {
    internal fun body(): RequestBody = object : RequestBody() {
        override fun contentType(): MediaType? = mimeType.toMediaType()
        override fun writeTo(sink: okio.BufferedSink) {
            stream.use { sink.writeAll(it.source()) }
        }
    }
}

fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
