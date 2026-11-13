package space.manus.tgvidapp.nativeapi

import android.content.Context
import com.chaquo.python.AndroidPlatform
import com.chaquo.python.Python
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LocalBotApiModule(private val context: Context) {
  companion object {
    private const val PORT = 18081
    init { System.loadLibrary("localbotapi") }
    @JvmStatic private external fun nativeStart(apiId: Long, apiHash: String, work: String, files: String, port: Int): Boolean
    @JvmStatic private external fun nativeStop()
    @JvmStatic private external fun nativeIsRunning(): Boolean
  }

  data class Media(val fileId: String, val fileName: String, val size: Long)
  data class Saved(val path: File, val size: Long)

  fun start(apiId: Long, apiHash: String): Boolean {
    val root = File(context.filesDir, "telegram-bot-api").apply { mkdirs() }
    File(root, "files").mkdirs(); File(root, "tmp").mkdirs()
    return nativeStart(apiId, apiHash, root.absolutePath, File(root, "files").absolutePath, PORT)
  }

  fun stop() = nativeStop()
  fun isRunning() = nativeIsRunning()

  fun resolve(apiId: Int, apiHash: String, botToken: String, link: String): Media {
    if (!Python.isStarted()) Python.start(AndroidPlatform(context))
    val session = File(context.filesDir, "telethon").apply { mkdirs() }
    val raw = Python.getInstance().getModule("telethon_bridge")
      .callAttr("resolve_media", apiId, apiHash, botToken, link, session.absolutePath).toString()
    val json = JSONObject(raw)
    val fileId = json.optString("file_id")
    if (fileId.isBlank()) throw IllegalStateException("Telethon က file_id မရပါ။")
    return Media(fileId, json.optString("file_name").ifBlank { "video_${json.optInt("message_id")}.mp4" }, json.optLong("size"))
  }

  fun download(botToken: String, media: Media): Saved {
    if (!isRunning()) throw IllegalStateException("Local Bot API Server မစတင်ရသေးပါ။")
    val root = File(context.filesDir, "telegram-bot-api/files").canonicalFile
    val destRoot = File(context.filesDir, "Movies/TelegramVideo").apply { mkdirs() }.canonicalFile
    val safe = media.fileName.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").replace("..", "_").ifBlank { "video.mp4" }.take(180)
    val dest = File(destRoot, safe).canonicalFile
    if (!dest.path.startsWith(destRoot.path + File.separator)) throw IllegalArgumentException("File name မမှန်ပါ။")
    val token = URLEncoder.encode(botToken, "UTF-8")
    val id = URLEncoder.encode(media.fileId, "UTF-8")
    val connection = (URL("http://127.0.0.1:$PORT/bot$token/getFile?file_id=$id").openConnection() as HttpURLConnection).apply {
      connectTimeout = 15000; readTimeout = 60000; requestMethod = "GET"
    }
    val response = try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
    val json = JSONObject(response)
    if (!json.optBoolean("ok")) throw IllegalStateException("Local Bot API getFile မအောင်မြင်ပါ: $response")
    val source = File(json.optJSONObject("result")?.optString("file_path").orEmpty()).canonicalFile
    if (!source.path.startsWith(root.path + File.separator) || !source.isFile) throw SecurityException("Bot API file path မမှန်ပါ။")
    FileInputStream(source).use { input -> FileOutputStream(dest).use { output -> input.copyTo(output); output.fd.sync() } }
    return Saved(dest, dest.length())
  }
}
