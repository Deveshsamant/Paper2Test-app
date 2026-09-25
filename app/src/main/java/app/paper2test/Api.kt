package app.paper2test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, val code: String, val body: JSONObject) : Exception(code)

/** Small JSON client for the Paper2Test API (same endpoints the website uses). */
class Api(val site: String, private val session: Session) {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun get(path: String): JSONObject = call(Request.Builder().url("$site/api$path").get())
    suspend fun post(path: String, body: JSONObject = JSONObject()): JSONObject = call(Request.Builder().url("$site/api$path").post(body.toString().toRequestBody(json)))
    suspend fun put(path: String, body: JSONObject): JSONObject = call(Request.Builder().url("$site/api$path").put(body.toString().toRequestBody(json)))
    suspend fun postBytes(path: String, bytes: ByteArray, mime: String): JSONObject = call(Request.Builder().url("$site/api$path").post(bytes.toRequestBody(mime.toMediaType())))

    private suspend fun call(b: Request.Builder): JSONObject = withContext(Dispatchers.IO) {
        b.header("User-Agent", "Paper2TestApp/${BuildConfig.VERSION_NAME} Android")
        b.header("x-app-version", BuildConfig.VERSION_CODE.toString()) // the admin sees who is on old versions
        session.token?.let { b.header("Authorization", "Bearer $it") }
        session.space?.let { b.header("x-p2t-space", it) } // papers, tests, plan and branding belong to the space
        http.newCall(b.build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            val obj = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!res.isSuccessful) {
                if (res.code == 401) session.token = null
                throw ApiException(res.code, obj.optString("error", "http_${res.code}"), obj)
            }
            obj
        }
    }
}
