package space.manus.tgvidapp.t145521605804137

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class LocalBotApiForegroundService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scheduledIds = mutableSetOf<String>()
    private val lastProgressPersistedAt = ConcurrentHashMap<String, Long>()
    private val lastProgressPercent = ConcurrentHashMap<String, Int>()
    private val threadSequence = AtomicInteger(0)
    private val executor by lazy {
        Executors.newCachedThreadPool(ThreadFactory { task ->
            Thread(task, "tg-download-${threadSequence.incrementAndGet()}").apply { isDaemon = false }
        })
    }
    private var isStopping = false
    @Volatile private var stopRequested = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        DownloadQueueStore.recoverInterrupted(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isStopping = false
        stopRequested = false
        startForegroundNow(buildNotification("Telegram downloads", "Download queue ကို ပြင်ဆင်နေပါတယ်…", null, true, ongoing = true))
        when (intent?.action) {
            ACTION_ENQUEUE -> {
                val links = intent.getStringArrayListExtra(EXTRA_LINKS).orEmpty().map { it.trim() }.filter { it.isNotBlank() }
                if (links.isNotEmpty()) DownloadQueueStore.enqueue(this, links)
            }
            ACTION_RETRY -> intent.getStringExtra(EXTRA_ID)?.let { DownloadQueueStore.retry(this, it) }
            ACTION_CANCEL_QUEUED -> intent.getStringExtra(EXTRA_ID)?.let { id ->
                DownloadQueueStore.get(this, id)?.takeIf { it.status == DownloadStatus.QUEUED }?.let { DownloadQueueStore.remove(this, id) }
            }
            ACTION_CLEAR_COMPLETED -> DownloadQueueStore.clearCompleted(this)
            ACTION_SETTINGS_CHANGED, ACTION_RESUME, null -> Unit
        }
        dispatchQueuedItems()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        stopRequested = true
        executor.shutdownNow()
        DownloadQueueStore.recoverInterrupted(applicationContext)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun dispatchQueuedItems() {
        if (isStopping) return
        val limit = DownloadSettings.parallelism(this)
        while (scheduledIds.size < limit) {
            val next = DownloadQueueStore.queued(this).firstOrNull { it.id !in scheduledIds } ?: break
            scheduledIds.add(next.id)
            DownloadQueueStore.update(this, next.id) {
                it.copy(status = DownloadStatus.RESOLVING, phase = "စတင်နေပါတယ်…", progress = -1, copiedBytes = 0L, totalBytes = null)
            }
            executor.execute {
                processItem(next)
                mainHandler.post {
                    scheduledIds.remove(next.id)
                    lastProgressPersistedAt.remove(next.id)
                    lastProgressPercent.remove(next.id)
                    updateNotification()
                    dispatchQueuedItems()
                }
            }
        }
        updateNotification()
        if (scheduledIds.isEmpty() && !DownloadQueueStore.hasPending(this)) finishWhenIdle()
    }

    private fun processItem(initialItem: DownloadItem) {
        val context = applicationContext
        var credentials: TelegramCredentials? = null
        var lastAttempt = 0
        try {
            credentials = CredentialsStore.load(context)
                ?: throw AppFailure("CredentialsNotConfiguredError", errorMessage = "API ဆက်တင်များ မရှိပါ။ Settings ထဲမှာ အရင်ဖြည့်ပါ။")
            LocalBotApiBridge.startServer(context, credentials)

            for (attempt in 1..MAX_ATTEMPTS) {
                lastAttempt = attempt
                try {
                    setState(initialItem.id, DownloadStatus.RESOLVING, "Telegram post မှ video file_id ကို ရှာနေပါတယ်…", attempt, -1, 0L, null)
                    val fileId = TelegramFileResolver.resolve(initialItem.link, credentials)

                    setState(
                        initialItem.id,
                        DownloadStatus.DOWNLOADING,
                        "Telegram Bot API Server က file ကို ရယူနေပါတယ်… (server ကရာခိုင်နှုန်းမပေးသေးပါ)",
                        attempt,
                        -1,
                        0L,
                        null,
                    )
                    val outputName = makeOutputName(initialItem)
                    val saved = LocalBotApiBridge.downloadVideo(
                        context = context,
                        botToken = credentials.botToken,
                        fileId = fileId,
                        requestedFileName = outputName,
                    ) { progress ->
                        val copyStage = progress.phase == "copying" || progress.phase == "complete"
                        val total = progress.totalBytes?.takeIf { it > 0L }
                        val percent = if (copyStage && total != null) {
                            ((progress.copiedBytes * 100L) / total).coerceIn(0L, 100L).toInt()
                        } else -1
                        val now = System.currentTimeMillis()
                        val changedPercent = percent >= 0 && lastProgressPercent.put(initialItem.id, percent) != percent
                        val elapsed = now - (lastProgressPersistedAt[initialItem.id] ?: 0L)
                        if (changedPercent || elapsed >= PROGRESS_PERSIST_INTERVAL_MS || progress.phase == "complete") {
                            lastProgressPersistedAt[initialItem.id] = now
                            setState(
                                initialItem.id,
                                DownloadStatus.DOWNLOADING,
                                if (copyStage) "Download/TGMovies သို့ ဖိုင်ကို သိမ်းနေပါတယ်…" else "Telegram server က ဖိုင်ကို ရယူနေပါတယ်…",
                                attempt,
                                percent,
                                progress.copiedBytes,
                                total,
                            )
                        }
                    }
                    setState(
                        initialItem.id,
                        DownloadStatus.COMPLETED,
                        "ပြီးပါပြီ · ${formatBytes(saved.sizeBytes)} · Download/TGMovies",
                        attempt,
                        100,
                        saved.sizeBytes,
                        saved.sizeBytes,
                        resultUri = saved.uri.toString(),
                    )
                    return
                } catch (error: Exception) {
                    val shouldRetry = attempt < MAX_ATTEMPTS && isRetryable(error)
                    if (!shouldRetry) throw error
                    val pause = retryDelayMillis(error, attempt)
                    setState(
                        initialItem.id,
                        DownloadStatus.RETRYING,
                        "ယာယီ network အမှားဖြစ်၍ ${attempt + 1}/$MAX_ATTEMPTS အကြိမ် ပြန်ကြိုးစားမည် (${pause / 1000}s)…",
                        attempt,
                        -1,
                        0L,
                        null,
                        error = safeError(error, credentials),
                    )
                    Thread.sleep(pause)
                }
            }
        } catch (error: Exception) {
            setState(
                initialItem.id,
                DownloadStatus.FAILED,
                "မအောင်မြင်ပါ · Retry ကိုနှိပ်၍ ပြန်စမ်းနိုင်ပါတယ်",
                lastAttempt,
                -1,
                0L,
                null,
                error = safeError(error, credentials),
            )
        }
    }

    private fun setState(
        id: String,
        status: String,
        phase: String,
        attempts: Int,
        progress: Int,
        bytes: Long,
        total: Long?,
        error: String = "",
        resultUri: String? = null,
    ) {
        if (stopRequested) return
        DownloadQueueStore.update(this, id) { old ->
            old.copy(
                status = status,
                phase = phase,
                attempts = attempts,
                progress = progress,
                copiedBytes = bytes,
                totalBytes = total,
                error = error,
                resultUri = resultUri ?: old.resultUri,
            )
        }
        mainHandler.post { updateNotification() }
    }

    private fun isRetryable(error: Throwable): Boolean {
        if (error is AppFailure) {
            val code = error.errorCode?.toIntOrNull()
            if (code == 429 || (code != null && code in 500..599)) return true
        }
        val failure = error as? AppFailure
        val classification = listOfNotNull(failure?.errorType, failure?.errorMessage, failure?.details).joinToString(" ")
        if (classification.contains("FloodWait", true) || classification.contains("SocketTimeout", true) ||
            classification.contains("timed out", true) || classification.contains("timeout", true) ||
            classification.contains("connection reset", true) || classification.contains("file reference expired", true) ||
            classification.contains("file_id_invalid", true)
        ) return true
        var cause: Throwable? = error
        val seen = mutableSetOf<Throwable>()
        while (cause != null && seen.add(cause)) {
            if (cause is SocketTimeoutException || cause is ConnectException || cause is SocketException ||
                cause is UnknownHostException || cause is java.io.EOFException
            ) return true
            if (cause is IOException) {
                val message = cause.message.orEmpty().lowercase()
                if (listOf("connection reset", "connection aborted", "temporarily unavailable", "unexpected end", "timed out").any(message::contains)) return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun retryDelayMillis(error: Throwable, attempt: Int): Long {
        val primary = error as? AppFailure
        if (primary?.errorType?.contains("FloodWait", true) == true || primary?.errorCode == "429") {
            val seconds = Regex("(?:retry[_ ]after|seconds|wait of)\\D{0,16}(\\d+)", RegexOption.IGNORE_CASE)
                .find(primary.details.orEmpty() + " " + primary.errorMessage)?.groupValues?.getOrNull(1)?.toLongOrNull()
            if (seconds != null) return (seconds * 1000L).coerceIn(1_000L, MAX_RETRY_WAIT_MS)
        }
        val exponential = (BASE_RETRY_WAIT_MS * (1L shl (attempt - 1).coerceIn(0, 5))).coerceAtMost(MAX_RETRY_WAIT_MS)
        return (exponential + Random.nextLong(0L, 750L)).coerceAtMost(MAX_RETRY_WAIT_MS)
    }

    private fun safeError(error: Throwable, credentials: TelegramCredentials?): String =
        ErrorDisplay.format(error, credentials).take(MAX_ERROR_CHARS)

    private fun makeOutputName(item: DownloadItem): String {
        val messageId = Regex("/([0-9]+)(?:\\?.*)?$").find(item.link)?.groupValues?.getOrNull(1)
        val suffix = item.id.take(8)
        val base = messageId?.let { "telegram_$it" } ?: "telegram_video"
        return "${base}_$suffix.mp4"
    }

    private fun updateNotification() {
        val items = DownloadQueueStore.list(this)
        val active = items.filter { DownloadStatus.isActive(it.status) }
        val queued = items.count { it.status == DownloadStatus.QUEUED }
        val completed = items.count { it.status == DownloadStatus.COMPLETED }
        val failed = items.count { it.status == DownloadStatus.FAILED }
        val primary = active.firstOrNull()
        val line = when {
            primary != null -> buildString {
                append(primary.shortLabel)
                if (primary.progress >= 0) append(" · ${primary.progress}%") else append(" · ${primary.phase.take(60)}")
                if (queued > 0) append(" · +$queued queued")
            }
            queued > 0 -> "$queued download(s) in queue"
            else -> "ပြီးဆုံး $completed · မအောင်မြင် $failed"
        }
        val determinate = primary?.progress?.takeIf { it >= 0 }
        val notification = buildNotification(
            title = if (active.isNotEmpty() || queued > 0) "Telegram downloads · ${active.size} active / $queued queued" else "Telegram downloads finished",
            text = line,
            progress = determinate,
            indeterminate = primary != null && determinate == null,
            ongoing = active.isNotEmpty() || queued > 0,
        )
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification) }
    }

    private fun buildNotification(title: String, text: String, progress: Int?, indeterminate: Boolean, ongoing: Boolean): Notification {
        val openQueue = Intent(this, DownloadsActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openQueue,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_LOW)
        }
        if (progress != null) builder.setProgress(100, progress.coerceIn(0, 100), false)
        else if (indeterminate) builder.setProgress(0, 0, true)
        return builder.build()
    }

    private fun startForegroundNow(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun finishWhenIdle() {
        if (isStopping) return
        isStopping = true
        updateNotification()
        if (runCatching { LocalBotApiBridge.isRunning() }.getOrDefault(false)) runCatching { LocalBotApiBridge.stopServer() }
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Telegram downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ongoing Telegram video download status and progress"
                    setShowBadge(false)
                },
            )
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    companion object {
        private const val CHANNEL_ID = "telegram_downloads"
        private const val NOTIFICATION_ID = 19081
        private const val EXTRA_LINKS = "links"
        private const val EXTRA_ID = "download_id"
        private const val ACTION_ENQUEUE = "${BuildConfig.APPLICATION_ID}.action.ENQUEUE"
        private const val ACTION_RETRY = "${BuildConfig.APPLICATION_ID}.action.RETRY"
        private const val ACTION_CANCEL_QUEUED = "${BuildConfig.APPLICATION_ID}.action.CANCEL_QUEUED"
        private const val ACTION_CLEAR_COMPLETED = "${BuildConfig.APPLICATION_ID}.action.CLEAR_COMPLETED"
        private const val ACTION_SETTINGS_CHANGED = "${BuildConfig.APPLICATION_ID}.action.SETTINGS_CHANGED"
        private const val ACTION_RESUME = "${BuildConfig.APPLICATION_ID}.action.RESUME"
        private const val MAX_ATTEMPTS = 4
        private const val BASE_RETRY_WAIT_MS = 1_500L
        private const val MAX_RETRY_WAIT_MS = 5 * 60 * 1000L
        private const val PROGRESS_PERSIST_INTERVAL_MS = 350L
        private const val MAX_ERROR_CHARS = 1_500

        private fun start(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun enqueue(context: Context, links: List<String>) {
            start(context, Intent(context, LocalBotApiForegroundService::class.java).apply {
                action = ACTION_ENQUEUE
                putStringArrayListExtra(EXTRA_LINKS, ArrayList(links))
            })
        }

        fun retry(context: Context, id: String) {
            start(context, Intent(context, LocalBotApiForegroundService::class.java).apply {
                action = ACTION_RETRY
                putExtra(EXTRA_ID, id)
            })
        }

        fun cancelQueued(context: Context, id: String) {
            start(context, Intent(context, LocalBotApiForegroundService::class.java).apply {
                action = ACTION_CANCEL_QUEUED
                putExtra(EXTRA_ID, id)
            })
        }

        fun clearCompleted(context: Context) {
            start(context, Intent(context, LocalBotApiForegroundService::class.java).apply { action = ACTION_CLEAR_COMPLETED })
        }

        fun settingsChanged(context: Context) {
            if (!DownloadQueueStore.hasPending(context)) return
            start(context, Intent(context, LocalBotApiForegroundService::class.java).apply { action = ACTION_SETTINGS_CHANGED })
        }

        fun resume(context: Context) {
            if (!DownloadQueueStore.hasPending(context)) return
            start(context, Intent(context, LocalBotApiForegroundService::class.java).apply { action = ACTION_RESUME })
        }
    }
}
