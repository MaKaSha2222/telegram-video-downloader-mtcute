import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";
import { TelegramClient } from "@mtcute/web";
import { MemoryStorage } from "@mtcute/core";

import { parsePublicTelegramPost } from "@/lib/telegram-link";

const SESSION_KEY = "telegram_mtproto_bot_session_v1";

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
    storage: new MemoryStorage(),
    disableUpdates: true,
  });

  try {
    await client.start({
      botToken: credentials.botToken,
      session: savedSession ?? undefined,
    });

    const sessionValue = await client.exportSession();
    if (sessionValue) {
      await SecureStore.setItemAsync(SESSION_KEY, sessionValue);
    }

    const [message] = await client.getMessages(post.channel, post.messageId);
    if (!message) throw new Error("Telegram post ကို မတွေ့ပါ။ Link နဲ့ channel access ကို စစ်ပါ။");

    const media = message.media;
    if (!media || media.type !== "video") {
      throw new Error("ဒီ post မှာ video file မပါပါ။");
    }

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
    await client.disconnect().catch(() => undefined);
  }
}
