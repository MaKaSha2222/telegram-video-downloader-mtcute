# Telegram Video Downloader — Kotlin + Python

This repository is an Android source project. The user interface, settings, credential storage, foreground service, and downloader are native Kotlin. Python is embedded with Chaquopy and runs Telethon only to inspect a public Telegram post and return its Bot API `file_id`. Telethon does **not** download or save video bytes. The Local Bot API Server publishes TDLib `updateFile` byte counts to Kotlin, which polls native progress every 500 ms and shows per-file percent, transferred bytes, and MB/s.

## Download flow

1. Kotlin starts the in-app Telegram Bot API Server through the existing C++/TDLib engine.
2. Kotlin calls `telegram_resolver.py` through Chaquopy. Telethon reads the public post metadata and converts the video document reference to a Bot API `file_id`.
3. Kotlin makes `getFile` against the local server at `127.0.0.1`. While that HTTP request waits, the app reads TDLib `updateFile` progress through JNI every 500 ms.
4. Kotlin copies the server's app-private returned file into `Download/TGMovies` and reports measured copy progress after the remote download completes.

The local API endpoint is bound to loopback. API ID, API Hash, and bot token are encrypted with an Android Keystore AES-GCM key. The app currently supports public `t.me` / `telegram.me` channel-post links, not private invite links or `/c/` links. Download error types and diagnostic behavior are documented in [DOWNLOAD_ERRORS.md](DOWNLOAD_ERRORS.md); bundled native library details are in [NATIVE_BINARY.md](NATIVE_BINARY.md).

## Build prerequisites

- Android Studio or Android SDK, Android NDK, CMake 3.22.1, Android Gradle Plugin 8.9.1, Gradle 8.11.1, and JDK 17.
- Python 3.12 on the build machine. Chaquopy uses this to package Telethon.
- The app builds `liblocalbotapi.so` from the pinned C++ source through CMake. Original ARM64 binaries are preserved under `android/prebuilt/arm64-v8a/` for reference and are not packaged.

Build an ARM64 debug APK from the repository root:

```bash
cd android
./gradlew assembleDebug
```

The expected APK path is `android/app/build/outputs/apk/debug/app-debug.apk`.

## Python resolver tests

```bash
python3 -m pip install -r requirements.txt
PYTHONPATH=android/app/src/main/python python3 -m unittest discover -s tests -v
```

These tests cover public-link parsing, the deterministic `file_id` packing format, and structured error envelopes. They do not contact Telegram or verify a real Bot API download.

## Important validation status

No APK is included. The native progress bridge requires an Android NDK/CMake rebuild; it cannot use the preserved prebuilt library because that binary does not export the new progress methods. This environment has no Android SDK/NDK, so the modified native library, APK build, and end-to-end transfer still need verification on an ARM64 Android device. The file-ID encoder still needs a real Telegram/Bot API integration test. Telethon bot authentication can only read posts the bot account is permitted to access. Do not treat a successful Python unit test as proof that a video will download.
