# Telegram Video Downloader (Native Android)

This project is a native Android application. It does not use Expo, React Native, TypeScript, or a JavaScript runtime.

## Architecture

```text
Kotlin Android UI
  -> Chaquopy Python runtime
  -> Telethon 1.42.0 resolves the public Telegram post and produces file_id
  -> Kotlin HTTP client calls the in-process Local Bot API Server
  -> video is copied to app-private Movies/TelegramVideo
```

The Local Bot API Server is compiled from the pinned source under `vendor/telegram-bot-api` with CMake/NDK. Telethon session files and credentials remain in app-private storage; credentials are entered on-device and are not included in source control.

## Build

```bash
cd android
./gradlew :app:assembleRelease --no-daemon
```

The supported release ABI is `arm64-v8a`. GitHub Actions builds on an x86_64 Ubuntu runner because the Android NDK toolchain is x86_64.
