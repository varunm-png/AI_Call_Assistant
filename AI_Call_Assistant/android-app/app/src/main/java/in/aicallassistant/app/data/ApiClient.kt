package in.aicallassistant.app.data

import in.aicallassistant.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CallItem(
    val id: String,
    val number: String,
    val summary: String,
    val intent: String,
    val status: String,
    val createdAt: String
)

data class CallDetail(
    val id: String,
    val number: String,
    val language: String,
    val intent: String,
    val summary: String,
    val transcript: String,
    val status: String,
    val createdAt: String
)

data class AIReply(val text: String, val intent: String)

/**
 * Thin REST client for the FastAPI backend. Configure the backend URL via
 * BuildConfig.API_BASE_URL (android-app/app/build.gradle.kts).
 */
object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()

    private fun url(path: String) = BuildConfig.API_BASE_URL + path

    suspend fun startSession(number: String, language: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("number", number)
            .put("language", language)
            .toString()
        val req = Request.Builder()
            .url(url("api/session/start"))
            .post(body.toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            JSONObject(r.body!!.string()).getString("session_id")
        }
    }

    suspend fun chat(sessionId: String, text: String): AIReply = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("session_id", sessionId)
            .put("message", text)
            .toString()

        val req = Request.Builder()
            .url(url("api/chat"))
            .post(body.toRequestBody(jsonType))
            .build()

        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val obj = JSONObject(r.body!!.string())
            AIReply(obj.optString("reply"), obj.optString("intent", "unknown"))
        }
    }

    suspend fun endSession(sessionId: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("session_id", sessionId).toString()
        val req = Request.Builder()
            .url(url("api/session/end"))
            .post(body.toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            JSONObject(r.body!!.string()).optString("summary", "Call ended.")
        }
    }

    suspend fun listCalls(): List<CallItem> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url("api/calls")).get().build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@withContext emptyList()
            val arr = org.json.JSONArray(r.body!!.string())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CallItem(
                    o.optString("id"),
                    o.optString("number"),
                    o.optString("summary"),
                    o.optString("intent"),
                    o.optString("status", "ended"),
                    o.optString("created_at")
                )
            }
        }
    }

    suspend fun getCallDetail(id: String): CallDetail = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url("api/calls/$id")).get().build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val o = JSONObject(r.body!!.string())
            CallDetail(
                o.optString("id"),
                o.optString("number"),
                o.optString("language", "en"),
                o.optString("intent"),
                o.optString("summary"),
                o.optString("transcript"),
                o.optString("status", "ended"),
                o.optString("created_at")
            )
        }
    }
}
