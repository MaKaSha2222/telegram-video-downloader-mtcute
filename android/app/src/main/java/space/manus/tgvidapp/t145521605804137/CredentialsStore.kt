package space.manus.tgvidapp.t145521605804137

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class TelegramCredentials(
    val apiId: Int,
    val apiHash: String,
    val botToken: String,
)

object CredentialsStore {
    private const val PREFS = "telegram_secure_credentials"
    private const val CREDENTIALS_KEY = "encrypted_credentials"
    private const val KEY_ALIAS = "telegram_downloader_aes_gcm"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun load(context: Context): TelegramCredentials? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CREDENTIALS_KEY, null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 12) { "သိမ်းထားတဲ့ ဆက်တင်တွေကို ဖတ်မရပါ။" }
        val iv = packed.copyOfRange(0, 12)
        val ciphertext = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
        return TelegramCredentials(
            apiId = json.getInt("api_id"),
            apiHash = json.getString("api_hash"),
            botToken = json.getString("bot_token"),
        )
    }

    fun save(context: Context, credentials: TelegramCredentials) {
        require(credentials.apiId > 0) { "API ID မှန်ကန်စွာ ထည့်ပါ။" }
        require(credentials.apiHash.isNotBlank()) { "API Hash ထည့်ပါ။" }
        require(credentials.botToken.isNotBlank()) { "Bot Token ထည့်ပါ။" }
        val json = JSONObject().apply {
            put("api_id", credentials.apiId)
            put("api_hash", credentials.apiHash)
            put("bot_token", credentials.botToken)
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val packed = cipher.iv + cipher.doFinal(json)
        val encoded = Base64.encodeToString(packed, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(CREDENTIALS_KEY, encoded).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
