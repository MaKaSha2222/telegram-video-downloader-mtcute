package space.manus.tgvidapp.t145521605804137

import android.content.Context

object DownloadSettings {
    val choices: List<Int> = listOf(1, 2, 3, 4)
    private const val PREFS = "telegram_download_settings"
    private const val PARALLELISM = "parallel_download_limit"
    private const val DEFAULT_PARALLELISM = 2

    fun parallelism(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(PARALLELISM, DEFAULT_PARALLELISM)
        .coerceIn(choices.first(), choices.last())

    fun setParallelism(context: Context, value: Int) {
        require(value in choices) { "Parallel download limit must be between 1 and 4." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(PARALLELISM, value).apply()
    }
}
