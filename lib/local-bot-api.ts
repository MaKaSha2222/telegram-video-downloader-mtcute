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

type LocalBotApiNativeModule = {
  getStatus(): Promise<LocalBotApiStatus>;
  startServer(apiId: number, apiHash: string): Promise<LocalBotApiStatus>;
  stopServer(): Promise<void>;
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
