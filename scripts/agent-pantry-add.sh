#!/usr/bin/env bash
# Sends Vesta an ADR-009 "add to Pantry" command as an x0x direct message, from a
# separately-named x0x instance (the "agent"). See docs/troubleshooting/agent-commands.md.
# Bash, not zsh: zsh treats GID specially.
#
# Usage:
#   agent-pantry-add.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID --title TITLE
#                       [--category PRIMARY [--category SECONDARY]] [--size SIZE] [--gtin GTIN]
#                       [--qty N] [--id URN]   # --id re-sends a command (same id => applied once)
#
# Needs: bash, curl, jq, base64. Prints the daemon's request_id on success.
set -euo pipefail

instance="" to="" home="" title="" size="" gtin="" qty="" command_id=""
categories=()

die() { echo "error: $*" >&2; exit 1; }
need() { [[ $# -ge 2 ]] || die "$1 needs a value"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) need "$@"; instance="$2"; shift 2 ;;
    --to)             need "$@"; to="$2"; shift 2 ;;
    --home)           need "$@"; home="$2"; shift 2 ;;
    --title)          need "$@"; title="$2"; shift 2 ;;
    --category)       need "$@"; categories+=("$2"); shift 2 ;;
    --size)           need "$@"; size="$2"; shift 2 ;;
    --gtin)           need "$@"; gtin="$2"; shift 2 ;;
    --qty)            need "$@"; qty="$2"; shift 2 ;;
    --id)             need "$@"; command_id="$2"; shift 2 ;;
    -h|--help)        sed -n '2,11p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done

[[ -n "$instance" && -n "$to" && -n "$home" && -n "$title" ]] \
  || die "--agent-instance, --to, --home and --title are required"
[[ ${#categories[@]} -le 2 ]] || die "at most two --category values (primary, secondary)"
[[ -z "$qty" || "$qty" =~ ^[0-9]+$ ]] || die "--qty must be a whole number"

data_dir="$HOME/.local/share/x0x-$instance"
[[ -f "$data_dir/api.port" ]] || die "no x0x instance '$instance' (missing $data_dir/api.port); start it with: x0x start --name $instance"
api="http://127.0.0.1:$(sed 's/.*://' "$data_dir/api.port" | tr -d '[:space:]')"
token="$(tr -d '[:space:]' < "$data_dir/api-token")"

[[ -n "$command_id" ]] || command_id="urn:uuid:$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)"

payload="$(jq -cn \
  --arg id "$command_id" --arg home "$home" --arg title "$title" \
  --arg size "$size" --arg gtin "$gtin" --arg qty "$qty" \
  --args '
  {
    "@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
    "type": "Add",
    "id": $id,
    "object": {
      "type": "schema:TypeAndQuantityNode",
      "schema:typeOfGood": ({
        "type": "schema:Product",
        "schema:name": $title
      }
      + (if ($ARGS.positional | length) > 0 then {"schema:category": $ARGS.positional} else {} end)
      + (if $gtin != "" then {"schema:gtin": $gtin} else {} end)
      + (if $size != "" then {"schema:size": $size} else {} end))
    } + (if $qty != "" then {"schema:amountOfThisGood": ($qty | tonumber)} else {} end),
    "target": {"type": "Collection", "id": ("urn:vesta:document:" + $home + ":pantry")}
  }' "${categories[@]}")"

call() { # method path json-body -> body on stdout; exits non-zero on HTTP >= 400
  curl -sS --fail-with-body -X "$1" "$api$2" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$3"
}

call POST /agents/connect "$(jq -cn --arg a "$to" '{agent_id: $a}')" >/dev/null || true

body="$(jq -cn --arg a "$to" --arg p "$(printf '%s' "$payload" | base64 -w0)" '{agent_id: $a, payload: $p}')"
deadline=$((SECONDS + 120)) delay=2
while true; do
  if response="$(call POST /direct/send "$body" 2>&1)"; then
    echo "command id: $command_id" >&2
    jq -r '.request_id // .' <<<"$response"
    exit 0
  fi
  if [[ "$response" == *recipient_ack_semantics_unavailable* && $SECONDS -lt $deadline ]]; then
    echo "peer not ready yet (recipient_ack_semantics_unavailable); retrying in ${delay}s" >&2
    sleep "$delay"
    delay=$((delay < 16 ? delay * 2 : 16))
    continue
  fi
  die "send failed: $response"
done
