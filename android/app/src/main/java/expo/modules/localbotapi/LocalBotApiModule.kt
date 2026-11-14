package expo.modules.localbotapi

/**
 * JNI compatibility shim for the locally built liblocalbotapi.so.
 * This class has no Expo dependency; its package and native signatures match the C++ exports.
 */
class LocalBotApiModule {
    companion object {
        init {
            System.loadLibrary("localbotapi")
        }

        @JvmStatic external fun nativeStart(
            apiId: Long,
            apiHash: String,
            workingDirectory: String,
            filesDirectory: String,
            port: Int,
        ): Boolean

        @JvmStatic external fun nativeStop()
        @JvmStatic external fun nativeIsRunning(): Boolean
        @JvmStatic external fun nativeGetDownloadProgress(botFileId: String): String
        @JvmStatic external fun nativeClearDownloadProgress(botFileId: String)
    }
}
