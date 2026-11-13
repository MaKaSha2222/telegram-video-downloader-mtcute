import * as SecureStore from "expo-secure-store";
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

import { parsePublicTelegramPost } from "@/lib/telegram-link";

// Do not import react-native-quick-crypto's package root: its CommonJS
// compatibility footer assigns module.exports.default, which Hermes exposes as
// a read-only ESM namespace property. These source modules contain the same
// native implementations without that incompatible footer.
const { Buffer: BufferImpl } = require("@craftzdog/react-native-buffer");
const { createCipheriv, createDecipheriv } = require("react-native-quick-crypto/src/cipher");
const { createHash } = require("react-native-quick-crypto/src/hash");
const { createHmac } = require("react-native-quick-crypto/src/hmac");
const { pbkdf2Sync } = require("react-native-quick-crypto/src/pbkdf2");
const { randomFillSync } = require("react-native-quick-crypto/src/random");

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
 * adapter, so this provider implements the small mtcute crypto surface using
 * the native OpenSSL-backed react-native-quick-crypto module instead.
 */
class ReactNativeCryptoProvider extends BaseCryptoProvider {
  sha1(data: Uint8Array): Uint8Array {
    return new Uint8Array(createHash("sha1").update(data).digest());
  }

  sha256(data: Uint8Array): Uint8Array {
    return new Uint8Array(createHash("sha256").update(data).digest());
  }

  async pbkdf2(
    password: Uint8Array,
    salt: Uint8Array,
    iterations: number,
    keylen = 64,
    algo = "sha512",
  ): Promise<Uint8Array> {
    return new Uint8Array(
      pbkdf2Sync(password, salt, iterations, keylen, algo),
    );
  }

  async hmacSha256(data: Uint8Array, key: Uint8Array): Promise<Uint8Array> {
    return new Uint8Array(
      createHmac("sha256", key).update(data).digest(),
    );
  }

  createAesCtr(key: Uint8Array, iv: Uint8Array) {
    const cipher = createCipheriv(
      "aes-256-ctr",
      BufferImpl.from(key),
      BufferImpl.from(iv),
    );
    return {
      process: (data: Uint8Array) =>
        new Uint8Array(cipher.update(BufferImpl.from(data))),
      close: () => undefined,
    };
  }

  createAesIge(key: Uint8Array, iv: Uint8Array) {
    const initialIv = new Uint8Array(iv);
    if (key.byteLength !== 32 || initialIv.byteLength !== 32) {
      throw new Error("AES-256-IGE requires a 32-byte key and IV");
    }

    const encryptBlock = (block: Uint8Array) => {
      const cipher = createCipheriv(
        "aes-256-ecb",
        BufferImpl.from(key),
        null as any,
      );
      cipher.setAutoPadding(false);
      return new Uint8Array(cipher.update(BufferImpl.from(block)));
    };
    const decryptBlock = (block: Uint8Array) => {
      const decipher = createDecipheriv(
        "aes-256-ecb",
        BufferImpl.from(key),
        null as any,
      );
      decipher.setAutoPadding(false);
      return new Uint8Array(decipher.update(BufferImpl.from(block)));
    };

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
    randomFillSync(buf);
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
