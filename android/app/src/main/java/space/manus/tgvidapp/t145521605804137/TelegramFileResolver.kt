package space.manus.tgvidapp.t145521605804137

import com.chaquo.python.Python
import com.chaquo.python.PyException
import org.json.JSONException
import org.json.JSONObject

/** Telethon is used only to inspect the public post and return a Bot API file_id. */
object TelegramFileResolver {
    @Synchronized
    fun resolve(link: String, credentials: TelegramCredentials): String {
        val payload = try {
            val module = Python.getInstance().getModule("telegram_resolver")
            module.callAttr(
                "resolve_public_video",
                link,
                credentials.apiId,
                credentials.apiHash,
                credentials.botToken,
            ).toString()
        } catch (error: PyException) {
            throw AppFailure(
                errorType = "TelethonBridgeException",
                errorMessage = error.message ?: "Chaquopy Python bridge failed without an error message.",
                cause = error,
            )
        }
        val response = try {
            JSONObject(payload)
        } catch (error: JSONException) {
            throw AppFailure(
                errorType = "TelethonBridgeResponseError",
                errorMessage = "Python resolver returned invalid JSON.",
                details = payload,
                cause = error,
            )
        }
        if (!response.optBoolean("ok")) {
            val failure = response.optJSONObject("error") ?: JSONObject()
            val rawCode = failure.opt("code")
            val code = if (rawCode == null || rawCode == JSONObject.NULL) null else rawCode.toString()
            val details = failure.optJSONObject("details")?.toString(2)
                ?.takeIf { it != "{}" }
                ?: response.toString(2)
            throw AppFailure(
                errorType = failure.optString("type").ifBlank { "TelethonResolverError" },
                errorCode = code,
                errorMessage = failure.optString("message").ifBlank { "Telethon resolver failed without a message." },
                details = details,
            )
        }
        val fileId = response.optString("file_id")
        if (fileId.isBlank()) throw IllegalStateException("Telegram က video file_id မပေးခဲ့ပါ။")
        return fileId
    }
}
