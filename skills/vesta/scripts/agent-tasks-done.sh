#!/usr/bin/env bash
# Sends Vesta a "mark this task done / not done" command (AS2 Update with a schema:actionStatus) as an x0x direct message.
# For a repeating task this is its current occurrence. Needs Tasks > Write for that Home. Safe to repeat.
#
# Usage: agent-tasks-done.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID (--task TASK_ID | --title TITLE)
#                            [--undo] [--id URN] [--timeout SECONDS]
#   --task is the id from agent-tasks-read.sh (use it when several tasks share a name); --undo marks it not done.
#
# Waits for Vesta's reply and prints e.g. "Accepted: Take the bins out is done".
# Exit: 0 accepted | 1 error | 3 Vesta rejected (reason on stderr; "unknown task" => read the tasks again) | 4 no reply in time
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" title="" task="" undo="" command_id="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --title)          title="${2:?--title needs a value}"; shift 2 ;;
    --task)           task="${2:?--task needs a value}"; shift 2 ;;
    --undo)           undo=1; shift ;;
    --id)             command_id="${2:?--id needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,10p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" ]] || die "--agent-instance, --to and --home are required"
[[ -n "$task" || -n "$title" ]] || die "--task or --title is required"

agent_instance "$instance"
[[ -n "$command_id" ]] || command_id="$(new_command_id)"

status="schema:CompletedActionStatus"; [[ -z "$undo" ]] || status="schema:PotentialActionStatus"
payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg title "$title" \
  --arg task "$task" --arg status "$status" '
  {
    "@context": $ctx,
    "type": "Update",
    "id": $id,
    "object": ({"type": "schema:Action", "schema:actionStatus": $status}
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
jq -r '.result | "Accepted: \(."schema:name") is \(if ."schema:actionStatus" == "schema:CompletedActionStatus" then "done" else "not done" end)"' <<<"$reply"
