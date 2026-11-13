# Telegram Video Downloader — Kotlin + Python

This repository is an Android source project. The user interface, settings, credential storage, foreground service, and downloader are native Kotlin. Python is embedded with Chaquopy and runs Telethon only to inspect a public Telegram post and return its Bot API `file_id`. Telethon does **not** download or save video bytes.

## Download flow

1. Kotlin starts the in-app Telegram Bot API Server through the existing C++/TDLib engine.
2. Kotlin calls `telegram_resolver.py` through Chaquopy. Telethon reads the public post metadata and converts the video document reference to a Bot API `file_id`.
3. Kotlin makes `getFile` against the local server at `127.0.0.1`. The Bot API Server retrieves the video file.
4. Kotlin copies the server's app-private returned file into `files/Movies/TelegramVideo` and reports measured copy progress.

The local API endpoint is bound to loopback. API ID, API Hash, and bot token are encrypted with an Android Keystore AES-GCM key. The app currently supports public `t.me` / `telegram.me` channel-post links, not private invite links or `/c/` links. Download error types and diagnostic behavior are documented in [DOWNLOAD_ERRORS.md](DOWNLOAD_ERRORS.md); bundled native library details are in [NATIVE_BINARY.md](NATIVE_BINARY.md).

## Build prerequisites

- Android Studio or Android SDK, Android Gradle Plugin 8.9.1, Gradle 8.11.1, and JDK 17.
- Python 3.12 on the build machine. Chaquopy uses this to package Telethon.
- The prebuilt `arm64-v8a` `liblocalbotapi.so` and matching `libc++_shared.so` are included under `android/app/src/main/jniLibs/arm64-v8a/`. The standard app build packages these files and does not rebuild the native server. Native C++ source remains under `android/app/src/main/cpp/` and `vendor/telegram-bot-api/`.

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

The native libraries are the prebuilt files recovered from the original AAR; they are ARM64 Android ELF libraries and export the JNI methods used by the compatibility shim. No APK is included. This environment has no Android SDK, so an APK build and end-to-end download on an ARM64 device have not been verified. The file-ID encoder still needs a real Telegram/Bot API integration test. The bundled native `nativeStart` method returns only a Boolean, so detailed native startup text is not available from this prebuilt binary. Telethon bot authentication can only read posts the bot account is permitted to access. Do not treat a successful Python unit test as proof that a video will download.
