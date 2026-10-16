#!/usr/bin/env bash
# Sends Vesta a "take this off the shopping list" command (AS2 Remove) as an x0x direct message: the whole line goes.
# Needs the user to have switched on Shopping list > Write for that Home (Settings > Assistants). The item must be on the list.
#
# Usage: agent-shopping-remove.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID --title TITLE [--gtin GTIN]
#                                       [--id URN] [--timeout SECONDS]
#   --id re-sends a command (same id => applied once). Removing is safe to repeat (a second one just says unknown item).
#
# Waits for Vesta's reply and prints e.g. "Accepted: Heinz Ketchup is off the list".
# Exit: 0 accepted | 1 error | 3 Vesta rejected (reason on stderr; "unknown item" => it isn't on the list)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
# Needs: bash, curl, jq, base64.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" title="" gtin="" command_id="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --title)          title="${2:?--title needs a value}"; shift 2 ;;
    --gtin)           gtin="${2:?--gtin needs a value}"; shift 2 ;;
    --id)             command_id="${2:?--id needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,10p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" && -n "$title" ]] || die "--agent-instance, --to, --home and --title are required"

agent_instance "$instance"
[[ -n "$command_id" ]] || command_id="$(new_command_id)"

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg title "$title" \
  --arg gtin "$gtin" '
  {
    "@context": $ctx,
    "type": "Remove",
    "id": $id,
    "object": {
      "type": "schema:TypeAndQuantityNode",
      "schema:typeOfGood": ({"type": "schema:Product", "schema:name": $title}
        + (if $gtin != "" then {"schema:gtin": $gtin} else {} end))
    },
    "target": {"type": "Collection", "id": ("urn:vesta:document:" + $home + ":shopping-list")}
  }')"

start_listener
send_command "$to" "$payload" >/dev/null
echo "command id: $command_id" >&2

reply="$(await_reply "$to" "$command_id" "$timeout")" || {
  echo "no reply from Vesta within ${timeout}s (is the app running, and do the x0x daemons trust each other?)" >&2
  exit 4
}
report_reply "$reply" || exit 3
jq -r '.result | "Accepted: \(."schema:name") is off the list"' <<<"$reply"
