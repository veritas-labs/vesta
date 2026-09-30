# Talking to Vesta with plain x0x REST calls

What `scripts/x0x-agent-lib.sh` does, for harnesses that can't run bash. `<name>` is your x0x instance.

## Find your daemon's API address and token

Read them from your instance's data directory — do not guess:

```bash
D=~/.local/share/x0x-<name>            # macOS: ~/Library/Application Support/x0x-<name>
API="http://127.0.0.1:$(sed 's/.*://' $D/api.port | tr -d '[:space:]')"
TOKEN="$(tr -d '[:space:]' < $D/api-token)"
```

An empty or wrong `$API`/`$TOKEN` makes every call below fail or hit the wrong daemon.

## The flow

1. **Listen first**, so a fast reply isn't missed (Server-Sent Events, keep it open in the background):
   ```bash
   curl -sN -H "Authorization: Bearer $TOKEN" "$API/direct/events"
   ```
   Each event is a `data: {…}` line; `payload` is base64. Decode it and match `inReplyTo` to your command id, and
   check `sender` is Vesta's agent id.
2. **Connect** (harmless if already connected):
   ```bash
   curl -X POST "$API/agents/connect" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"agent_id": "<vesta-agent-id>"}'
   ```
3. **Send** the message from [messages.md](messages.md), base64-encoded:
   ```bash
   curl -X POST "$API/direct/send" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d "{\"agent_id\": \"<vesta-agent-id>\", \"payload\": \"$(printf '%s' "$JSON" | base64 -w0)\"}"
   ```
   (`base64 -w0` on Linux; on macOS use `base64 | tr -d '\n'`.) An error containing
   `recipient_ack_semantics_unavailable` means the peers haven't finished exchanging capability adverts yet: wait,
   back off (2 s, 4 s, … up to 16 s) and retry for up to two minutes.
4. **Wait for the reply** (up to about a minute) and handle it as in the outcomes table in `SKILL.md`.
