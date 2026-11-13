package expo.modules.localbotapi

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.chaquo.python.AndroidPlatform
import com.chaquo.python.Python
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

class LocalBotApiModule : Module() {
  companion object {
    private const val SERVER_PORT = 18081

    init {
      System.loadLibrary("localbotapi")
    }

    @JvmStatic private external fun nativeStart(
      apiId: Long,
      apiHash: String,
      workingDirectory: String,
      filesDirectory: String,
      port: Int,
    ): Boolean

    @JvmStatic private external fun nativeStop()
    @JvmStatic private external fun nativeIsRunning(): Boolean
  }

  override fun definition() = ModuleDefinition {
    Name("LocalBotApi")
    Events("onDownloadProgress")

    AsyncFunction("getStatus") {
      statusBundle()
    }

    AsyncFunction("resolveTelethon") { apiId: Int, apiHash: String, botToken: String, link: String ->
      val context = appContext.reactContext ?: throw IllegalStateException("Android context မရပါ။")
      if (!Python.isStarted()) Python.start(AndroidPlatform(context))
      val sessionRoot = File(context.filesDir, "telethon").apply { mkdirs() }
      val json = Python.getInstance()
        .getModule("telethon_bridge")
        .callAttr("resolve_media", apiId, apiHash, botToken, link, sessionRoot.absolutePath)
        .toString()
      val result = JSONObject(json)
      Bundle().apply {
        putString("channel", result.optString("channel"))
        putLong("chatId", result.optLong("chat_id"))
        putInt("messageId", result.optInt("message_id"))
        putString("mediaType", result.optString("media_type"))
        putString("mimeType", result.optString("mime_type"))
        putString("fileName", result.optString("file_name"))
        putLong("size", result.optLong("size"))
        putLong("documentId", result.optLong("document_id"))
        putLong("accessHash", result.optLong("access_hash"))
        putLong("dcId", result.optLong("dc_id"))
        putString("fileId", result.optString("file_id"))
      }
    }

    AsyncFunction("downloadTelethonVideo") { apiId: Int, apiHash: String, botToken: String, link: String, fileName: String ->
      val context = appContext.reactContext ?: throw IllegalStateException("Android context မရပါ။")
      if (!Python.isStarted()) Python.start(AndroidPlatform(context))
      val destinationRoot = File(context.filesDir, "Movies/TelegramVideo").apply { mkdirs() }.canonicalFile
      val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_")
        .replace("..", "_").ifBlank { "video.mp4" }.take(180)
      val destination = File(destinationRoot, safeName).canonicalFile
      if (!destination.path.startsWith(destinationRoot.path + File.separator)) {
        throw IllegalArgumentException("သိမ်းမည့် file name မမှန်ပါ။")
      }
      val sessionRoot = File(context.filesDir, "telethon").apply { mkdirs() }
      val json = Python.getInstance()
        .getModule("telethon_bridge")
        .callAttr("download_media", apiId, apiHash, botToken, link, sessionRoot.absolutePath, destination.absolutePath)
        .toString()
      val result = JSONObject(json)
      Bundle().apply {
        putString("uri", Uri.fromFile(destination).toString())
        putLong("sizeBytes", result.optLong("size"))
      }
    }

    AsyncFunction("startServer") { apiId: Long, apiHash: String ->
      val context = appContext.reactContext ?: throw IllegalStateException("Android context မရပါ။")
      val root = File(context.filesDir, "telegram-bot-api")
      val files = File(root, "files")
      val temporary = File(root, "tmp")
      root.mkdirs()
      files.mkdirs()
      temporary.mkdirs()

      val started = nativeStart(apiId, apiHash, root.absolutePath, files.absolutePath, SERVER_PORT)
      if (!started) {
        throw IllegalStateException("Telegram Bot API native engine မစတင်နိုင်သေးပါ။")
      }
      statusBundle()
    }

    AsyncFunction("stopServer") {
      nativeStop()
    }

    AsyncFunction("downloadVideo") { botToken: String, fileId: String, fileName: String ->
      val context = appContext.reactContext ?: throw IllegalStateException("Android context မရပါ။")
      if (!nativeIsRunning()) {
        throw IllegalStateException("Telegram Bot API server မစတင်ရသေးပါ။")
      }
      val root = File(context.filesDir, "telegram-bot-api")
      val sourceRoot = File(root, "files").canonicalFile
      val destinationRoot = File(context.filesDir, "Movies/TelegramVideo").apply { mkdirs() }.canonicalFile
      val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_")
        .replace("..", "_").ifBlank { "video.mp4" }.take(180)
      val destination = File(destinationRoot, safeName).canonicalFile
      if (!destination.path.startsWith(destinationRoot.path + File.separator)) {
        throw IllegalArgumentException("သိမ်းမည့် file name မမှန်ပါ။")
      }

      val serviceIntent = Intent(context, LocalBotApiForegroundService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
      else context.startService(serviceIntent)

      try {
        sendProgress("requesting", 0L, null, null)
        val encodedToken = URLEncoder.encode(botToken, "UTF-8")
        val encodedFileId = URLEncoder.encode(fileId, "UTF-8")
        val connection = (URL("http://127.0.0.1:$SERVER_PORT/bot$encodedToken/getFile?file_id=$encodedFileId")
          .openConnection() as HttpURLConnection).apply {
          requestMethod = "GET"
          connectTimeout = 15_000
          readTimeout = 60_000
        }
        val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        connection.disconnect()
        val json = JSONObject(response)
        if (!json.optBoolean("ok")) throw IllegalStateException("Bot API getFile မအောင်မြင်ပါ။")
        val sourcePath = json.optJSONObject("result")?.optString("file_path").orEmpty()
        if (sourcePath.isBlank()) throw IllegalStateException("Bot API က local file path မပေးပါ။")
        val source = File(sourcePath).canonicalFile
        if (!source.path.startsWith(sourceRoot.path + File.separator) || !source.isFile) {
          throw SecurityException("Bot API file path ကို app-private directory အတွင်း မတွေ့ပါ။")
        }

        val total = source.length()
        var copied = 0L
        val startedAt = System.nanoTime()
        FileInputStream(source).use { input ->
          FileOutputStream(destination).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
              val count = input.read(buffer)
              if (count < 0) break
              output.write(buffer, 0, count)
              copied += count
              val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000.0
              val speed = if (elapsed > 0) (copied / elapsed).toLong() else null
              sendProgress("copying", copied, total, speed)
            }
            output.fd.sync()
          }
        }
        if (!destination.isFile || destination.length() != copied) {
          throw IllegalStateException("Video file ကို အပြည့်အဝ မသိမ်းနိုင်ပါ။")
        }
        sendProgress("complete", copied, total, null)
        Bundle().apply {
          putString("uri", Uri.fromFile(destination).toString())
          putLong("sizeBytes", copied)
        }
      } finally {
        context.stopService(serviceIntent)
      }
    }
  }

  private fun statusBundle(): Bundle = Bundle().apply {
    putBoolean("running", nativeIsRunning())
    if (nativeIsRunning()) putInt("port", SERVER_PORT)
  }

  private fun sendProgress(phase: String, copied: Long, total: Long?, speed: Long?) {
    val payload = Bundle().apply {
      putString("phase", phase)
      putLong("copiedBytes", copied)
      if (total != null) putLong("totalBytes", total)
      if (speed != null) putLong("speedBytesPerSecond", speed)
    }
    sendEvent("onDownloadProgress", payload)
  }

}
