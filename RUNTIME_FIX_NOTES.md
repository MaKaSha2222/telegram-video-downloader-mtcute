# Runtime error and size fix notes

## Root causes found

The first Android crash was caused by the mtcute web adapter importing a WebAssembly implementation that Hermes does not provide. The next runtime risk came from the native quick-crypto wrapper and its Hermes-incompatible package export. A local app-equivalent mtcute harness then found a third issue while testing the replacement: noble AES-ECB padding was enabled by default, so Telegram packet encryption did not match OpenSSL and authentication timed out.

## Current fix

- Removed `@mtcute/web` and the `@mtcute/wasm` dependency path.
- Kept the existing mtcute core + Telegram WebSocket transport and the local native Bot API download server.
- Replaced native quick-crypto with audited pure-JavaScript noble hashes/ciphers and Expo Crypto random bytes.
- Disabled noble AES-ECB padding and created single-use ECB instances for Telegram AES-CTR/AES-IGE blocks.
- Added 30-second timeouts around Telegram login and message lookup, plus a bounded disconnect timeout.
- The UI now shows the exact failed stage and error message, and provides a retry button instead of an indefinite spinner.
- Kept release LTO, section garbage collection, symbol stripping, R8, and resource shrinking.

## Validation

- TypeScript check: passed after the noble provider change.
- Vitest: 31 tests passed, 1 test skipped.
- App-equivalent mtcute harness using the same noble crypto provider, WebSocket URLs, packet codecs, bot login, and `getMessages("ruijie_demo", 173)`: passed and returned `video/mp4`.
- Bot API `getFile` validation: passed for the returned file ID and returned `videos/file_0`.
- The previous noble AES padding bug was reproduced locally, fixed, and retested successfully.
- A physical Android device was not available in this sandbox, so a device-level React Native test remains the final validation step after the release APK is installed.

## Size

The previous APK contained a 9.4 MB `libQuickCrypto.so`. Removing that native module is expected to reduce the APK by roughly 8–10 MB. The 51 MB `liblocalbotapi.so` is the bundled Telegram Bot API engine required to preserve the fast local download workflow and cannot be removed without changing that workflow.

## Build

```bash
cd android
./gradlew assembleRelease
```

APK path: `android/app/build/outputs/apk/release/app-release.apk`.
