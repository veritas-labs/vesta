#!/usr/bin/env bash
# Removes an event from a Home's shared calendar, over x0x (AS2 Remove of a schema:Event), by its id from agent-calendar-read.sh.
# Removing is permanent for every device in the Home: only do it when the user asked you to, and say which event you removed.
# Needs Calendar > Write for that Home.
#
# Usage: agent-calendar-remove.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID --event EVENT_ID [--id URN] [--timeout SECONDS]
#
# Waits for Vesta's reply and prints "Removed: <title> (<eventId>)".
# Exit: 0 accepted | 1 error | 3 Vesta rejected (reason on stderr; "unknown event" => no such event in that calendar)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
# Needs: bash, curl, jq, base64.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" event="" command_id="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --event)           event="${2:?--event needs a value}"; shift 2 ;;
    --id)              command_id="${2:?--id needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,11p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" ]] || die "--agent-instance, --to and --home are required"
[[ -n "$event" ]] || die "--event is required"

agent_instance "$instance"
[[ -n "$command_id" ]] || command_id="$(new_command_id)"

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg event "$event" '
  {"@context": $ctx, "type": "Remove", "id": $id,
   "object": {"type": "schema:Event", "schema:identifier": $event},
   "target": {"type": "Collection", "id": ("urn:vesta:document:" + $home + ":calendar")}}')"

start_listener
send_command "$to" "$payload" >/dev/null
echo "command id: $command_id" >&2

reply="$(await_reply "$to" "$command_id" "$timeout")" || {
  echo "no reply from Vesta within ${timeout}s (is the app running, and do the x0x daemons trust each other?)" >&2
  exit 4
}
report_reply "$reply" || exit 3
jq -r '.result | "Removed: \(."schema:name") (\(."schema:identifier"))"' <<<"$reply"
