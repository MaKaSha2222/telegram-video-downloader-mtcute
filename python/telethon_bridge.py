"""Chaquopy entry point for the Android Telethon resolver."""
from __future__ import annotations

import asyncio
import json
from pathlib import Path

from telethon import TelegramClient

from telethon_resolver import parse_public_link, resolve_public_media, to_dict


async def _client(api_id: int, api_hash: str, bot_token: str, session_dir: str):
    session_root = Path(session_dir)
    session_root.mkdir(parents=True, exist_ok=True)
    client = TelegramClient(str(session_root / "telethon-bot"), int(api_id), api_hash)
    await client.start(bot_token=bot_token)
    return client


async def _resolve(api_id: int, api_hash: str, bot_token: str, link: str, session_dir: str):
    client = await _client(api_id, api_hash, bot_token, session_dir)
    try:
        return to_dict(await resolve_public_media(client, link))
    finally:
        await client.disconnect()


async def _download(api_id: int, api_hash: str, bot_token: str, link: str, session_dir: str, destination: str):
    channel, message_id = parse_public_link(link)
    client = await _client(api_id, api_hash, bot_token, session_dir)
    try:
        entity = await client.get_entity(channel)
        message = await client.get_messages(entity, ids=message_id)
        if message is None or not message.media:
            raise LookupError(f"Telegram media not found: {link}")
        path = await client.download_media(message, file=destination)
        if not path:
            raise RuntimeError("Telethon returned no downloaded file path")
        return {"path": str(path), "size": Path(path).stat().st_size}
    finally:
        await client.disconnect()


def resolve_media(api_id: int, api_hash: str, bot_token: str, link: str, session_dir: str) -> str:
    return json.dumps(asyncio.run(_resolve(api_id, api_hash, bot_token, link, session_dir)), ensure_ascii=False)


def download_media(api_id: int, api_hash: str, bot_token: str, link: str, session_dir: str, destination: str) -> str:
    return json.dumps(asyncio.run(_download(api_id, api_hash, bot_token, link, session_dir, destination)), ensure_ascii=False)
