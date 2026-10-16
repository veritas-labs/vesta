#!/usr/bin/env bash
# Sends Vesta an "add a task" command (AS2 Add of a schema:Action) as an x0x direct message: a new shared task or chore.
# Needs the user to have switched on Tasks > Write for that Home (Settings > Assistants). Read the tasks first: a task with
# the same name on the same day is refused ("task already exists").
#
# Usage: agent-tasks-add.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID --title TITLE
#                           [--due YYYY-MM-DD] [--description TEXT] [--id URN] [--timeout SECONDS]
#   --id re-sends a command (same id => applied once). Without it every run is a NEW command.
#
# Waits for Vesta's reply and prints e.g. "Accepted: Take the bins out (id ..., due 2026-10-14)".
# Exit: 0 accepted | 1 error | 3 Vesta rejected (reason on stderr) | 4 no reply in time
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" title="" due="" description="" command_id="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --title)          title="${2:?--title needs a value}"; shift 2 ;;
    --due)            due="${2:?--due needs a value}"; shift 2 ;;
    --description)    description="${2:?--description needs a value}"; shift 2 ;;
    --id)             command_id="${2:?--id needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,11p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" && -n "$title" ]] || die "--agent-instance, --to, --home and --title are required"
[[ -z "$due" || "$due" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || die "--due must look like 2026-10-14"

agent_instance "$instance"
[[ -n "$command_id" ]] || command_id="$(new_command_id)"

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg title "$title" \
  --arg due "$due" --arg description "$description" '
  {
    "@context": $ctx,
    "type": "Add",
    "id": $id,
    "object": ({"type": "schema:Action", "schema:name": $title}
      + (if $due != "" then {"schema:endTime": $due} else {} end)
      + (if $description != "" then {"schema:description": $description} else {} end)),
    "target": {"type": "Collection", "id": ("urn:vesta:document:" + $home + ":tasks")}
  }')"

start_listener
send_command "$to" "$payload" >/dev/null
echo "command id: $command_id" >&2

reply="$(await_reply "$to" "$command_id" "$timeout")" || {
  echo "no reply from Vesta within ${timeout}s (is the app running, and do the x0x daemons trust each other?)" >&2
  exit 4
}
report_reply "$reply" || exit 3
jq -r '.result | "Accepted: \(."schema:name") (id \(."schema:identifier")\(if ."schema:endTime" then ", due \(."schema:endTime")" else "" end))"' <<<"$reply"
