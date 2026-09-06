package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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

class ApiFailure(val status: Int, val code: String? = null) : IOException("Server request failed ($status)")

/** No redirects: an authenticated POST must never forward its token to another host. */
class MisskeyApi(private val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false)
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(40, TimeUnit.SECONDS).build()) {
    suspend fun post(origin: String, endpoint: String, body: JSONObject = JSONObject()): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$origin/api/$endpoint")
            .header("Accept", "application/json")
            .header("User-Agent", "Palustris/0.1 (Android)")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
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
                            continuation.resume(text)
                        } catch (e: Exception) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                    }
                }
            })
        }
    }
}

fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
