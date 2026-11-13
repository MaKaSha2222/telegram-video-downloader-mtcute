package space.manus.tgvidapp.t145521605804137

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class DownloadsActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var summary: TextView
    private lateinit var listContainer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            renderItems()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        setContentView(buildScreen())
        LocalBotApiForegroundService.resume(this)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun buildScreen(): View {
        scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(32))
        }
        scroll.addView(content)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(button("← နောက်သို့", SECONDARY, WHITE) { finish() })
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(button("ထပ်ထည့်ရန်", ACCENT, DARK) { startActivity(Intent(this, MainActivity::class.java)) })
        content.addView(top)
        content.addView(text("DOWNLOADS", 11, ACCENT, true).apply { setPadding(0, dp(24), 0, dp(4)) })
        content.addView(text("ဒေါင်းလုဒ်စာရင်း", 28, WHITE, true))
        summary = text("စာရင်းကို ဖတ်နေပါတယ်…", 13, MUTED, false).apply { setPadding(0, dp(8), 0, dp(14)) }
        content.addView(summary)
        content.addView(button("ပြီးဆုံးပြီးသားဖိုင်များကို စာရင်းမှဖယ်မည်", SECONDARY, WHITE) {
            val removed = DownloadQueueStore.clearCompleted(this)
            Toast.makeText(this, "$removed ခု ဖယ်ရှားပြီးပါပြီ", Toast.LENGTH_SHORT).show()
            renderItems()
        }.apply { gravity = Gravity.CENTER })
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
        })
        return scroll
    }

    private fun renderItems() {
        if (!::listContainer.isInitialized) return
        val scrollPosition = scroll.scrollY
        val items = runCatching { DownloadQueueStore.list(this) }.getOrDefault(emptyList())
        val active = items.count { DownloadStatus.isActive(it.status) }
        val queued = items.count { it.status == DownloadStatus.QUEUED }
        val completed = items.count { it.status == DownloadStatus.COMPLETED }
        val failed = items.count { it.status == DownloadStatus.FAILED }
        summary.text = "လုပ်ဆောင်နေ $active · စောင့်နေ $queued · ပြီးဆုံး $completed · အမှား $failed"
        listContainer.removeAllViews()
        if (items.isEmpty()) {
            listContainer.addView(card().apply {
                orientation = LinearLayout.VERTICAL
                addView(text("စာရင်းထဲမှာ download မရှိသေးပါ။", 15, WHITE, true))
                addView(text("Main screen မှာ Telegram link များကို တစ်ကြောင်းစီ ထည့်နိုင်ပါတယ်။", 12, MUTED, false).apply { setPadding(0, dp(8), 0, 0) })
            })
        } else {
            items.forEach { item -> listContainer.addView(buildItemCard(item)) }
        }
        scroll.post { scroll.scrollTo(0, scrollPosition) }
    }

    private fun buildItemCard(item: DownloadItem): View {
        val card = card().apply { orientation = LinearLayout.VERTICAL }
        val title = item.link.trimEnd('/').substringAfterLast('/').ifBlank { item.link }.take(64)
        card.addView(text(title, 15, WHITE, true))
        card.addView(text(item.link, 11, MUTED, false).apply {
            setPadding(0, dp(5), 0, dp(7))
            maxLines = 2
            setTextIsSelectable(true)
        })
        val stateLine = when (item.status) {
            DownloadStatus.QUEUED -> "စောင့်နေပါတယ်"
            DownloadStatus.RESOLVING -> "Telegram post ကိုစစ်နေပါတယ်"
            DownloadStatus.DOWNLOADING -> item.phase
            DownloadStatus.RETRYING -> item.phase
            DownloadStatus.COMPLETED -> item.phase
            DownloadStatus.FAILED -> item.phase
            DownloadStatus.CANCELED -> "ရပ်တန့်ထားပါတယ်"
            else -> item.phase
        }
        card.addView(text(stateLine, 12, statusColor(item.status), true))
        if (DownloadStatus.isActive(item.status) || item.status == DownloadStatus.COMPLETED) {
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progressTintList = android.content.res.ColorStateList.valueOf(ACCENT)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xff27343a.toInt())
                isIndeterminate = item.progress < 0 && DownloadStatus.isActive(item.status)
                if (!isIndeterminate) progress = item.progress.coerceAtLeast(0)
            }
            card.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply { topMargin = dp(10) })
            val progressText = if (item.progress >= 0 && item.totalBytes != null) {
                "${item.progress}% · ${formatBytes(item.copiedBytes)} / ${formatBytes(item.totalBytes)}${if (item.status == DownloadStatus.DOWNLOADING) " · ဖိုင်ကို public folder သို့ သိမ်းခြင်း" else ""}"
            } else if (item.status == DownloadStatus.COMPLETED) {
                "100% · ${formatBytes(item.copiedBytes)}"
            } else {
                "Remote fetch stage · Telegram server က % ပေးမထားသေးပါ"
            }
            card.addView(text(progressText, 11, MUTED, false).apply { setPadding(0, dp(6), 0, 0) })
        }
        if (item.attempts > 0) {
            card.addView(text("ကြိုးစားမှု ${item.attempts}/4", 11, MUTED, false).apply { setPadding(0, dp(5), 0, 0) })
        }
        if (item.error.isNotBlank() && item.status == DownloadStatus.FAILED) {
            card.addView(text(item.error, 11, ERROR, false).apply {
                setPadding(0, dp(8), 0, 0)
                setTextIsSelectable(true)
                maxLines = 8
            })
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        when {
            item.status == DownloadStatus.FAILED -> {
                actions.addView(button("ပြန်စမ်း", ACCENT, DARK) {
                    LocalBotApiForegroundService.retry(this@DownloadsActivity, item.id)
                    renderItems()
                })
                actions.addView(button("ဖယ်ရှား", SECONDARY, WHITE) {
                    DownloadQueueStore.remove(this@DownloadsActivity, item.id)
                    renderItems()
                }.apply { setPadding(dp(9), 0, dp(9), 0) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { leftMargin = dp(8) })
            }
            item.status == DownloadStatus.QUEUED -> actions.addView(button("စောင့်စာရင်းမှဖယ်", SECONDARY, WHITE) {
                LocalBotApiForegroundService.cancelQueued(this@DownloadsActivity, item.id)
                renderItems()
            })
            item.status == DownloadStatus.COMPLETED -> {
                actions.addView(button("ဗီဒီယိုဖွင့်ရန်", ACCENT, DARK) { openVideo(item) })
                actions.addView(button("စာရင်းမှဖယ်", SECONDARY, WHITE) {
                    DownloadQueueStore.remove(this@DownloadsActivity, item.id)
                    renderItems()
                }.apply { setPadding(dp(9), 0, dp(9), 0) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { leftMargin = dp(8) })
            }
        }
        if (actions.childCount > 0) {
            card.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        }
        return card
    }

    private fun openVideo(item: DownloadItem) {
        val uri = runCatching { Uri.parse(item.resultUri) }.getOrNull()
        if (uri == null || item.resultUri.isBlank()) {
            Toast.makeText(this, "သိမ်းထားသော video URI ကို မတွေ့ပါ။ Download/TGMovies ကို စစ်ပါ။", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "Video ဖွင့်မရပါ။ File manager မှ Download/TGMovies ထဲကနေ ဖွင့်ပါ။", Toast.LENGTH_LONG).show()
        }
    }

    private fun statusColor(status: String): Int = when (status) {
        DownloadStatus.COMPLETED -> SUCCESS
        DownloadStatus.FAILED -> ERROR
        DownloadStatus.QUEUED -> MUTED
        else -> ACCENT
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(15), dp(15), dp(15))
        background = rounded(0xff121a20.toInt(), 0xff27343a.toInt(), 1, 16)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
    }

    private fun button(label: String, fill: Int, foreground: Int, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        setTextColor(foreground)
        isAllCaps = false
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(fill, fill, 0, 14)
        setPadding(dp(10), 0, dp(10), 0)
        minHeight = dp(42)
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(fill: Int, stroke: Int, strokeWidth: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val BG = 0xff0b0f14.toInt()
        private const val WHITE = 0xfff1f6f7.toInt()
        private const val MUTED = 0xffa5b3ba.toInt()
        private const val ACCENT = 0xff51d4c8.toInt()
        private const val DARK = 0xff071012.toInt()
        private const val SECONDARY = 0xff182329.toInt()
        private const val SUCCESS = 0xff59d79a.toInt()
        private const val ERROR = 0xffff9677.toInt()
        private const val REFRESH_MS = 1_000L
    }
}
