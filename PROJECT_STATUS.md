# Telegram Video Downloader — Native Android

The app has been rebuilt as a plain native Kotlin Android project.

- UI and orchestration: Kotlin Android Views
- Telegram resolution: Python 3.11 through Chaquopy and Telethon 1.42.0
- Download: in-process C++ Local Bot API Server, called from Kotlin over loopback
- Storage: app-private `Movies/TelegramVideo`
- Release ABI: `arm64-v8a`
- JavaScript/Expo/React Native/TypeScript are not part of the Android build

The app must be tested on a real Android device with valid API ID, API Hash, and Bot Token before release claims are made.
