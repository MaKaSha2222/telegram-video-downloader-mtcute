export type PublicTelegramPost = {
  channel: string;
  messageId: number;
};

const PUBLIC_POST_PATH = /^\/(?:s\/)?([A-Za-z][A-Za-z0-9_]{4,31})\/([1-9]\d*)\/?$/;
const TELEGRAM_HOSTS = new Set(["t.me", "www.t.me", "telegram.me", "www.telegram.me"]);

export function parsePublicTelegramPost(raw: string): PublicTelegramPost {
  let url: URL;
  try {
    url = new URL(raw.trim());
  } catch {
    throw new Error("Telegram post link ပုံစံ မမှန်ပါ။");
  }

  if (!TELEGRAM_HOSTS.has(url.hostname.toLowerCase()) || !["https:", "http:"].includes(url.protocol)) {
    throw new Error("အများမြင်နိုင်တဲ့ Telegram link ကို ထည့်ပါ။ ဥပမာ: https://t.me/channelname/123");
  }
  if (url.username || url.password) {
    throw new Error("Link ပုံစံ မမှန်ပါ။");
  }

  const match = PUBLIC_POST_PATH.exec(url.pathname);
  if (!match) {
    throw new Error("အများမြင်နိုင်တဲ့ channel post link ကို ထည့်ပါ။");
  }

  const messageId = Number(match[2]);
  if (!Number.isSafeInteger(messageId) || messageId < 1) {
    throw new Error("Post ID မမှန်ပါ။");
  }

  return { channel: match[1], messageId };
}
