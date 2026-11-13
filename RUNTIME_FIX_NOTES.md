# Runtime error fix notes

## Root causes found

The first Android crash was caused by the mtcute web adapter importing a WebAssembly implementation that Hermes does not provide. After that dependency was removed, the next crash was caused by the package-level `react-native-quick-crypto` CommonJS compatibility footer assigning `module.exports.default`; Hermes treated that export as a read-only ESM namespace property.

## Current fix

- Removed `@mtcute/web` and the `@mtcute/wasm` dependency path.
- Kept the existing mtcute core + WebSocket workflow and the local native Bot API download server.
- Load only the native quick-crypto source modules (`cipher`, `hash`, `hmac`, `pbkdf2`, and `random`) through Metro `require()`, bypassing the incompatible package-root export footer.
- Added the `process.nextTick` compatibility guard required by `readable-stream` on React Native.
- Kept the `Object.hasOwn()` and Expo Crypto random-value fallbacks before Expo Router initializes.
- Enabled native LTO/size optimization plus release R8/resource shrinking.

## Validation

- TypeScript check: passed.
- Vitest: 31 tests passed, 1 test skipped.
- Android Hermes bundle export: passed.
- Bundle scan: no mtcute WebAssembly implementation, mtcute web adapter, or incompatible quick-crypto package wrapper found.
- A physical Android device was not available in this sandbox, so a real Telegram end-to-end download cannot be claimed as tested until the new APK is installed on the device.

## Build

```bash
cd android
./gradlew assembleRelease
```

APK path: `android/app/build/outputs/apk/release/app-release.apk`.
