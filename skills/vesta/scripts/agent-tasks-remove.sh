#!/usr/bin/env bash
# Sends Vesta a "delete this task" command (AS2 Remove of a schema:Action) as an x0x direct message. Its history stays.
# Needs Tasks > Write for that Home. Deleting is for the whole household: only on the user's request.
#
# Usage: agent-tasks-remove.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID (--task TASK_ID | --title TITLE)
#                              [--id URN] [--timeout SECONDS]
#
# Waits for Vesta's reply and prints e.g. "Accepted: Take the bins out is deleted".
# Exit: 0 accepted | 1 error | 3 Vesta rejected (reason on stderr; "unknown task" => it isn't there) | 4 no reply in time
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" title="" task="" command_id="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --title)          title="${2:?--title needs a value}"; shift 2 ;;
    --task)           task="${2:?--task needs a value}"; shift 2 ;;
    --id)             command_id="${2:?--id needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,8p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" ]] || die "--agent-instance, --to and --home are required"
[[ -n "$task" || -n "$title" ]] || die "--task or --title is required"

agent_instance "$instance"
[[ -n "$command_id" ]] || command_id="$(new_command_id)"

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg title "$title" --arg task "$task" '
  {
    "@context": $ctx,
    "type": "Remove",
    "id": $id,
    "object": ({"type": "schema:Action"}
      + (if $task != "" then {"schema:identifier": $task} else {"schema:name": $title} end)),
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
jq -r '.result | "Accepted: \(."schema:name") is deleted"' <<<"$reply"
