#!/usr/bin/env bash
# Sends Vesta an ADR-009 "add to Pantry" command as an x0x direct message, from a
# separately-named x0x instance (the "agent"). See docs/troubleshooting/agent-commands.md.
# Bash, not zsh: zsh treats GID specially.
#
# Usage:
#   agent-pantry-add.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID --title TITLE
#                       [--category PRIMARY [--category SECONDARY]] [--size SIZE] [--gtin GTIN]
#                       [--qty N] [--id URN] [--timeout SECONDS]
#   --id re-sends a command (same id => applied once). Without it every run is a NEW add.
#
# Waits for Vesta's reply and prints e.g. "Accepted: Heinz Ketchup now has 3 in stock".
# Get --home from agent-list-homes.sh (over x0x) after asking the user which Home they mean.
# Exit: 0 accepted | 1 error | 3 Vesta rejected (reason on stderr; "not approved" => user must Allow in the app)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
# Needs: bash, curl, jq, base64.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" title="" size="" gtin="" qty="" command_id="" timeout=60
categories=()

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
    --timeout)        need "$@"; timeout="$2"; shift 2 ;;
    -h|--help)        sed -n '2,15p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done

[[ -n "$instance" && -n "$to" && -n "$home" && -n "$title" ]] \
  || die "--agent-instance, --to, --home and --title are required"
[[ ${#categories[@]} -le 2 ]] || die "at most two --category values (primary, secondary)"
[[ -z "$qty" || "$qty" =~ ^[0-9]+$ ]] || die "--qty must be a whole number"

agent_instance "$instance"
[[ -n "$command_id" ]] || command_id="$(new_command_id)"

payload="$(jq -cn \
  --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg title "$title" \
  --arg size "$size" --arg gtin "$gtin" --arg qty "$qty" \
  --args '
  {
    "@context": $ctx,
    "type": "Add",
    "id": $id,
    "object": ({
      "type": "schema:TypeAndQuantityNode",
      "schema:typeOfGood": ({
        "type": "schema:Product",
        "schema:name": $title
      }
      + (if ($ARGS.positional | length) > 0 then {"schema:category": $ARGS.positional} else {} end)
      + (if $gtin != "" then {"schema:gtin": $gtin} else {} end)
      + (if $size != "" then {"schema:size": $size} else {} end))
    } + (if $qty != "" then {"schema:amountOfThisGood": ($qty | tonumber)} else {} end)),
    "target": {"type": "Collection", "id": ("urn:vesta:document:" + $home + ":pantry")}
  }' "${categories[@]}")"

start_listener
send_command "$to" "$payload" >/dev/null
echo "command id: $command_id" >&2

reply="$(await_reply "$to" "$command_id" "$timeout")" || {
  echo "no reply from Vesta within ${timeout}s (is the app running, and do the x0x daemons trust each other?)" >&2
  exit 4
}
report_reply "$reply" || exit 3
jq -r '.result | "Accepted: \(."schema:name") now has \(."schema:amountOfThisGood") in stock"' <<<"$reply"
