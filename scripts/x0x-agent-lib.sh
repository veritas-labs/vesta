# Shared helpers for the agent-*.sh scripts (source, don't run). Bash only: zsh treats GID specially.
# Needs: curl, jq, base64.

die() { echo "error: $*" >&2; exit 1; }

CONTEXT='["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]'

# agent_instance NAME -> sets API and TOKEN from that x0x instance's data dir.
agent_instance() {
  local data_dir="$HOME/.local/share/x0x-$1"
  [[ -f "$data_dir/api.port" ]] || die "no x0x instance '$1' (missing $data_dir/api.port); start it with: x0x start --name $1"
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
