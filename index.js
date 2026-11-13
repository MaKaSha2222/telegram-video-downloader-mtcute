// Some Telegram helpers use Object.hasOwn().
if (typeof Object.hasOwn !== "function") {
  Object.defineProperty(Object, "hasOwn", {
    configurable: true,
    writable: true,
    value: (object, property) => Object.prototype.hasOwnProperty.call(object, property),
  });
}

// readable-stream, used by the native crypto source modules, expects the
// Node-compatible scheduling hook that React Native does not always expose.
if (typeof globalThis.process !== "object" || globalThis.process === null) {
  globalThis.process = {};
}
if (typeof globalThis.process.nextTick !== "function") {
  globalThis.process.nextTick = (callback, ...args) => setImmediate(callback, ...args);
}

// mtcute's pure-JavaScript crypto provider still needs secure random bytes;
// Expo Crypto supplies them without a native crypto wrapper.
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
