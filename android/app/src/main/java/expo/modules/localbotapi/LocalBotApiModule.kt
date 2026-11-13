package expo.modules.localbotapi

/**
 * JNI compatibility shim for the prebuilt liblocalbotapi.so bundled in the original AAR.
 * This class has no Expo dependency; its package and native signatures must match the
 * exported symbols in that already-compiled ARM64 library.
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
    }
}
