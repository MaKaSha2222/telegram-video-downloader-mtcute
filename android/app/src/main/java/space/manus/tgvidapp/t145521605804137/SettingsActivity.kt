package space.manus.tgvidapp.t145521605804137

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var apiIdInput: EditText
    private lateinit var apiHashInput: EditText
    private lateinit var botTokenInput: EditText
    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        setContentView(buildScreen())
        loadSavedValues()
    }

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(36))
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
        content.addView(text("API ID, API Hash နဲ့ Bot Token ကို ထည့်ပါ။ တန်ဖိုးတွေ Android Keystore ဖြင့် encryption လုပ်ပြီး ဒီစက်မှာသာ သိမ်းဆည်းပါတယ်။", 14, MUTED, false).apply {
            setPadding(0, 0, 0, dp(28))
        })

        apiIdInput = addField(content, "API ID", InputType.TYPE_CLASS_NUMBER)
        apiHashInput = addField(content, "API Hash", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        botTokenInput = addField(content, "Bot Token", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

        message = text("", 13, SUCCESS, false).apply {
            visibility = View.GONE
            setTextIsSelectable(true)
        }
        content.addView(message, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        val save = Button(this).apply {
            text = "လုံခြုံစွာ သိမ်းမည်"
            textSize = 15f
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xff081011.toInt())
            background = rounded(ACCENT, 16)
            setOnClickListener { saveCredentials() }
        }
        content.addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(20) })
        content.addView(text("Telethon က public post မှ file_id ကိုသာ ရယူပါတယ်။ ဗီဒီယိုကို app အတွင်း Bot API Server က ဒေါင်းလုဒ်လုပ်ပါတယ်။", 12, MUTED, false).apply {
            setPadding(0, dp(18), 0, 0)
        })
        return scroll
    }

    private fun addField(parent: LinearLayout, label: String, inputType: Int): EditText {
        parent.addView(text(label, 13, WHITE, true).apply { setPadding(0, 0, 0, dp(8)) })
        val edit = EditText(this).apply {
            this.inputType = inputType
            setSingleLine(true)
            setTextColor(WHITE)
            setHintTextColor(0xff687780.toInt())
            hint = "$label ထည့်ပါ"
            textSize = 15f
            setPadding(dp(15), 0, dp(15), 0)
            background = rounded(0xff121a20.toInt(), 14, 0xff27343a.toInt())
        }
        parent.addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { bottomMargin = dp(18) })
        return edit
    }

    private fun loadSavedValues() {
        runCatching { CredentialsStore.load(this) }.onSuccess { saved ->
            if (saved != null) {
                apiIdInput.setText(saved.apiId.toString())
                apiHashInput.setText(saved.apiHash)
                botTokenInput.setText(saved.botToken)
            }
        }.onFailure { error ->
            showMessage(ErrorDisplay.format(error), true)
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

    private fun rounded(fill: Int, radius: Int, stroke: Int = fill) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != fill) setStroke(dp(1), stroke)
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
