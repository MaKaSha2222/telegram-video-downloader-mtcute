import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";

const KEYS = {
  apiId: "tgvid_api_id_v1",
  apiHash: "tgvid_api_hash_v1",
  botToken: "tgvid_bot_token_v1",
} as const;

export type SavedTelegramCredentials = {
  apiId: string;
  apiHash: string;
  botToken: string;
};

export async function loadTelegramCredentials(): Promise<SavedTelegramCredentials | null> {
  if (Platform.OS === "web") return null;
  const [apiId, apiHash, botToken] = await Promise.all([
    SecureStore.getItemAsync(KEYS.apiId),
    SecureStore.getItemAsync(KEYS.apiHash),
    SecureStore.getItemAsync(KEYS.botToken),
  ]);
  if (!apiId || !apiHash || !botToken) return null;
  return { apiId, apiHash, botToken };
}

export async function saveTelegramCredentials(credentials: SavedTelegramCredentials): Promise<void> {
  if (Platform.OS === "web") {
    throw new Error("လျှို့ဝှက်ဆက်တင်တွေကို Android app ရဲ့ လုံခြုံတဲ့နေရာမှာပဲ သိမ်းဆည်းပါ။");
  }
  const apiId = credentials.apiId.trim();
  const apiHash = credentials.apiHash.trim();
  const botToken = credentials.botToken.trim();
  if (!/^\d+$/.test(apiId) || Number(apiId) <= 0 || !apiHash || !botToken) {
    throw new Error("API ID, API Hash နဲ့ Bot Token ကို မှန်ကန်စွာ ထည့်ပါ။");
  }

  await Promise.all([
    SecureStore.setItemAsync(KEYS.apiId, apiId),
    SecureStore.setItemAsync(KEYS.apiHash, apiHash),
    SecureStore.setItemAsync(KEYS.botToken, botToken),
  ]);
}

export async function clearTelegramCredentials(): Promise<void> {
  if (Platform.OS === "web") return;
  await Promise.all(Object.values(KEYS).map((key) => SecureStore.deleteItemAsync(key)));
}
