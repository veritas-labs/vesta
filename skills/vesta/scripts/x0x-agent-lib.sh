# Shared helpers for the agent-*.sh scripts (source, don't run). Bash only: zsh treats GID specially.
# Needs: curl, jq, base64.

die() { echo "error: $*" >&2; exit 1; }

CONTEXT='["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]'

# agent_instance NAME -> sets API and TOKEN from that x0x instance's data dir (Linux or macOS layout).
agent_instance() {
  local dir data_dir=""
  for dir in "$HOME/.local/share/x0x-$1" "$HOME/Library/Application Support/x0x-$1"; do
    [[ -f "$dir/api.port" ]] && { data_dir="$dir"; break; }
  done
  [[ -n "$data_dir" ]] || die "no x0x instance '$1' (no api.port under ~/.local/share/x0x-$1); start it with: x0x start --name $1"
  API="http://127.0.0.1:$(sed 's/.*://' "$data_dir/api.port" | tr -d '[:space:]')"
  TOKEN="$(tr -d '[:space:]' < "$data_dir/api-token")"
}

new_command_id() { echo "urn:uuid:$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)"; }

# call METHOD PATH JSON -> response body; non-zero exit on HTTP >= 400 (body still printed).
call() {
  curl -sS --fail-with-body -X "$1" "$API$2" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$3"
}

# send_command VESTA_AGENT_ID JSON_PAYLOAD -> prints request_id. Retries ~2 minutes while the peers
# are still exchanging capability adverts (recipient_ack_semantics_unavailable).
send_command() {
  local to="$1" payload="$2" body response deadline delay=2
  call POST /agents/connect "$(jq -cn --arg a "$to" '{agent_id: $a}')" >/dev/null || true
  body="$(jq -cn --arg a "$to" --arg p "$(printf '%s' "$payload" | base64 -w0)" '{agent_id: $a, payload: $p}')"
  deadline=$((SECONDS + 120))
  while true; do
    if response="$(call POST /direct/send "$body" 2>&1)"; then
      jq -r '.request_id // .' <<<"$response"
      return 0
    fi
    if [[ "$response" == *recipient_ack_semantics_unavailable* && $SECONDS -lt $deadline ]]; then
      echo "peer not ready yet (recipient_ack_semantics_unavailable); retrying in ${delay}s" >&2
      sleep "$delay"
      delay=$((delay < 16 ? delay * 2 : 16))
      continue
    fi
    die "send failed: $response"
  done
}

# start_listener: records this instance's incoming direct messages from now on. Call it BEFORE
# send_command so a fast reply can't be missed. Cleans up on exit.
start_listener() {
  EVENTS_FILE="$(mktemp)"
  curl -sSN -H "Authorization: Bearer $TOKEN" "$API/direct/events" > "$EVENTS_FILE" 2>/dev/null &
  LISTENER_PID=$!
  trap 'kill "${LISTENER_PID:-}" 2>/dev/null || true; rm -f "${EVENTS_FILE:-}"' EXIT
  sleep 1
}

# await_reply FROM_AGENT_ID COMMAND_ID TIMEOUT_SECONDS -> prints Vesta's reply JSON (an Accept or Reject
# whose inReplyTo is COMMAND_ID); returns 1 if none arrives in time.
await_reply() {
  local from="$1" id="$2" deadline=$((SECONDS + $3)) line reply
  while [[ $SECONDS -lt $deadline ]]; do
    while IFS= read -r line; do
      [[ "$line" == data:* ]] || continue
      reply="$(jq -r --arg from "$from" 'select(.sender == $from) | .payload' <<<"${line#data:}" 2>/dev/null \
        | base64 -d 2>/dev/null || true)"
      [[ -n "$reply" ]] || continue
      if [[ "$(jq -r '.inReplyTo // empty' <<<"$reply" 2>/dev/null)" == "$id" ]]; then
        printf '%s\n' "$reply"
        return 0
      fi
    done < "$EVENTS_FILE"
    sleep 1
  done
  return 1
}

# report_reply REPLY_JSON -> for a Reject, prints its summary to stderr and returns 3 (else 0).
report_reply() {
  if [[ "$(jq -r .type <<<"$1")" == "Reject" ]]; then
    echo "Vesta rejected the request: $(jq -r .summary <<<"$1")" >&2
    return 3
  fi
}
