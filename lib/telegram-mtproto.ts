import * as SecureStore from "expo-secure-store";
import { getRandomBytes } from "expo-crypto";
import { Platform } from "react-native";
import { connectWs } from "@fuman/net";
import {
  IntermediatePacketCodec,
  ObfuscatedPacketCodec,
  type ICorePlatform,
  type TelegramTransport,
} from "@mtcute/core";
import { TelegramClient } from "@mtcute/core/client.js";
import { MemoryStorage } from "@mtcute/core";
import { BaseCryptoProvider } from "@mtcute/core/utils.js";
import { gzip, ungzip, inflate } from "pako";
import { ecb } from "@noble/ciphers/aes.js";
import { hmac } from "@noble/hashes/hmac.js";
import { pbkdf2 } from "@noble/hashes/pbkdf2.js";
import { sha256, sha512 } from "@noble/hashes/sha2.js";
import { sha1 } from "@noble/hashes/legacy.js";

import { parsePublicTelegramPost } from "@/lib/telegram-link";

const SESSION_KEY = "telegram_mtproto_bot_session_v1";
const TELEGRAM_START_TIMEOUT_MS = 30_000;
const TELEGRAM_LOOKUP_TIMEOUT_MS = 30_000;
const TELEGRAM_DISCONNECT_TIMEOUT_MS = 5_000;

function withTimeout<T>(promise: Promise<T>, milliseconds: number, message: string): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error(message)), milliseconds);
  });

  return Promise.race([promise, timeout]).finally(() => {
    if (timer !== undefined) clearTimeout(timer);
  });
}

/**
 * Hermes does not provide the browser WebAssembly runtime used by mtcute's web
 * adapter. This provider uses small audited pure-JavaScript noble primitives,
 * keeping the Android runtime independent from native crypto wrappers.
 */
class ReactNativeCryptoProvider extends BaseCryptoProvider {
  sha1(data: Uint8Array): Uint8Array {
    return sha1(data);
  }

  sha256(data: Uint8Array): Uint8Array {
    return sha256(data);
  }

  async pbkdf2(
    password: Uint8Array,
    salt: Uint8Array,
    iterations: number,
    keylen = 64,
    algo = "sha512",
  ): Promise<Uint8Array> {
    const hash = algo.toLowerCase() === "sha512" ? sha512 : sha256;
    return pbkdf2(hash, password, salt, { c: iterations, dkLen: keylen });
  }

  async hmacSha256(data: Uint8Array, key: Uint8Array): Promise<Uint8Array> {
    return hmac(sha256, key, data);
  }

  createAesCtr(key: Uint8Array, iv: Uint8Array) {
    const counter = new Uint8Array(iv);
    let keystream = new Uint8Array(0);
    const incrementCounter = () => {
      for (let index = counter.length - 1; index >= 0; index -= 1) {
        counter[index] = (counter[index] + 1) & 0xff;
        if (counter[index] !== 0) break;
      }
    };
    return {
      process: (data: Uint8Array) => {
        const output = new Uint8Array(data.length);
        for (let index = 0; index < data.length; index += 1) {
          if (keystream.length === 0) {
            // noble cipher instances are intentionally single-use. Create a
            // fresh ECB instance for each counter block while keeping the
            // counter and keystream state across process() calls.
            keystream = ecb(key, { disablePadding: true }).encrypt(counter);
            incrementCounter();
          }
          output[index] = data[index] ^ keystream[0];
          keystream = keystream.slice(1);
        }
        return output;
      },
      close: () => undefined,
    };
  }

  createAesIge(key: Uint8Array, iv: Uint8Array) {
    const initialIv = new Uint8Array(iv);
    if (key.byteLength !== 32 || initialIv.byteLength !== 32) {
      throw new Error("AES-256-IGE requires a 32-byte key and IV");
    }

    const encryptBlock = (block: Uint8Array) => ecb(key, { disablePadding: true }).encrypt(block);
    const decryptBlock = (block: Uint8Array) => ecb(key, { disablePadding: true }).decrypt(block);

    return {
      encrypt: (data: Uint8Array) => {
        if (data.byteLength % 16 !== 0) throw new Error("AES-IGE data must be block aligned");
        const output = new Uint8Array(data.byteLength);
        let previousCipher = initialIv.slice(0, 16);
        let previousPlain = initialIv.slice(16, 32);
        for (let offset = 0; offset < data.byteLength; offset += 16) {
          const mixed = new Uint8Array(16);
          for (let i = 0; i < 16; i++) mixed[i] = data[offset + i] ^ previousCipher[i];
          const encrypted = encryptBlock(mixed);
          for (let i = 0; i < 16; i++) output[offset + i] = encrypted[i] ^ previousPlain[i];
          previousCipher = output.slice(offset, offset + 16);
          previousPlain = data.slice(offset, offset + 16);
        }
        return output;
      },
      decrypt: (data: Uint8Array) => {
        if (data.byteLength % 16 !== 0) throw new Error("AES-IGE data must be block aligned");
        const output = new Uint8Array(data.byteLength);
        let previousCipher = initialIv.slice(0, 16);
        let previousPlain = initialIv.slice(16, 32);
        for (let offset = 0; offset < data.byteLength; offset += 16) {
          const mixed = new Uint8Array(16);
          for (let i = 0; i < 16; i++) mixed[i] = data[offset + i] ^ previousPlain[i];
          const decrypted = decryptBlock(mixed);
          for (let i = 0; i < 16; i++) output[offset + i] = decrypted[i] ^ previousCipher[i];
          previousCipher = data.slice(offset, offset + 16);
          previousPlain = output.slice(offset, offset + 16);
        }
        return output;
      },
    };
  }

