package space.manus.tgvidapp.t145521605804137

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var apiIdInput: EditText
    private lateinit var apiHashInput: EditText
    private lateinit var botTokenInput: EditText
    private lateinit var parallelismSpinner: Spinner
    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        setContentView(buildScreen())
        loadSavedValues()
        configureParallelismSpinner()
    }

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(42), dp(24), dp(36))
        }
        scroll.addView(content)
        val back = TextView(this).apply {
            text = "← နောက်သို့"
            textSize = 15f
            setTextColor(MUTED)
            setPadding(0, dp(8), dp(14), dp(20))
            setOnClickListener { finish() }
        }
        content.addView(back)
        content.addView(text("APP ဆက်တင်များ", 11, ACCENT, true))
        content.addView(text("Telegram API", 31, WHITE, true).apply { setPadding(0, dp(7), 0, dp(9)) })
        apiIdInput = addField(content, "API ID", InputType.TYPE_CLASS_NUMBER)
        apiHashInput = addField(content, "API Hash", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        botTokenInput = addField(content, "Bot Token", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

        content.addView(text("Download ပြိုင်တူလုပ်ဆောင်မှု", 15, WHITE, true).apply { setPadding(0, dp(8), 0, dp(7)) })
        parallelismSpinner = Spinner(this, Spinner.MODE_DROPDOWN).apply {
            adapter = ParallelismAdapter(this@SettingsActivity, DownloadSettings.choices)
            setPopupBackgroundDrawable(solidBackground(0xff182329.toInt()))
            dropDownWidth = ViewGroup.LayoutParams.MATCH_PARENT
            dropDownVerticalOffset = dp(4)
            background = rounded(0xff121a20.toInt(), 14, 0xff27343a.toInt())
            setPadding(dp(12), 0, dp(12), 0)
        }
        content.addView(parallelismSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(18) })

        message = text("", 13, SUCCESS, false).apply {
            visibility = View.GONE
            setTextIsSelectable(true)
        }
        content.addView(message, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        val save = Button(this).apply {
            text = "API ဆက်တင်များကို encrypted သိမ်းမည်"
            textSize = 14f
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xff081011.toInt())
            background = rounded(ACCENT, 16)
            setOnClickListener { saveCredentials() }
        }
        content.addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(18) })
        return scroll
    }

    private fun addField(parent: LinearLayout, label: String, inputType: Int): EditText {
        parent.addView(text(label, 13, WHITE, true).apply { setPadding(0, 0, 0, dp(8)) })
        val edit = EditText(this).apply {
            this.inputType = inputType
            setSingleLine(true)
            setTextColor(WHITE)
            setHintTextColor(0xff687780.toInt())
            hint = ""
            textSize = 15f
            setPadding(dp(15), 0, dp(15), 0)
            background = rounded(0xff121a20.toInt(), 14, 0xff27343a.toInt())
        }
        parent.addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { bottomMargin = dp(16) })
        return edit
    }

    private fun loadSavedValues() {
        runCatching { CredentialsStore.load(this) }.onSuccess { saved ->
            if (saved != null) {
                apiIdInput.setText(saved.apiId.toString())
                apiHashInput.setText(saved.apiHash)
                botTokenInput.setText(saved.botToken)
            }
        }.onFailure { error -> showMessage(ErrorDisplay.format(error), true) }
        val current = DownloadSettings.parallelism(this)
        parallelismSpinner.setSelection(DownloadSettings.choices.indexOf(current).coerceAtLeast(0))
    }

    private fun configureParallelismSpinner() {
        parallelismSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = DownloadSettings.choices.getOrNull(position) ?: return
                if (selected == DownloadSettings.parallelism(this@SettingsActivity)) return
                DownloadSettings.setParallelism(this@SettingsActivity, selected)
                LocalBotApiForegroundService.settingsChanged(this@SettingsActivity)
                showMessage("Parallel download limit ကို $selected ဖိုင်အဖြစ် သိမ်းပြီးပါပြီ။", false)
            }
        }
    }

    private fun saveCredentials() {
        try {
            val id = apiIdInput.text.toString().trim().toIntOrNull()
                ?: throw IllegalArgumentException("API ID ကို နံပါတ်အဖြစ် ထည့်ပါ။")
            CredentialsStore.save(
                this,
                TelegramCredentials(
                    apiId = id,
                    apiHash = apiHashInput.text.toString().trim(),
                    botToken = botTokenInput.text.toString().trim(),
                ),
            )
            showMessage("ဆက်တင်တွေကို ဒီစက်မှာ encrypted သိမ်းပြီးပါပြီ။", false)
        } catch (error: Exception) {
            val enteredCredentials = TelegramCredentials(
                apiId = apiIdInput.text.toString().trim().toIntOrNull() ?: 0,
                apiHash = apiHashInput.text.toString().trim(),
                botToken = botTokenInput.text.toString().trim(),
            )
            showMessage(ErrorDisplay.format(error, enteredCredentials), true)
        }
    }

    private fun showMessage(value: String, isError: Boolean) {
        message.text = value
        message.setTextColor(if (isError) ERROR else SUCCESS)
        message.visibility = View.VISIBLE
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private class ParallelismAdapter(context: Context, choices: List<Int>) : ArrayAdapter<String>(
        context,
        android.R.layout.simple_spinner_item,
        choices.map { "$it ဖိုင် တစ်ပြိုင်နက်" },
    ) {
        private fun row(position: Int, dropdown: Boolean): TextView = TextView(context).apply {
            text = getItem(position).orEmpty()
            textSize = 15f
            setTextColor(WHITE)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(18, 0, 18, 0)
            minimumHeight = 54
            if (dropdown) background = GradientDrawable().apply {
                setColor(0xff182329.toInt())
                cornerRadius = 12f
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = row(position, false)

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = row(position, true)
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int = fill) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != fill) setStroke(dp(1), stroke)
    }

    private fun solidBackground(fill: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(12).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val BG = 0xff0b0f14.toInt()
        private const val WHITE = 0xfff1f6f7.toInt()
        private const val MUTED = 0xffa5b3ba.toInt()
        private const val ACCENT = 0xff51d4c8.toInt()
        private const val SUCCESS = 0xff59d79a.toInt()
        private const val ERROR = 0xffff9677.toInt()
    }
}
