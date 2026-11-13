import base64
import json
import struct
import unittest
from unittest.mock import patch

from telegram_resolver import _error_payload, pack_bot_api_file_id, parse_public_post, resolve_public_video


def decode_zero_runs(encoded):
    data = base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4))
    output = bytearray()
    index = 0
    while index < len(data):
        value = data[index]
        index += 1
        if value == 0:
            count = data[index]
            index += 1
            output.extend(b"\x00" * count)
        else:
            output.append(value)
    return bytes(output)


def read_tl_string(payload, offset):
    first = payload[offset]
    offset += 1
    if first < 254:
        length = first
    else:
        length = payload[offset] | (payload[offset + 1] << 8) | (payload[offset + 2] << 16)
        offset += 3
    value = payload[offset : offset + length]
    offset += length
    offset += (-offset) % 4
    return value, offset


class TelegramResolverTests(unittest.TestCase):
    def test_parse_channel_link(self):
        self.assertEqual(parse_public_post("https://t.me/testchannel/123"), ("testchannel", 123))
        self.assertEqual(parse_public_post("https://t.me/s/testchannel/456?single"), ("testchannel", 456))

    def test_reject_non_telegram_or_private_links(self):
        for link in ("https://example.com/channel/12", "https://t.me/c/123456/78", "not a link"):
            with self.subTest(link=link), self.assertRaises(ValueError):
                parse_public_post(link)

    def test_encodes_pinned_tdlib_persistent_file_id(self):
        reference = b"\x00reference-bytes\x00"
        encoded = pack_bot_api_file_id(4, 1234567890123456, -1234567890123456789, reference)
        self.assertRegex(encoded, r"^[A-Za-z0-9_-]+$")
        binary = decode_zero_runs(encoded)
        self.assertEqual(binary[-2:], bytes((61, 4)))
        payload = binary[:-2]
        file_type_flags, dc_id = struct.unpack_from("<ii", payload, 0)
        self.assertEqual(file_type_flags, 4 | (1 << 25))
        self.assertEqual(dc_id, 4)
        decoded_reference, offset = read_tl_string(payload, 8)
        self.assertEqual(decoded_reference, reference)
        self.assertEqual(
            struct.unpack_from("<qq", payload, offset),
            (1234567890123456, -1234567890123456789),
        )
        self.assertEqual(offset + 16, len(payload))

    def test_omits_file_reference_flag_when_reference_is_empty(self):
        encoded = pack_bot_api_file_id(2, 7, 8, b"")
        payload = decode_zero_runs(encoded)[:-2]
        file_type_flags, dc_id = struct.unpack_from("<ii", payload, 0)
        self.assertEqual(file_type_flags, 4)
        self.assertEqual(dc_id, 2)
        self.assertEqual(struct.unpack_from("<qq", payload, 8), (7, 8))

    def test_reject_invalid_dc(self):
        with self.assertRaises(ValueError):
            pack_bot_api_file_id(0, 1, 2, b"x")

    def test_preserves_telethon_error_type_code_message_and_attributes(self):
        class FakeFloodWaitError(Exception):
            code = 420
            seconds = 37

        FakeFloodWaitError.__module__ = "telethon.errors.rpcerrorlist"
        error = FakeFloodWaitError("Flood wait for bot-secret-token")
        payload = _error_payload(error, ("bot-secret-token",))
        self.assertEqual(payload["type"], "Telethon.FakeFloodWaitError")
        self.assertEqual(payload["code"], 420)
        self.assertEqual(payload["message"], "Flood wait for [hidden]")
        self.assertEqual(payload["details"], {"seconds": 37})

    def test_resolver_returns_structured_validation_error(self):
        response = json.loads(resolve_public_video("https://t.me/testchannel/3", "", "", ""))
        self.assertFalse(response["ok"])
        self.assertEqual(response["error"]["type"], "ValueError")
        self.assertIn("API ID", response["error"]["message"])

    def test_resolver_returns_structured_success(self):
        async def fake_resolve(*_args):
            return "sample_file_id"

        with patch("telegram_resolver._resolve_async", fake_resolve):
            response = json.loads(resolve_public_video("https://t.me/testchannel/3", "123", "hash", "token"))
        self.assertEqual(response, {"ok": True, "file_id": "sample_file_id"})


if __name__ == "__main__":
    unittest.main()