  gzip(data: Uint8Array, maxSize: number): Uint8Array | null {
    const compressed = gzip(data);
    return compressed.byteLength > maxSize ? null : compressed;
  }

  gunzip(data: Uint8Array): Uint8Array {
    return ungzip(data);
  }

  inflate(data: Uint8Array, maxSize: number): Uint8Array {
    const result = inflate(data);
    if (result.byteLength > maxSize) throw new Error("Inflated data exceeds the allowed size");
    return result;
  }

  randomFill(buf: Uint8Array): void {
    buf.set(getRandomBytes(buf.byteLength));
  }
}

const platform: ICorePlatform = {
  beforeExit: () => () => undefined,
  log: () => undefined,
  getDefaultLogLevel: () => null,
  getDeviceModel: () => "Android React Native",
  onNetworkChanged: () => () => undefined,
  isOnline: () => true,
};

const subdomains: Record<number, string> = {
  1: "pluto",
  2: "venus",
  3: "aurora",
  4: "vesta",
  5: "flora",
};

const transport: TelegramTransport = {
  connect: (dc, abortSignal) =>
    connectWs(
      {
        url: `wss://${subdomains[dc.id]}.web.telegram.org/apiws${dc.testMode ? "_test" : ""}`,
        implementation: WebSocket,
        protocols: "binary",
      },
      abortSignal,
    ),
  packetCodec: () => new ObfuscatedPacketCodec(new IntermediatePacketCodec()),
};

export type TelegramCredentials = {
  apiId: number;
  apiHash: string;
  botToken: string;
};

export type ResolvedTelegramVideo = {
  chatId: string;
  messageId: number;
  fileId: string;
  fileName: string;
  mimeType: string;
};

function safeError(error: unknown, credentials: TelegramCredentials): Error {
  let message = error instanceof Error ? error.message : "Telegram နဲ့ ချိတ်ဆက်မရပါ။";
  for (const secret of [credentials.botToken, credentials.apiHash]) {
    if (secret) message = message.split(secret).join("[hidden]");
  }
  return new Error(message);
}

export async function resolvePublicTelegramVideo(
  link: string,
  credentials: TelegramCredentials,
): Promise<ResolvedTelegramVideo> {
  if (Platform.OS === "web") {
    throw new Error("ဒီလုပ်ဆောင်ချက်ကို Android app ထဲမှာ သုံးပါ။");
  }
  if (!Number.isInteger(credentials.apiId) || credentials.apiId <= 0 || !credentials.apiHash || !credentials.botToken) {
    throw new Error("Settings ထဲမှာ API ID, API Hash နဲ့ Bot Token ကို ထည့်ပါ။");
  }

  const post = parsePublicTelegramPost(link);
  const savedSession = await SecureStore.getItemAsync(SESSION_KEY).catch(() => null);
  const client = new TelegramClient({
    apiId: credentials.apiId,
    apiHash: credentials.apiHash,
    crypto: new ReactNativeCryptoProvider(),
    platform,
    transport,
    storage: new MemoryStorage(),
    disableUpdates: true,
  });

  try {
    await withTimeout(
      client.start({
        botToken: credentials.botToken,
        session: savedSession ?? undefined,
      }),
      TELEGRAM_START_TIMEOUT_MS,
      "Telegram ချိတ်ဆက်မှု အချိန်ကုန်သွားပါပြီ။ Internet, API ID/API Hash နဲ့ Bot Token ကို စစ်ပြီး ထပ်ကြိုးစားပါ။",
    );

    const sessionValue = await client.exportSession();
    if (sessionValue) await SecureStore.setItemAsync(SESSION_KEY, sessionValue);

    const [message] = await withTimeout(
      client.getMessages(post.channel, post.messageId),
      TELEGRAM_LOOKUP_TIMEOUT_MS,
      "Telegram post အချက်အလက် ရယူချိန် အလွန်ကြာနေပါပြီ။ Channel link နဲ့ Internet connection ကို စစ်ပြီး ထပ်ကြိုးစားပါ။",
    );
    if (!message) throw new Error("Telegram post ကို မတွေ့ပါ။ Link နဲ့ channel access ကို စစ်ပါ။");

    const media = message.media;
    if (!media || media.type !== "video") throw new Error("ဒီ post မှာ video file မပါပါ။");

    const rawName = media.fileName ?? `video_${post.messageId}.mp4`;
    const fileName = rawName.replace(/[\\/:*?"<>|\u0000-\u001f]/g, "_").slice(0, 180);
    return {
      chatId: String(message.chat.id),
      messageId: post.messageId,
      fileId: media.fileId,
      fileName: fileName || `video_${post.messageId}.mp4`,
      mimeType: media.mimeType,
    };
  } catch (error) {
    throw safeError(error, credentials);
  } finally {
    await withTimeout(client.disconnect(), TELEGRAM_DISCONNECT_TIMEOUT_MS, "Telegram connection ပိတ်ချိန် အလွန်ကြာနေပါပြီ.").catch(() => undefined);
  }
}
