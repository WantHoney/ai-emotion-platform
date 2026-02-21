param(
  [Parameter(Mandatory = $true)]
  [int]$TaskId,

  [Parameter(Mandatory = $true)]
  [string]$AccessToken,

  [string]$WsBaseUrl = "ws://127.0.0.1:8080",
  [int]$Connections = 30,
  [int]$DurationSec = 40,
  [int]$ReceiveTimeoutMs = 2000,
  [switch]$ShowErrors
)

if ($Connections -lt 1) {
  throw "Connections must be >= 1"
}
if ($DurationSec -lt 1) {
  throw "DurationSec must be >= 1"
}
if ([string]::IsNullOrWhiteSpace($AccessToken)) {
  throw "AccessToken is required"
}

$wsUrl = "$WsBaseUrl/ws/tasks/stream?taskId=$TaskId&accessToken=$([uri]::EscapeDataString($AccessToken))"
Write-Host "[stress] target=$wsUrl"
Write-Host "[stress] connections=$Connections durationSec=$DurationSec"

$env:STRESS_WS_URL = $wsUrl
$env:STRESS_CONNECTIONS = [string]$Connections
$env:STRESS_DURATION_SEC = [string]$DurationSec
$env:STRESS_TIMEOUT_MS = [string]$ReceiveTimeoutMs
$env:STRESS_SHOW_ERRORS = if ($ShowErrors) { "1" } else { "0" }

$pythonScript = @'
import asyncio
import base64
import os
import random
import struct
import time
from urllib.parse import urlparse


def ws_close_frame():
    mask = os.urandom(4)
    # client-to-server frame must be masked
    return b"\x88\x80" + mask


async def read_exactly(reader, n, timeout_s):
    return await asyncio.wait_for(reader.readexactly(n), timeout=timeout_s)


async def read_frame(reader, timeout_s):
    head = await read_exactly(reader, 2, timeout_s)
    b1, b2 = head[0], head[1]
    opcode = b1 & 0x0F
    masked = (b2 & 0x80) != 0
    length = b2 & 0x7F
    if length == 126:
        ext = await read_exactly(reader, 2, timeout_s)
        length = int.from_bytes(ext, "big")
    elif length == 127:
        ext = await read_exactly(reader, 8, timeout_s)
        length = int.from_bytes(ext, "big")
    mask_key = b""
    if masked:
        mask_key = await read_exactly(reader, 4, timeout_s)
    payload = b""
    if length > 0:
        payload = await read_exactly(reader, length, timeout_s)
    if masked and payload:
        payload = bytes(b ^ mask_key[i % 4] for i, b in enumerate(payload))
    return opcode, payload


async def run_client(client_id: int, url: str, duration_sec: int, timeout_ms: int):
    parsed = urlparse(url)
    host = parsed.hostname or "127.0.0.1"
    port = parsed.port or (443 if parsed.scheme == "wss" else 80)
    path = parsed.path or "/"
    if parsed.query:
        path += "?" + parsed.query

    message_count = 0
    reader = None
    writer = None

    try:
        reader, writer = await asyncio.open_connection(host, port)

        key = base64.b64encode(os.urandom(16)).decode("ascii")
        req = (
            f"GET {path} HTTP/1.1\r\n"
            f"Host: {host}:{port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            "\r\n"
        )
        writer.write(req.encode("ascii"))
        await writer.drain()

        headers_raw = await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), timeout=5.0)
        status_line = headers_raw.split(b"\r\n", 1)[0].decode("latin1", "replace")
        if " 101 " not in status_line:
            raise RuntimeError(f"handshake_failed: {status_line}")

        deadline = time.monotonic() + duration_sec
        default_timeout_s = max(0.05, timeout_ms / 1000.0)
        while time.monotonic() < deadline:
            timeout_s = min(default_timeout_s, max(0.05, deadline - time.monotonic()))
            try:
                opcode, payload = await read_frame(reader, timeout_s)
            except asyncio.TimeoutError:
                continue
            except asyncio.IncompleteReadError:
                break

            if opcode == 0x8:  # close
                break
            if opcode in (0x1, 0x2) and payload:
                message_count += 1
            # ignore ping/pong/continuation

        if writer is not None:
            try:
                writer.write(ws_close_frame())
                await writer.drain()
            except Exception:
                pass

        return {"id": client_id, "ok": True, "messages": message_count, "error": None}
    except Exception as e:
        return {"id": client_id, "ok": False, "messages": message_count, "error": str(e)}
    finally:
        if writer is not None:
            writer.close()
            try:
                await writer.wait_closed()
            except Exception:
                pass


async def main():
    url = os.environ["STRESS_WS_URL"]
    connections = int(os.environ["STRESS_CONNECTIONS"])
    duration_sec = int(os.environ["STRESS_DURATION_SEC"])
    timeout_ms = int(os.environ["STRESS_TIMEOUT_MS"])
    show_errors = os.environ.get("STRESS_SHOW_ERRORS", "0") == "1"

    tasks = [
        asyncio.create_task(run_client(i + 1, url, duration_sec, timeout_ms))
        for i in range(connections)
    ]
    results = await asyncio.gather(*tasks)

    total = len(results)
    ok_count = sum(1 for r in results if r["ok"])
    fail_count = total - ok_count
    total_messages = sum(int(r["messages"]) for r in results)
    avg_per_client = round(total_messages / total, 2) if total else 0

    print("")
    print("[stress] summary")
    print(f"  total clients : {total}")
    print(f"  success       : {ok_count}")
    print(f"  failed        : {fail_count}")
    print(f"  total messages: {total_messages}")
    print(f"  avg/client    : {avg_per_client}")

    if show_errors:
        errors = [r for r in results if not r["ok"]][:20]
        if errors:
            print("")
            print("[stress] first errors")
            print(f"{'id':>3} error")
            for item in errors:
                print(f"{item['id']:>3} {item['error']}")


if __name__ == "__main__":
    asyncio.run(main())
'@

$pythonScript | python -
