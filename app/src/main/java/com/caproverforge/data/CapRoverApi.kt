package com.caproverforge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** An error reported by CapRover (`status` != 100/101/102) or by the transport. */
class CapRoverException(val status: Int, message: String) : Exception(message) {
    companion object {
        const val OKAY = 100
        const val OKAY_BUILD_STARTED = 101
        const val OK_PARTIALLY = 102
        const val NOT_AUTHORIZED = 1102
        const val ALREADY_EXIST = 1103
        const val BAD_NAME = 1104
        const val WRONG_PASSWORD = 1105
        const val AUTH_TOKEN_INVALID = 1106
        const val PASSWORD_BACK_OFF = 1113
        const val OTP_REQUIRED = 1114
        const val NETWORK = -1
        const val HTTP = -2
    }
}

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
    encodeDefaults = true
}

/** Low-level client for `https://<captain>/api/v2`. Every call returns the envelope's `data` field. */
class CapRoverApi(
    private val sessionStore: SessionStore,
    private val http: OkHttpClient,
) {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the server rejects the stored token; the UI responds by returning to sign-in. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun login(baseUrl: String, password: String, otpToken: String?): String {
        val body = buildJsonObject {
            put("password", password)
            if (!otpToken.isNullOrBlank()) put("otpToken", otpToken.trim())
        }
        val request = Request.Builder()
            .url(url(baseUrl, "/login"))
            .header(NAMESPACE_HEADER, NAMESPACE)
            .post(body.toString().toRequestBody(jsonType))
            .build()
        val data = execute(request, authenticated = false)
        return data.jsonObject["token"]?.jsonPrimitive?.contentOrNull
            ?: throw CapRoverException(CapRoverException.HTTP, "The server did not return a session token.")
    }

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement {
        val session = requireSession()
        val url = url(session.baseUrl, path).newBuilder().apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        return execute(authed(session, url).get().build())
    }

    suspend fun post(path: String, body: JsonElement = JsonObject(emptyMap())): JsonElement {
        val session = requireSession()
        val request = authed(session, url(session.baseUrl, path))
            .post(body.toString().toRequestBody(jsonType))
            .build()
        return execute(request)
    }

    suspend fun postMultipart(path: String, part: MultipartBody.Part): JsonElement {
        val session = requireSession()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addPart(part).build()
        return execute(authed(session, url(session.baseUrl, path)).post(body).build())
    }

    fun buildPart(name: String, fileName: String, body: RequestBody): MultipartBody.Part =
        MultipartBody.Part.createFormData(name, fileName, body)

    /** Absolute URL for a path under the API root, e.g. for downloads opened in the browser. */
    fun absoluteUrl(path: String): String = url(requireSession().baseUrl, path).toString()

    private fun requireSession(): Session =
        sessionStore.session.value ?: throw CapRoverException(CapRoverException.AUTH_TOKEN_INVALID, "Please sign in again.")

    private fun authed(session: Session, url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .header(NAMESPACE_HEADER, NAMESPACE)
            .header(TOKEN_HEADER, session.token)

    private fun url(baseUrl: String, path: String): HttpUrl {
        val raw = baseUrl.trimEnd('/') + API_ROOT + path
        return raw.toHttpUrlOrNull()
            ?: throw CapRoverException(CapRoverException.NETWORK, "“$baseUrl” is not a valid server address.")
    }

    private suspend fun execute(request: Request, authenticated: Boolean = true): JsonElement =
        withContext(Dispatchers.IO) {
            val text = try {
                http.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        throw CapRoverException(CapRoverException.HTTP, httpErrorMessage(response.code, body))
                    }
                    body
                }
            } catch (e: CapRoverException) {
                throw e
            } catch (e: UnknownHostException) {
                throw CapRoverException(CapRoverException.NETWORK, "Can't find ${request.url.host}. Check the address and your internet connection.")
            } catch (e: SocketTimeoutException) {
                throw CapRoverException(CapRoverException.NETWORK, "The server took too long to respond. Try again.")
            } catch (e: SSLException) {
                throw CapRoverException(CapRoverException.NETWORK, "Secure connection failed: ${e.message ?: "certificate problem"}")
            } catch (e: IOException) {
                throw CapRoverException(CapRoverException.NETWORK, "Network error: ${e.message ?: "connection failed"}")
            }

            val envelope = runCatching { ApiJson.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: throw CapRoverException(
                    CapRoverException.HTTP,
                    "Unexpected response from server. Is this a CapRover dashboard address?",
                )
            val status = envelope["status"]?.jsonPrimitive?.intOrNull ?: CapRoverException.HTTP
            val description = envelope["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
            when (status) {
                CapRoverException.OKAY, CapRoverException.OKAY_BUILD_STARTED, CapRoverException.OK_PARTIALLY ->
                    envelope["data"] ?: JsonNull
                CapRoverException.AUTH_TOKEN_INVALID, CapRoverException.NOT_AUTHORIZED -> {
                    if (authenticated) {
                        sessionStore.clear()
                        _sessionExpired.tryEmit(Unit)
                    }
                    throw CapRoverException(status, description.ifBlank { "Your session has expired. Please sign in again." })
                }
                else -> throw CapRoverException(status, friendlyMessage(status, description))
            }
        }

    private fun friendlyMessage(status: Int, description: String): String = when (status) {
        CapRoverException.WRONG_PASSWORD -> "Incorrect password."
        CapRoverException.PASSWORD_BACK_OFF -> description.ifBlank { "Too many attempts. Wait a moment and try again." }
        CapRoverException.OTP_REQUIRED -> "Enter the code from your authenticator app."
        else -> description.ifBlank { "CapRover returned error $status." }
    }

    private fun httpErrorMessage(code: Int, body: String): String {
        val snippet = body.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(160)
        return when (code) {
            404 -> "CapRover API not found at this address (HTTP 404)."
            502, 503, 504 -> "CapRover is not responding (HTTP $code). It may be restarting."
            else -> "HTTP $code${if (snippet.isNotEmpty()) ": $snippet" else ""}"
        }
    }

    companion object {
        private const val API_ROOT = "/api/v2"
        private const val TOKEN_HEADER = "x-captain-auth"
        private const val NAMESPACE_HEADER = "x-namespace"
        const val NAMESPACE = "captain"
    }
}
