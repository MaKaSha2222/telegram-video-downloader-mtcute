import { requireOptionalNativeModule, type EventSubscription } from "expo-modules-core";

export type LocalBotApiStatus = {
  running: boolean;
  port?: number;
};

export type BotApiDownloadProgress = {
  phase: "requesting" | "copying" | "complete";
  copiedBytes: number;
  totalBytes: number | null;
  speedBytesPerSecond?: number;
};

export type SavedVideo = {
  uri: string;
  sizeBytes: number;
};

export type TelethonResolvedMedia = {
  channel: string;
  chatId: number;
  messageId: number;
  mediaType: string;
  mimeType: string;
  fileName: string;
  size: number;
  documentId: number;
  accessHash: number;
  dcId: number;
  fileId: string;
};

type LocalBotApiNativeModule = {
  getStatus(): Promise<LocalBotApiStatus>;
  startServer(apiId: number, apiHash: string): Promise<LocalBotApiStatus>;
  stopServer(): Promise<void>;
  resolveTelethon(apiId: number, apiHash: string, botToken: string, link: string): Promise<TelethonResolvedMedia>;
  downloadTelethonVideo(apiId: number, apiHash: string, botToken: string, link: string, fileName: string): Promise<SavedVideo>;
  downloadVideo(botToken: string, fileId: string, fileName: string): Promise<SavedVideo>;
  addListener(eventName: "onDownloadProgress", listener: (progress: BotApiDownloadProgress) => void): EventSubscription;
};

const nativeModule = requireOptionalNativeModule<LocalBotApiNativeModule>("LocalBotApi");

export const isLocalBotApiAvailable = nativeModule !== null;

export async function getLocalBotApiStatus(): Promise<LocalBotApiStatus> {
  if (!nativeModule) return { running: false };
  return nativeModule.getStatus();
}

export async function startLocalBotApi(apiId: number, apiHash: string): Promise<LocalBotApiStatus> {
  if (!nativeModule) throw new Error("ဒီ Android build မှာ app အတွင်း Bot API Server native module မပါသေးပါ။");
  return nativeModule.startServer(apiId, apiHash);
}

export async function stopLocalBotApi(): Promise<void> {
  if (!nativeModule) return;
  await nativeModule.stopServer();
}

export async function resolveWithTelethon(
  apiId: number,
  apiHash: string,
  botToken: string,
  link: string,
): Promise<TelethonResolvedMedia> {
  if (!nativeModule) throw new Error("ဒီ Android build မှာ Telethon native module မပါသေးပါ။");
  return nativeModule.resolveTelethon(apiId, apiHash, botToken, link);
}

export async function downloadWithTelethon(
  apiId: number,
  apiHash: string,
  botToken: string,
  link: string,
  fileName: string,
): Promise<SavedVideo> {
  if (!nativeModule) throw new Error("ဒီ Android build မှာ Telethon native module မပါသေးပါ။");
  return nativeModule.downloadTelethonVideo(apiId, apiHash, botToken, link, fileName);
}

export async function downloadWithLocalBotApi(
  botToken: string,
  fileId: string,
  fileName: string,
): Promise<SavedVideo> {
  if (!nativeModule) throw new Error("ဒီ Android build မှာ app အတွင်း Bot API Server native module မပါသေးပါ။");
  return nativeModule.downloadVideo(botToken, fileId, fileName);
}

export function addBotApiProgressListener(
  listener: (progress: BotApiDownloadProgress) => void,
): EventSubscription | { remove: () => void } {
  if (!nativeModule) return { remove: () => undefined };
  return nativeModule.addListener("onDownloadProgress", listener);
}
