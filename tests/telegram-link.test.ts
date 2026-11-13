import { describe, expect, it } from "vitest";

import { parsePublicTelegramPost } from "../lib/telegram-link";

describe("parsePublicTelegramPost", () => {
  it("reads a public t.me post link", () => {
    expect(parsePublicTelegramPost("https://t.me/senpaianimemm/17646")).toEqual({
      channel: "senpaianimemm",
      messageId: 17646,
    });
  });

  it("reads the web preview form and ignores a query string", () => {
    expect(parsePublicTelegramPost("https://www.t.me/s/senpaianimemm/17646?single")).toEqual({
      channel: "senpaianimemm",
      messageId: 17646,
    });
  });

  it("rejects private invite links and unrelated domains", () => {
    expect(() => parsePublicTelegramPost("https://t.me/+privateInvite")).toThrow();
    expect(() => parsePublicTelegramPost("https://example.com/channel/17646")).toThrow();
  });

  it("rejects malformed and unsafe post ids", () => {
    expect(() => parsePublicTelegramPost("not a URL")).toThrow();
    expect(() => parsePublicTelegramPost("https://t.me/channel/0")).toThrow();
    expect(() => parsePublicTelegramPost("https://t.me/channel/999999999999999999999")).toThrow();
  });
});
