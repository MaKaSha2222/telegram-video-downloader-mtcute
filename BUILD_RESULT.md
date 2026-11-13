# Build result

- `pnpm install --frozen-lockfile`: passed
- `pnpm exec tsc --noEmit`: passed
- Telegram unit tests: 6 passed
- Android SDK/platform/build-tools/JDK installed in the sandbox
- Android release APK build: blocked by the ARM64 sandbox because the Android NDK package provides a `linux-x86_64` compiler; CMake reports `Exec format error`.
- Build must run on an x86_64 Android CI runner/device environment.
