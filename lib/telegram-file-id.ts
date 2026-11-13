import { Buffer } from "buffer";

export type TelegramDocumentReference = {
  dcId: number;
  id: string | number | bigint;
  accessHash: string | number | bigint;
};

function encodeZeroRuns(input: Uint8Array): Uint8Array {
  const output: number[] = [];
  let zeroCount = 0;

  for (const byte of input) {
    if (byte === 0) {
      zeroCount += 1;
      continue;
    }
    if (zeroCount > 0) {
      output.push(0, zeroCount);
      zeroCount = 0;
    }
    output.push(byte);
  }
  if (zeroCount > 0) output.push(0, zeroCount);
  return Uint8Array.from(output);
}

export function packBotApiFileId(document: TelegramDocumentReference): string {
  if (!Number.isInteger(document.dcId) || document.dcId < 1) {
    throw new Error("Telegram document ရဲ့ DC ID မမှန်ပါ။");
  }

  const raw = new Uint8Array(25);
  const view = new DataView(raw.buffer);
  view.setInt32(0, 4, true);
  view.setInt32(4, document.dcId, true);
  view.setBigInt64(8, BigInt(String(document.id)), true);
  view.setBigInt64(16, BigInt(String(document.accessHash)), true);
  view.setUint8(24, 2);

  return Buffer.from(encodeZeroRuns(raw))
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}
