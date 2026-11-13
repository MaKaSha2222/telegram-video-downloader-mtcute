package space.manus.tgvidapp.t145521605804137

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
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
    private var pendingLinks: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BG
        window.navigationBarColor = Color.BG
        window.decorView.systemUiVisibility = 0
        pendingLinks = savedInstanceState?.getStringArrayList(KEY_PENDING_LINKS)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingLinks?.let { outState.putStringArrayList(KEY_PENDING_LINKS, it) }
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Permission results are handled here to support the minimum SDK")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS && requestCode != REQUEST_WRITE_STORAGE) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            enqueuePendingLinks()
            return
        }
        stageText.visibility = View.GONE
        progressBar.visibility = View.GONE
        detailText.visibility = View.GONE
        val notificationPermission = requestCode == REQUEST_NOTIFICATIONS
        val permission = if (notificationPermission) Manifest.permission.POST_NOTIFICATIONS else Manifest.permission.WRITE_EXTERNAL_STORAGE
        val canAskAgain = shouldShowRequestPermissionRationale(permission)
        val message = if (notificationPermission) {
            if (canAskAgain) "Download notification ခွင့်ပြုချက် မရသေးပါ။ Notification ခွင့်ပြုပြီးမှ download ကို စတင်နိုင်ပါတယ်။"
            else "Download notification ပိတ်ထားပါတယ်။ App settings ထဲက Notifications ကို ဖွင့်ပြီး ထပ်ကြိုးစားပါ။"
        } else {
            if (canAskAgain) "အဟောင်း Android တွင် Download/TGMovies သို့သိမ်းရန် storage ခွင့်ပြုချက် လိုပါတယ်။"
            else "Storage ခွင့်ပြုချက် ပိတ်ထားပါတယ်။ App settings ထဲက Files and media ခွင့်ပြုချက်ကို ဖွင့်ပါ။"
        }
        showError(ErrorDisplay.format(AppFailure(
            errorType = if (notificationPermission) "NotificationPermissionDeniedError" else "StoragePermissionDeniedError",
            errorMessage = message,
            details = "Permission: $permission",
        ), credentials))
    }

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BG); isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(32))
        }
        scroll.addView(content)

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val headingColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headingColumn.addView(text("TELEGRAM VIDEO TOOL", 11, Color.ACCENT, true).apply { letterSpacing = 0.12f })
        headingColumn.addView(text("Telegram ဗီဒီယို", 26, Color.WHITE, true).apply { setPadding(0, dp(5), 0, 0) })
        header.addView(headingColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(button("စာရင်း", Color.SECONDARY, Color.WHITE) { startActivity(Intent(this, DownloadsActivity::class.java)) })
        header.addView(button("ဆက်တင်", Color.SECONDARY, Color.WHITE) { startActivity(Intent(this, SettingsActivity::class.java)) }.apply {
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)).apply { leftMargin = dp(6) }
        })
        content.addView(header)
        content.addView(text("အများမြင်နိုင်တဲ့ Telegram channel video post link များကို တစ်ကြောင်းစီ ထည့်ပါ။", 14, Color.MUTED, false).apply {
            setPadding(0, dp(17), 0, dp(22))
        })

        val serverCard = card()
        statusDot = View(this).apply { background = circle(0xff687780.toInt()) }
        serverCard.addView(statusDot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(12) })
        val serverColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        serverColumn.addView(text("App အတွင်း Bot API Server", 14, Color.WHITE, true))
        serverState = text("စစ်ဆေးနေပါတယ်…", 12, Color.MUTED, false).apply { setPadding(0, dp(4), 0, 0) }
        serverColumn.addView(serverState)
        serverCard.addView(serverColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(serverCard)

        val inputCard = card().apply { orientation = LinearLayout.VERTICAL }
        inputCard.addView(text("Telegram post link များ", 13, Color.WHITE, true))
        linkInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_VARIATION_URI
            minLines = 3
            maxLines = 6
            gravity = Gravity.TOP or Gravity.START
            setTextColor(Color.WHITE)
            setHintTextColor(0xff687780.toInt())
            hint = "https://t.me/channelname/123\nhttps://t.me/channelname/124"
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(0xff0b1116.toInt(), 0xff2d3d43.toInt(), 1, 13)
        }
        inputCard.addView(linkInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
        inputCard.addView(text("Parallel limit ကို Settings မှာ 1–4 အထိ ပြောင်းနိုင်ပါတယ်။", 11, Color.MUTED, false).apply {
            setPadding(0, dp(10), 0, 0)
        })
        content.addView(inputCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })

        downloadButton = button("Queue ထဲသို့ ထည့်ပြီး download လုပ်မည်", Color.ACCENT, Color.DARK) { startDownload() }
        content.addView(downloadButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(16) })

        stageText = text("", 13, Color.WHITE, true).apply { visibility = View.GONE; setPadding(0, dp(18), 0, 0) }
        detailText = text("", 11, Color.MUTED, false).apply { visibility = View.GONE; setPadding(0, dp(6), 0, 0) }
        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.ACCENT)
            visibility = View.GONE
        }
        content.addView(stageText)
        content.addView(progressBar, LinearLayout.LayoutParams(dp(36), dp(36)).apply { topMargin = dp(12) })
        content.addView(detailText)

        errorText = text("", 12, Color.ERROR, false).apply {
            visibility = View.GONE
            setTextIsSelectable(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(0xff291916.toInt(), 0xff704035.toInt(), 1, 14)
        }
        content.addView(errorText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        resultText = text("", 12, Color.SUCCESS, false).apply {
            visibility = View.GONE
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(0xff102219.toInt(), 0xff28533f.toInt(), 1, 16)
        }
        content.addView(resultText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) })
        content.addView(text("API credentials များကို ဒီစက်မှာ encrypted သိမ်းဆည်းပါတယ်။ Server က byte callback မပေးတဲ့ remote-fetch အဆင့်မှာ indeterminate အဖြစ်ပြပြီး Download/TGMovies သို့ ဖိုင်ကူးနေချိန်မှသာ တိကျသော percent ကိုပြပါမယ်။", 11, 0xff718087.toInt(), false).apply {
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
            else -> "လိုအပ်သည့်အခါ စတင်ပါမယ်"
        }
        serverState.setTextColor(if (running) Color.SUCCESS else Color.MUTED)
        statusDot.background = circle(if (running) Color.SUCCESS else 0xff687780.toInt())
        stateError?.let { showError(ErrorDisplay.format(it, credentials)) }
    }

    private fun startDownload() {
        errorText.visibility = View.GONE
        resultText.visibility = View.GONE
        stageText.visibility = View.GONE
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
        val links = linkInput.text.toString().lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (links.isEmpty()) {
            showError(ErrorDisplay.format(AppFailure("TelegramLinkMissingError", errorMessage = "Telegram video post link တစ်ခု သို့မဟုတ် တစ်ခုထက်ပို ထည့်ပါ။"), saved))
            return
        }
        pendingLinks = ArrayList(links)
        enqueuePendingLinks()
    }

    private fun enqueuePendingLinks() {
        val links = pendingLinks ?: return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            stageText.visibility = View.VISIBLE
            stageText.text = "Download notification ခွင့်ပြုချက်ကို တောင်းနေပါတယ်…"
            detailText.visibility = View.VISIBLE
            detailText.text = "ခွင့်ပြုပါက ဖိုင်များကို background မှာ ဆက်လက်လုပ်ပါမယ်။"
            progressBar.visibility = View.VISIBLE
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            showError(ErrorDisplay.format(AppFailure(
                "NotificationsDisabledError",
                errorMessage = "App notification ကို Android settings ထဲမှာ ပိတ်ထားပါတယ်။ Download မစမီ Telegram Video Downloader အတွက် Notifications ကို ဖွင့်ပါ။",
            ), credentials))
            return
        }
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            stageText.visibility = View.VISIBLE
            stageText.text = "Download folder အတွက် storage ခွင့်ပြုချက်ကို တောင်းနေပါတယ်…"
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_STORAGE)
            return
        }
        try {
            LocalBotApiForegroundService.enqueue(this, links)
            linkInput.text?.clear()
            pendingLinks = null
            stageText.visibility = View.VISIBLE
            stageText.text = "${links.size} ခုကို download queue ထဲ ထည့်ပြီးပါပြီ။"
            detailText.visibility = View.VISIBLE
            detailText.text = "ဖိုင်များကို Download/TGMovies ထဲမှာ သိမ်းပါမယ်။"
            progressBar.visibility = View.GONE
            resultText.text = "Queue ထဲသို့ ${links.size} ခု ထည့်ပြီးပါပြီ။ Download list မှာ status နှင့် progress ကိုကြည့်ပါ။"
            resultText.visibility = View.VISIBLE
            startActivity(Intent(this, DownloadsActivity::class.java))
        } catch (error: Exception) {
            showError(ErrorDisplay.format(error, credentials))
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
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
    }

    private fun button(label: String, fill: Int, foreground: Int, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 13f
        setTextColor(foreground)
        isAllCaps = false
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(fill, fill, 0, 15)
        setPadding(dp(10), 0, dp(10), 0)
        minHeight = dp(44)
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, stroke: Int, strokeWidth: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
    }

    private fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private object Color {
        const val BG = 0xff0b0f14.toInt()
        const val WHITE = 0xfff1f6f7.toInt()
        const val MUTED = 0xffa5b3ba.toInt()
        const val ACCENT = 0xff51d4c8.toInt()
        const val DARK = 0xff071012.toInt()
        const val SECONDARY = 0xff182329.toInt()
        const val ERROR = 0xffff9677.toInt()
        const val SUCCESS = 0xff59d79a.toInt()
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 3104
        private const val REQUEST_WRITE_STORAGE = 3105
        private const val KEY_PENDING_LINKS = "pending_links"
    }
}
