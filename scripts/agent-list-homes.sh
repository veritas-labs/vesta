#!/usr/bin/env bash
# Asks Vesta which Homes it has, over x0x, and prints one "<homeId><TAB><name>" line per Home.
# Ask your user which Home they mean, then pass its id as --home to agent-pantry-add.sh.
# See docs/troubleshooting/agent-commands.md.
#
# Usage: agent-list-homes.sh --agent-instance NAME --to VESTA_AGENT_ID [--timeout SECONDS]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,7p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" ]] || die "--agent-instance and --to are required"

agent_instance "$instance"
command_id="$(new_command_id)"
events="$(mktemp)"
trap 'kill "${listener:-}" 2>/dev/null || true; rm -f "$events"' EXIT

# Listen BEFORE sending, so the reply can't be missed.
curl -sSN -H "Authorization: Bearer $TOKEN" "$API/direct/events" > "$events" 2>/dev/null &
listener=$!
sleep 1

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" \
  '{"@context": $ctx, "type": "Read", "id": $id, "object": {"type": "Collection", "id": "urn:vesta:homes"}}')"
send_command "$to" "$payload" >/dev/null

deadline=$((SECONDS + timeout))
while [[ $SECONDS -lt $deadline ]]; do
  while IFS= read -r line; do
    [[ "$line" == data:* ]] || continue
    reply="$(jq -r --arg id "$command_id" --arg from "$to" '
      select(.sender == $from) | .payload' <<<"${line#data:}" 2>/dev/null | base64 -d 2>/dev/null || true)"
    [[ -n "$reply" ]] || continue
    [[ "$(jq -r '.inReplyTo // empty' <<<"$reply" 2>/dev/null)" == "$command_id" ]] || continue
    if [[ "$(jq -r .type <<<"$reply")" == "Reject" ]]; then
      die "Vesta rejected the request: $(jq -r .summary <<<"$reply")"
    fi
    jq -r '.result.items[] | [."schema:identifier", ."schema:name"] | @tsv' <<<"$reply"
    exit 0
  done < "$events"
  sleep 1
done
die "no reply from Vesta within ${timeout}s (is the app running, and is this agent on its allowlist? see docs/troubleshooting/agent-commands.md)"
