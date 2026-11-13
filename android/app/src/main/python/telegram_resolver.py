"""Resolve a public Telegram video post to a Bot API-compatible file_id.

This module intentionally only uses Telethon to inspect message metadata.
Video bytes are fetched separately by the in-app Telegram Bot API Server.
"""

import asyncio
import base64
import json
import re
import struct
from urllib.parse import urlsplit

from telethon import TelegramClient
from telethon.sessions import StringSession
from telethon.tl.types import DocumentAttributeVideo


PUBLIC_POST_PATH = re.compile(r"^/(?:s/)?([A-Za-z][A-Za-z0-9_]{4,31})/([1-9][0-9]*)/?$")
TELEGRAM_HOSTS = {"t.me", "www.t.me", "telegram.me", "www.telegram.me"}


def parse_public_post(raw_link):
    try:
        parsed = urlsplit((raw_link or "").strip())
    except Exception as exc:
        raise ValueError("Telegram post link ပုံစံ မမှန်ပါ။") from exc

    if parsed.scheme.lower() not in {"http", "https"} or (parsed.hostname or "").lower() not in TELEGRAM_HOSTS:
        raise ValueError("အများမြင်နိုင်တဲ့ Telegram link ကို ထည့်ပါ။")
    if parsed.username or parsed.password:
        raise ValueError("Link ပုံစံ မမှန်ပါ။")

    match = PUBLIC_POST_PATH.fullmatch(parsed.path)
    if not match:
        raise ValueError("အများမြင်နိုင်တဲ့ channel post link ကို ထည့်ပါ။")

    message_id = int(match.group(2))
    if message_id < 1 or message_id > 2**63 - 1:
        raise ValueError("Post ID မမှန်ပါ။")
    return match.group(1), message_id


def _zero_run_encode(data):
    """Match td::zero_encode exactly (runs are split at 250 bytes)."""
    output = bytearray()
    index = 0
    while index < len(data):
        value = data[index]
        output.append(value)
        if value == 0:
            count = 1
            while count < 250 and index + count < len(data) and data[index + count] == 0:
                count += 1
            output.append(count)
            index += count
        else:
            index += 1
    return bytes(output)


def _tl_store_string(value):
    """Serialize a TL string using TDLib's TlStorerUnsafe byte layout."""
    value = bytes(value)
    length = len(value)
    if length < 254:
        header = bytes((length,))
    elif length < (1 << 24):
        header = bytes((254, length & 255, (length >> 8) & 255, (length >> 16) & 255))
    else:
        raise ValueError("Telegram file_reference သည် အလွန်ရှည်နေပါသည်။")
    raw = header + value
    return raw + (b"\x00" * ((-len(raw)) % 4))


def pack_bot_api_file_id(dc_id, document_id, access_hash, file_reference):
    """Serialize a TDLib FullRemoteFileLocation and persistent-ID v4 trailer."""
    if not isinstance(dc_id, int) or dc_id < 1:
        raise ValueError("Telegram document ရဲ့ DC ID မမှန်ပါ။")
    file_reference = bytes(file_reference or b"")
    file_type_video = 4
    file_reference_flag = 1 << 25
    type_value = file_type_video | (file_reference_flag if file_reference else 0)
    location = struct.pack("<ii", type_value, dc_id)
    if file_reference:
        location += _tl_store_string(file_reference)
    location += struct.pack("<qq", int(document_id), int(access_hash))

    # Pinned TDLib Version::Next is 62: the serializer stores Next - 1,
    # followed by FileNode::PERSISTENT_ID_VERSION (4).
    persistent_id = _zero_run_encode(location) + bytes((61, 4))
    return base64.urlsafe_b64encode(persistent_id).decode("ascii").rstrip("=")


def _error_payload(error, secrets):
    message = str(error) or type(error).__name__
    for secret in secrets:
        if secret and len(secret) >= 6:
            message = message.replace(secret, "[hidden]")

    error_type = type(error).__name__
    if type(error).__module__.startswith("telethon"):
        error_type = "Telethon." + error_type
    code = getattr(error, "code", None)
    if not isinstance(code, (int, float, str)):
        code = None
    details = {}
    for name in ("seconds", "new_dc", "dc", "code_length", "which"):
        value = getattr(error, name, None)
        if isinstance(value, (int, float, str, bool)):
            details[name] = value
    return {"type": error_type, "code": code, "message": message, "details": details}


async def _resolve_async(link, api_id, api_hash, bot_token):
    channel, message_id = parse_public_post(link)
    client = TelegramClient(StringSession(), int(api_id), str(api_hash), connection_retries=3)
    try:
        await client.start(bot_token=str(bot_token))
        entity = await client.get_entity(channel)
        message = await client.get_messages(entity, ids=message_id)
        if message is None:
            raise ValueError("Telegram post ကို မတွေ့ပါ။ Link နဲ့ channel access ကို စစ်ပါ။")

        document = message.document
        if document is None:
            raise ValueError("ဒီ post မှာ video file မပါပါ။")
        attributes = document.attributes or []
        has_video = any(isinstance(attribute, DocumentAttributeVideo) for attribute in attributes)
        mime_type = str(document.mime_type or "")
        if not has_video and not mime_type.startswith("video/"):
            raise ValueError("ဒီ post မှာ video file မပါပါ။")

        return pack_bot_api_file_id(document.dc_id, document.id, document.access_hash, document.file_reference)
    finally:
        await client.disconnect()


def resolve_public_video(link, api_id, api_hash, bot_token):
    """Return only the Bot API file_id; never fetch or save media bytes."""
    try:
        if not str(api_id).isdigit() or int(api_id) <= 0 or not api_hash or not bot_token:
            raise ValueError("Settings ထဲမှာ API ID, API Hash နဲ့ Bot Token ကို ထည့်ပါ။")
        file_id = asyncio.run(_resolve_async(link, int(api_id), api_hash, bot_token))
        return json.dumps({"ok": True, "file_id": file_id}, ensure_ascii=False)
    except Exception as exc:
        error = _error_payload(exc, (str(api_hash), str(bot_token)))
        return json.dumps({"ok": False, "error": error}, ensure_ascii=False)
