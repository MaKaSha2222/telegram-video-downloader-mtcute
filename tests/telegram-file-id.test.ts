import { describe, expect, it } from "vitest";

import { packBotApiFileId } from "../lib/telegram-file-id";

function decodeZeroRuns(encoded: Buffer): Buffer {
  const output: number[] = [];
  for (let i = 0; i < encoded.length; i += 1) {
    const byte = encoded[i];
    if (byte === 0) {
      const count = encoded[++i];
      for (let j = 0; j < count; j += 1) output.push(0);
    } else {
      output.push(byte);
    }
  }
  return Buffer.from(output);
}

describe("packBotApiFileId", () => {
  it("encodes a Telegram document reference in Bot API URL-safe form", () => {
    const fileId = packBotApiFileId({ dcId: 4, id: "1234567890123456", accessHash: "-1234567890123456789" });
    expect(fileId).toMatch(/^[A-Za-z0-9_-]+$/);

    const raw = decodeZeroRuns(Buffer.from(fileId, "base64url"));
    const view = new DataView(raw.buffer, raw.byteOffset, raw.byteLength);
    expect(raw).toHaveLength(25);
    expect(view.getInt32(0, true)).toBe(4);
    expect(view.getInt32(4, true)).toBe(4);
    expect(view.getBigInt64(8, true)).toBe(1234567890123456n);
    expect(view.getBigInt64(16, true)).toBe(-1234567890123456789n);
    expect(view.getUint8(24)).toBe(2);
  });

  it("rejects an invalid Telegram data-center id", () => {
    expect(() => packBotApiFileId({ dcId: 0, id: "1", accessHash: "2" })).toThrow();
  });
});
