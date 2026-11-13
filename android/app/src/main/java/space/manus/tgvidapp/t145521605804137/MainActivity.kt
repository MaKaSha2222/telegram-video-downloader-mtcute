package space.manus.tgvidapp.t145521605804137

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var linkInput: EditText
    private lateinit var serverState: TextView
    private lateinit var statusDot: View
    private lateinit var progressBar: ProgressBar
    private lateinit var stageText: TextView
    private lateinit var detailText: TextView
    private lateinit var errorText: TextView
    private lateinit var resultText: TextView
    private lateinit var downloadButton: Button
    private var credentials: TelegramCredentials? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 15, 20)
        window.navigationBarColor = Color.rgb(11, 15, 20)
        window.decorView.systemUiVisibility = 0
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(11, 15, 20))
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(32))
        }
        scroll.addView(content)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headingColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headingColumn.addView(text("TELEGRAM VIDEO TOOL", 11, ACCENT, true).apply { letterSpacing = 0.12f })
        headingColumn.addView(text("Telegram ဗီဒီယို", 28, WHITE, true).apply { setPadding(0, dp(5), 0, 0) })
        header.addView(headingColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(button("ဆက်တင်", SECONDARY, WHITE) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        content.addView(header)
        content.addView(text("အများမြင်နိုင်တဲ့ Telegram channel video post link ကို ထည့်ပါ။", 14, MUTED, false).apply {
            setPadding(0, dp(17), 0, dp(22))
        })

        val serverCard = card()
        statusDot = View(this).apply { background = circle(0xff687780.toInt()) }
        serverCard.addView(statusDot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(12) })
        val serverColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        serverColumn.addView(text("App အတွင်း Bot API Server", 14, WHITE, true))
        serverState = text("စစ်ဆေးနေပါတယ်…", 12, MUTED, false).apply { setPadding(0, dp(4), 0, 0) }
        serverColumn.addView(serverState)
        serverCard.addView(serverColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(serverCard)

        val inputCard = card().apply { orientation = LinearLayout.VERTICAL }
        inputCard.addView(text("Telegram post link", 13, WHITE, true))
        linkInput = EditText(this).apply {
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(WHITE)
            setHintTextColor(0xff687780.toInt())
            hint = "https://t.me/channelname/123"
            textSize = 14f
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(0xff0b1116.toInt(), 0xff2d3d43.toInt(), 1, 13)
        }
        inputCard.addView(linkInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply {
            topMargin = dp(12)
        })
        inputCard.addView(text("အများမြင်နိုင်တဲ့ channel ရဲ့ video post link ကိုသာ ထည့်ပါ။", 11, MUTED, false).apply {
            setPadding(0, dp(10), 0, 0)
        })
        val inputParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        inputParams.topMargin = dp(4)
        content.addView(inputCard, inputParams)

        downloadButton = button("ဗီဒီယို ဒေါင်းလုဒ်လုပ်မည်", ACCENT, 0xff071012.toInt()) { startDownload() }
        content.addView(downloadButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply {
            topMargin = dp(16)
        })

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xff27343a.toInt())
            visibility = View.GONE
        }
        stageText = text("", 13, WHITE, true).apply { visibility = View.GONE; setPadding(0, dp(18), 0, 0) }
        detailText = text("", 11, MUTED, false).apply { visibility = View.GONE; setPadding(0, dp(6), 0, 0) }
        content.addView(stageText)
        content.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(10) })
        content.addView(detailText)

        errorText = text("", 12, ERROR, false).apply {
            visibility = View.GONE
            setTextIsSelectable(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(0xff291916.toInt(), 0xff704035.toInt(), 1, 14)
        }
        content.addView(errorText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })
        resultText = text("", 12, SUCCESS, false).apply {
            visibility = View.GONE
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(0xff102219.toInt(), 0xff28533f.toInt(), 1, 16)
        }
        content.addView(resultText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
        })
        content.addView(text("API ID, API Hash နဲ့ Bot Token ကို ဒီစက်မှာပဲ encrypted သိမ်းဆည်းပါတယ်။ Telethon က file_id ရယူရန်သာ အသုံးပြုပြီး ဗီဒီယိုကို Bot API Server က ဒေါင်းလုဒ်လုပ်ပါတယ်။", 11, 0xff718087.toInt(), false).apply {
            setPadding(0, dp(24), 0, 0)
        })
        return scroll
    }

    private fun refreshState() {
        val credentialsResult = runCatching { CredentialsStore.load(this) }
        credentials = credentialsResult.getOrNull()
        val runningResult = runCatching { LocalBotApiBridge.isRunning() }
        val running = runningResult.getOrDefault(false)
        val stateError = credentialsResult.exceptionOrNull() ?: runningResult.exceptionOrNull()
        serverState.text = when {
            stateError != null -> "စစ်ဆေးမှု မအောင်မြင်ပါ (${stateError.javaClass.simpleName})"
            credentials == null -> "ဆက်တင်များ မထည့်ရသေးပါ။"
            running -> "အလုပ်လုပ်နေပါတယ်"
            else -> "မစတင်ရသေးပါ"
        }
        serverState.setTextColor(if (running) SUCCESS else MUTED)
        statusDot.background = circle(if (running) SUCCESS else 0xff687780.toInt())
        stateError?.let { showError(ErrorDisplay.format(it, credentials)) }
    }

    private fun startDownload() {
        errorText.visibility = View.GONE
        resultText.visibility = View.GONE
        progressBar.visibility = View.GONE
        detailText.visibility = View.GONE
        val saved = try {
            CredentialsStore.load(this)
        } catch (error: Exception) {
            showError(ErrorDisplay.format(error))
            return
        }
        credentials = saved
        if (saved == null) {
            showError(ErrorDisplay.format(AppFailure(
                errorType = "CredentialsNotConfiguredError",
                errorMessage = "အရင်ဆုံး ဆက်တင်ထဲမှာ API ID, API Hash နဲ့ Bot Token ကို သိမ်းပါ။",
            )))
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val link = linkInput.text?.toString()?.trim().orEmpty()
        if (link.isBlank()) {
            showError(ErrorDisplay.format(AppFailure("TelegramLinkMissingError", errorMessage = "Telegram video link ကို ထည့်ပါ."), saved))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            showError(ErrorDisplay.format(AppFailure(
                errorType = "NotificationPermissionRequiredError",
                errorMessage = "Download notification ခွင့်ပြုချက်ကို ရွေးပြီး download ကို ထပ်နှိပ်ပါ။",
                details = "Permission: ${Manifest.permission.POST_NOTIFICATIONS}",
            ), saved))
            return
        }

        downloadButton.isEnabled = false
        downloadButton.alpha = 0.7f
        stageText.visibility = View.VISIBLE
        stageText.text = "စတင်နေပါတယ်…"
        detailText.visibility = View.VISIBLE
        detailText.text = "အမှန်တကယ် တိုင်းတာချက် မရှိသေးလို့ ရာခိုင်နှုန်း မပြပါ။"
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

        worker.execute {
            try {
                runOnUiThread { stageText.text = "App အတွင်း Bot API Server ကို စတင်နေပါတယ်…" }
                LocalBotApiBridge.startServer(this, saved)
                runOnUiThread { stageText.text = "Telethon နဲ့ post ရဲ့ video file_id ကို စစ်ဆေးနေပါတယ်…" }
                val video = TelegramFileResolver.resolve(link, saved)
                runOnUiThread { stageText.text = "Bot API Server က ဗီဒီယိုဖိုင်ကို ရယူနေပါတယ်…" }
                val result = LocalBotApiBridge.downloadVideo(this, saved.botToken, video, "telegram_video.mp4") { progress ->
                    runOnUiThread { renderProgress(progress) }
                }
                runOnUiThread {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 100
                    stageText.text = "Download ပြီးပါပြီ"
                    detailText.text = "${formatBytes(result.sizeBytes)} သိမ်းပြီးပါပြီ။"
                    resultText.text = "Video သိမ်းပြီးပါပြီ\n${result.uri.path ?: result.uri}"
                    resultText.visibility = View.VISIBLE
                    refreshState()
                }
            } catch (error: Throwable) {
                if (error is VirtualMachineError || error is ThreadDeath) throw error
                val safe = ErrorDisplay.format(error, saved)
                runOnUiThread {
                    stageText.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    detailText.visibility = View.GONE
                    showError(safe)
                    refreshState()
                }
            } finally {
                runOnUiThread {
                    downloadButton.isEnabled = true
                    downloadButton.alpha = 1f
                }
            }
        }
    }

    private fun renderProgress(progress: LocalBotApiBridge.Progress) {
        progressBar.visibility = View.VISIBLE
        stageText.visibility = View.VISIBLE
        detailText.visibility = View.VISIBLE
        when (progress.phase) {
            "requesting" -> {
                progressBar.isIndeterminate = true
                stageText.text = "Bot API Server က ဗီဒီယိုဖိုင်ကို ရယူနေပါတယ်…"
                detailText.text = "Server ရဲ့ getFile တုံ့ပြန်ချက်ကို စောင့်နေပါတယ်။"
            }
            "copying" -> {
                progressBar.isIndeterminate = false
                val total = progress.totalBytes
                if (total != null && total > 0) {
                    val percent = ((progress.copiedBytes * 100L) / total).coerceIn(0, 100).toInt()
                    progressBar.progress = percent
                    val rate = progress.speedBytesPerSecond?.takeIf { it > 0 }?.let { " · ${formatRate(it)}/s" }.orEmpty()
                    detailText.text = "$percent% · ${formatBytes(progress.copiedBytes)} / ${formatBytes(total)}$rate"
                } else {
                    detailText.text = "${formatBytes(progress.copiedBytes)} ကူးယူပြီးပါပြီ။ စုစုပေါင်းအရွယ်အစားကို မသိရသေးပါ။"
                }
            }
            "complete" -> {
                progressBar.isIndeterminate = false
                progressBar.progress = 100
                stageText.text = "ဖိုင်သိမ်းဆည်းနေပါတယ်…"
                detailText.text = "${formatBytes(progress.copiedBytes)}"
            }
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(0xff121a20.toInt(), 0xff27343a.toInt(), 1, 18)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = dp(16)
        layoutParams = params
    }

    private fun button(label: String, fill: Int, foreground: Int, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 14f
        setTextColor(foreground)
        isAllCaps = false
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(fill, fill, 0, 16)
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, stroke: Int, strokeWidth: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun formatRate(bytes: Long): String = formatBytes(bytes)

    companion object {
        private const val REQUEST_NOTIFICATIONS = 3104
        private const val ACCENT = 0xff51d4c8.toInt()
        private const val WHITE = 0xfff1f6f7.toInt()
        private const val MUTED = 0xffa5b3ba.toInt()
        private const val SECONDARY = 0xff182329.toInt()
        private const val ERROR = 0xffff9677.toInt()
        private const val SUCCESS = 0xff59d79a.toInt()
    }
}
