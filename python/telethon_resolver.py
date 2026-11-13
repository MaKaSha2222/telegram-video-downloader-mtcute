"""Telethon bot resolver used by the Android bridge and local diagnostics."""
from __future__ import annotations

import os
import re
import base64
import struct
from dataclasses import asdict, dataclass
from typing import Any

from telethon import TelegramClient

_LINK_RE = re.compile(r"https?://t\.me/([A-Za-z0-9_]+)/([0-9]+)")


@dataclass(frozen=True)
class ResolvedMedia:
    channel: str
    chat_id: int | None
    message_id: int
    media_type: str | None
    mime_type: str | None
    file_name: str | None
    size: int | None
    document_id: int | None
    access_hash: int | None
    dc_id: int | None
    file_id: str | None


def parse_public_link(link: str) -> tuple[str, int]:
    match = _LINK_RE.fullmatch(link.strip())
    if not match:
        raise ValueError("Expected https://t.me/<public-channel>/<message-id>")
    return match.group(1), int(match.group(2))


def _int_or_none(value: Any) -> int | None:
    return int(value) if value is not None else None


def _pack_file_id(dc_id: int | None, document_id: int | None, access_hash: int | None) -> str | None:
    """Encode the Telethon document reference in the Local Bot API file-id form."""
    if dc_id is None or document_id is None or access_hash is None or dc_id < 1:
        return None
    raw = struct.pack("<iiqqB", 4, dc_id, document_id, access_hash, 2)
    packed = bytearray()
    zero_count = 0
    for byte in raw:
        if byte == 0:
            zero_count += 1
        else:
            if zero_count:
                packed.extend((0, zero_count))
                zero_count = 0
            packed.append(byte)
    if zero_count:
        packed.extend((0, zero_count))
    encoded = base64.urlsafe_b64encode(bytes(packed)).decode("ascii").rstrip("=")
    return encoded


async def resolve_public_media(client: TelegramClient, link: str) -> ResolvedMedia:
    channel, message_id = parse_public_link(link)
    entity = await client.get_entity(channel)
    message = await client.get_messages(entity, ids=message_id)
    if message is None:
        raise LookupError(f"Telegram message not found: {link}")
    document = getattr(message, "document", None)
    file_info = getattr(message, "file", None)
    peer = getattr(message, "peer_id", None)
    document_id = _int_or_none(getattr(document, "id", None))
    access_hash = _int_or_none(getattr(document, "access_hash", None))
    dc_id = _int_or_none(getattr(document, "dc_id", None))
    return ResolvedMedia(
        channel=channel,
        chat_id=_int_or_none(getattr(peer, "channel_id", None)),
        message_id=int(message.id),
        media_type=type(message.media).__name__ if message.media else None,
        mime_type=getattr(file_info, "mime_type", None),
        file_name=getattr(file_info, "name", None),
        size=_int_or_none(getattr(file_info, "size", None)),
        document_id=document_id,
        access_hash=access_hash,
        dc_id=dc_id,
        file_id=_pack_file_id(dc_id, document_id, access_hash),
    )


async def resolve_from_environment(link: str) -> ResolvedMedia:
    client = TelegramClient(
        os.environ.get("TG_SESSION", "/tmp/tgvid-telethon-bot"),
        int(os.environ["TG_API_ID"]),
        os.environ["TG_API_HASH"],
    )
    try:
        await client.start(bot_token=os.environ["TG_BOT_TOKEN"])
        return await resolve_public_media(client, link)
    finally:
        await client.disconnect()


def to_dict(media: ResolvedMedia) -> dict[str, Any]:
    return asdict(media)
