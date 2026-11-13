// mtcute needs WebCrypto (including crypto.subtle) on Android. This native
// provider works with React Native's New Architecture and avoids the legacy
// synchronous NativeModules random-values bridge.
try {
  const { install } = require("react-native-quick-crypto");
  if (typeof install === "function") install();
} catch (error) {
  // Keep the fallback below so the app can report a useful runtime error.
  console.warn("Native crypto provider was not installed", error);
}

// teleproto/cifrante and some Telegram helpers use Object.hasOwn().
if (typeof Object.hasOwn !== "function") {
  Object.defineProperty(Object, "hasOwn", {
    configurable: true,
    writable: true,
    value: (object, property) => Object.prototype.hasOwnProperty.call(object, property),
  });
}

// Fallback for environments where quick-crypto is unavailable. mtcute's
// native Android path still requires crypto.subtle; this only supplies the
// random-values part for diagnostics and web compatibility.
const { getRandomBytes } = require("expo-crypto");
const integerArrayTypes = new Set([
  Int8Array,
  Uint8Array,
  Uint8ClampedArray,
  Int16Array,
  Uint16Array,
  Int32Array,
  Uint32Array,
]);

function getSecureRandomValues(array) {
  if (!ArrayBuffer.isView(array) || !integerArrayTypes.has(array.constructor)) {
    throw new TypeError("getRandomValues expects an integer typed array");
  }
  if (array.byteLength > 65536) {
    throw new RangeError("getRandomValues cannot request more than 65536 bytes");
  }

  const target = new Uint8Array(array.buffer, array.byteOffset, array.byteLength);
  for (let offset = 0; offset < target.byteLength; offset += 1024) {
    const count = Math.min(1024, target.byteLength - offset);
    target.set(getRandomBytes(count), offset);
  }
  return array;
}

if (typeof globalThis.crypto !== "object" || globalThis.crypto === null) {
  globalThis.crypto = {};
}
if (typeof globalThis.crypto.getRandomValues !== "function") {
  globalThis.crypto.getRandomValues = getSecureRandomValues;
}

// Load Expo Router only after crypto compatibility is ready.
require("expo-router/entry");
