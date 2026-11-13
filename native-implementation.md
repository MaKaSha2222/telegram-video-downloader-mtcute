# Android native implementation status

## Upstream sources selected

The official Telegram Bot API source is pinned to commit `7232881b39dfcd048a0d569f1983c5184249ee05` from `tdlib/telegram-bot-api`. Its TDLib submodule is pinned by the upstream repository to `c8828d50f5eac2fc7089ce1089d7ce9095c61b5e`.

The upstream CMake project currently creates a standalone `telegram-bot-api` executable. The Android integration still needs to turn that lifecycle into an in-process library entry point. In particular, the current upstream `main()` installs process-wide signal handlers, creates a `td::ConcurrentScheduler`, binds HTTP on `0.0.0.0` by default, and waits for signal-driven shutdown. Those behaviors must not be copied unchanged into an Android app.

## Required native work before an APK can be called functional

1. Add a local Expo module at `modules/local-bot-api` with an Android Kotlin module named `LocalBotApi`.
2. Add an Android foreground service for the download operation. The service must declare the data-sync foreground-service type, post a user-visible notification, and stop itself after the file has been copied to MediaStore or app-private Movies storage.
3. Build TDLib, OpenSSL, and the Bot API sources for `arm64-v8a` with the Android NDK. The upstream `td/example/android/build-tdlib.sh` and `build-openssl.sh` are reference scripts, not a completed build for this project.
4. Replace the executable-only Bot API entry point with an app lifecycle API: start, status, stop, and a real `getFile` download operation. Bind the HTTP endpoint only to `127.0.0.1` and use an app-private writable directory for working, files, and temporary data.
5. Emit `onDownloadProgress` only from measured bytes copied from the returned local file into the Android destination. The TypeScript contract accepts an optional measured `speedBytesPerSecond`; no percentage or speed is shown before native code reports real values.
6. Validate the resulting custom Android build and a real public Telegram video on an ARM64 device. Expo Go and this Sandbox cannot validate the custom native module.

No placeholder native success path is included. Until the module and device test exist, the app must continue to report that the native server is unavailable rather than claim a completed download.
