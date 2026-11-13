package space.manus.tgvidapp

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import space.manus.tgvidapp.nativeapi.LocalBotApiModule

class MainActivity : Activity() {
  private lateinit var apiId: EditText
  private lateinit var apiHash: EditText
  private lateinit var botToken: EditText
  private lateinit var link: EditText
  private lateinit var status: TextView
  private lateinit var download: Button
  private val prefs by lazy { getSharedPreferences("telegram", MODE_PRIVATE) }
  private val native by lazy { LocalBotApiModule(this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    title = "Telegram Video Downloader"
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 36, 36, 36) }
    root.addView(TextView(this).apply { text = "Telegram Video Downloader"; textSize = 26f; setPadding(0, 0, 0, 24) })
    apiId = field("API ID", prefs.getString("api_id", ""), InputType.TYPE_CLASS_NUMBER); root.addView(apiId)
    apiHash = field("API Hash", prefs.getString("api_hash", ""), InputType.TYPE_CLASS_TEXT); root.addView(apiHash)
    botToken = field("Bot Token", prefs.getString("bot_token", ""), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD); root.addView(botToken)
    link = field("Public Telegram link", "https://t.me/ruijie_demo/173", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI); root.addView(link)
    val save = Button(this).apply { text = "Save settings"; setOnClickListener { saveSettings(); toast("Settings saved") } }; root.addView(save)
    download = Button(this).apply { text = "Download video"; setOnClickListener { startDownload() } }; root.addView(download)
    status = TextView(this).apply { text = "Ready"; textSize = 15f; setPadding(0, 24, 0, 0) }; root.addView(status)
    setContentView(ScrollView(this).apply { addView(root) })
  }

  private fun field(label: String, value: String?, type: Int) = EditText(this).apply {
    hint = label; setText(value ?: ""); inputType = type; setSingleLine(true)
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
  }

  private fun saveSettings() { prefs.edit().putString("api_id", apiId.text.toString().trim()).putString("api_hash", apiHash.text.toString().trim()).putString("bot_token", botToken.text.toString().trim()).apply() }
  private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
  private fun setStage(message: String) = runOnUiThread { status.text = message }

  private fun startDownload() {
    saveSettings()
    val id = apiId.text.toString().trim().toIntOrNull()
    val hash = apiHash.text.toString().trim(); val token = botToken.text.toString().trim(); val publicLink = link.text.toString().trim()
    if (id == null || hash.isBlank() || token.isBlank() || publicLink.isBlank()) { toast("API ID, API Hash, Bot Token နဲ့ link ဖြည့်ပါ"); return }
    download.isEnabled = false
    Thread {
      try {
        setStage("[1/3] Local Bot API Server စတင်နေပါတယ်…")
        if (!native.start(id.toLong(), hash)) throw IllegalStateException("Local Bot API Server မစတင်နိုင်ပါ။")
        setStage("[2/3] Telethon နဲ့ file_id ရှာနေပါတယ်…")
        val media = native.resolve(id, hash, token, publicLink)
        setStage("[3/3] Local Bot API နဲ့ download လုပ်နေပါတယ်…")
        val saved = native.download(token, media)
        setStage("ပြီးပါပြီ: ${saved.path.name} (${saved.size} bytes)")
      } catch (error: Throwable) {
        setStage("မအောင်မြင်ပါ: ${error.message ?: error.javaClass.simpleName}")
      } finally { runOnUiThread { download.isEnabled = true } }
    }.start()
  }

  override fun onDestroy() { runCatching { native.stop() }; super.onDestroy() }
}
