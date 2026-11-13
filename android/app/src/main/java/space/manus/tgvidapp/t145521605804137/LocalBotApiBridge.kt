package space.manus.tgvidapp.t145521605804137

import expo.modules.localbotapi.LocalBotApiModule as PrebuiltBotApiNative
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object LocalBotApiBridge {
    private const val SERVER_PORT = 18081

    data class Progress(
        val phase: String,
        val copiedBytes: Long,
        val totalBytes: Long?,
        val speedBytesPerSecond: Long?,
    )

    data class SavedVideo(val uri: Uri, val sizeBytes: Long)

    fun isRunning(): Boolean = PrebuiltBotApiNative.nativeIsRunning()

    @Synchronized
    fun startServer(context: Context, credentials: TelegramCredentials) {
        if (PrebuiltBotApiNative.nativeIsRunning()) return
        val root = File(context.filesDir, "telegram-bot-api")
        val working = File(root, "work")
        val files = File(root, "files")
        val temporary = File(root, "tmp")
        if (!(working.mkdirs() || working.isDirectory)) {
            throw AppFailure("StorageDirectoryError", errorMessage = "Bot API server work folder ကို မဖန်တီးနိုင်ပါ။", details = working.absolutePath)
        }
        if (!(files.mkdirs() || files.isDirectory)) {
            throw AppFailure("StorageDirectoryError", errorMessage = "Bot API server files folder ကို မဖန်တီးနိုင်ပါ။", details = files.absolutePath)
        }
        if (!(temporary.mkdirs() || temporary.isDirectory)) {
            throw AppFailure("StorageDirectoryError", errorMessage = "Bot API server temporary folder ကို မဖန်တီးနိုင်ပါ။", details = temporary.absolutePath)
        }
        val started = PrebuiltBotApiNative.nativeStart(
            credentials.apiId.toLong(),
            credentials.apiHash,
            nativeDirectoryArgument(working),
            nativeDirectoryArgument(files),
            SERVER_PORT,
        )
        if (!started) {
            throw AppFailure(
                errorType = "BotApiServerStartError",
                errorMessage = "Telegram Bot API Server ကို စတင်မရပါ။",
                details = "ပါဝင်လာတဲ့ native binary က စတင်မှုအောင်မြင်/မအောင်မြင်ကို Boolean ဖြင့်သာ ပြန်ပေးပါတယ်။ Native အသေးစိတ်အကြောင်းရင်းကို Logcat မှာ စစ်ပါ။",
            )
        }
    }

    @Synchronized
    fun stopServer() {
        PrebuiltBotApiNative.nativeStop()
    }

    fun downloadVideo(
        context: Context,
        botToken: String,
        fileId: String,
        requestedFileName: String,
        onProgress: (Progress) -> Unit,
    ): SavedVideo {
        if (!PrebuiltBotApiNative.nativeIsRunning()) {
            throw AppFailure("BotApiServerNotRunningError", errorMessage = "Telegram Bot API Server မစတင်ရသေးပါ။")
        }

        var destinationUri: Uri? = null
        var destinationFile: File? = null
        try {
            onProgress(Progress("requesting", 0L, null, null))
            val safeToken = URLEncoder.encode(botToken, Charsets.UTF_8.name())
            val safeFileId = URLEncoder.encode(fileId, Charsets.UTF_8.name())
            val url = URL("http://127.0.0.1:$SERVER_PORT/bot$safeToken/getFile?file_id=$safeFileId")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 180_000
                instanceFollowRedirects = false
            }
            var responseCode = -1
            var responseMessage: String? = null
            val responseText = try {
                responseCode = connection.responseCode
                responseMessage = connection.responseMessage
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } catch (error: Exception) {
                throw AppFailure(
                    errorType = error.javaClass.simpleName.ifBlank { error.javaClass.name },
                    errorMessage = error.localizedMessage ?: "Local Bot API getFile request failed.",
                    details = "Method: getFile\nEndpoint: 127.0.0.1:$SERVER_PORT\nConnect timeout: ${connection.connectTimeout} ms\nRead timeout: ${connection.readTimeout} ms\n${error.javaClass.name}",
                    cause = error,
                )
            } finally {
                connection.disconnect()
            }
            if (responseText.isBlank()) {
                throw AppFailure(
                    errorType = "BotApiEmptyResponseError",
                    errorCode = responseCode.toString(),
                    errorMessage = responseMessage ?: "Bot API Server returned an empty response.",
                    details = "HTTP status: $responseCode",
                )
            }
            val json = try {
                JSONObject(responseText)
            } catch (error: Exception) {
                throw AppFailure(
                    errorType = "BotApiInvalidJsonResponseError",
                    errorCode = responseCode.toString(),
                    errorMessage = "Bot API Server response is not valid JSON.",
                    details = "HTTP status: $responseCode\nComplete response body:\n$responseText",
                    cause = error,
                )
            }
            if (!json.optBoolean("ok")) {
                val rawCode = json.opt("error_code")
                val errorCode = if (rawCode == null || rawCode == JSONObject.NULL) responseCode.toString() else rawCode.toString()
                val description = json.optString("description").ifBlank { "Bot API Server returned ok=false without a description." }
                val parameters = json.optJSONObject("parameters")?.toString(2)
                val details = buildString {
                    append("HTTP status: ").append(responseCode)
                    parameters?.let { append("\nResponse parameters:\n").append(it) }
                    append("\nComplete Bot API response:\n").append(responseText)
                }
                throw AppFailure(
                    errorType = "TelegramBotApiError",
                    errorCode = errorCode,
                    errorMessage = description,
                    details = details,
                )
            }
            val returnedPath = json.optJSONObject("result")?.optString("file_path").orEmpty()
            if (returnedPath.isBlank()) {
                throw AppFailure(
                    errorType = "BotApiMissingFilePathError",
                    errorCode = responseCode.toString(),
                    errorMessage = "Bot API Server did not return result.file_path.",
                    details = responseText,
                )
            }

            val root = File(context.filesDir, "telegram-bot-api").canonicalFile
            val sourceRoot = File(root, "files").canonicalFile
            val sourceCandidate = File(returnedPath).let { if (it.isAbsolute) it else File(root, returnedPath) }.canonicalFile
            val insidePrivateWorkspace = sourceCandidate.path.startsWith(root.path + File.separator)
            if (!insidePrivateWorkspace || !sourceCandidate.isFile) {
                throw AppFailure(
                    errorType = "BotApiDownloadedFileMissingError",
                    errorMessage = "Bot API Server returned a missing file or a path outside its private workspace.",
                    details = "Returned path: $returnedPath\nResolved path: ${sourceCandidate.absolutePath}\nExists: ${sourceCandidate.exists()}\nIs file: ${sourceCandidate.isFile}\nExpected files folder: ${sourceRoot.absolutePath}\nAllowed private workspace: ${root.absolutePath}",
                )
            }

            val safeName = sanitizeFileName(requestedFileName)
            val totalBytes = sourceCandidate.length()
            if (totalBytes <= 0L) {
                throw AppFailure("EmptyVideoFileError", errorMessage = "Telegram က ရလာတဲ့ video file သည် ဗလာဖြစ်နေပါတယ်။")
            }
            val resolver = context.contentResolver
            val outputStream: OutputStream
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TGMovies")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                destinationUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw AppFailure("PublicStorageInsertError", errorMessage = "Download/TGMovies ထဲမှာ ဖိုင်အသစ်ဖန်တီးမရပါ။")
                outputStream = resolver.openOutputStream(destinationUri!!, "w")
                    ?: throw AppFailure("PublicStorageOpenError", errorMessage = "Download/TGMovies ထဲက ဖိုင်ကို ဖွင့်မရပါ။")
            } else {
                if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    throw AppFailure(
                        "StoragePermissionRequiredError",
                        errorMessage = "Android ဗားရှင်းဟောင်းတွင် Download/TGMovies သို့ သိမ်းရန် storage permission လိုအပ်ပါတယ်။",
                        details = "Permission: android.permission.WRITE_EXTERNAL_STORAGE",
                    )
                }
                val outputRoot = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TGMovies")
                if (!(outputRoot.mkdirs() || outputRoot.isDirectory)) {
                    throw AppFailure("StorageDirectoryError", errorMessage = "Download/TGMovies folder ကို မဖန်တီးနိုင်ပါ။", details = outputRoot.absolutePath)
                }
                destinationFile = uniqueDestination(outputRoot.canonicalFile, safeName)
                destinationUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destinationFile!!)
                outputStream = FileOutputStream(destinationFile)
            }

            var copiedBytes = 0L
            val startedAt = System.nanoTime()
            FileInputStream(sourceCandidate).use { input ->
                outputStream.use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException("Download canceled.")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copiedBytes += count
                        val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000.0
                        val speed = if (elapsed > 0) (copiedBytes / elapsed).toLong() else null
                        onProgress(Progress("copying", copiedBytes, totalBytes, speed))
                    }
                    output.flush()
                    (output as? FileOutputStream)?.fd?.sync()
                }
            }
            if (copiedBytes != totalBytes) {
                throw AppFailure(
                    "FileCopyVerificationError",
                    errorMessage = "ဗီဒီယိုဖိုင်အပြည့်အစုံ မကူးယူနိုင်ပါ။",
                    details = "Expected: $totalBytes bytes; copied: $copiedBytes bytes",
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalized = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                if (resolver.update(destinationUri!!, finalized, null, null) <= 0) {
                    throw AppFailure("PublicStorageFinalizeError", errorMessage = "ဖိုင်ကို Download/TGMovies ထဲမှာ publish မလုပ်နိုင်ပါ။")
                }
            } else if (destinationFile != null) {
                MediaScannerConnection.scanFile(context, arrayOf(destinationFile!!.absolutePath), arrayOf("video/mp4"), null)
            }
            onProgress(Progress("complete", copiedBytes, totalBytes, null))
            return SavedVideo(destinationUri!!, copiedBytes)
        } catch (error: Exception) {
            destinationUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            destinationFile?.delete()
            throw error
        }
    }

    private fun nativeDirectoryArgument(directory: File): String {
        val path = directory.canonicalPath
        return if (path.endsWith(File.separator)) path else path + File.separator
    }

    private fun sanitizeFileName(requestedName: String): String = requestedName
        .replace(Regex("[\\\\/:*?\"<>|\u0000-\u001f]"), "_")
        .replace("..", "_")
        .trim()
        .take(180)
        .ifBlank { "video.mp4" }

    private fun uniqueDestination(directory: File, requestedName: String): File {
        val normalized = sanitizeFileName(requestedName)
        val dot = normalized.lastIndexOf('.')
        val stem = if (dot > 0) normalized.substring(0, dot) else normalized
        val extension = if (dot > 0) normalized.substring(dot) else ".mp4"
        var candidate = File(directory, normalized).canonicalFile
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, String.format(Locale.US, "%s_%d%s", stem, suffix++, extension)).canonicalFile
        }
        check(candidate.path.startsWith(directory.path + File.separator)) { "သိမ်းမည့် file name မမှန်ပါ။" }
        return candidate
    }
}
