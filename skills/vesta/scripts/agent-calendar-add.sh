#!/usr/bin/env bash
# Adds an event to a Home's shared calendar, over x0x (AS2 Add of a schema:Event). Check the calendar first (agent-calendar-read.sh)
# so you do not add what is already there; an identical title and time is refused. Needs Calendar > Write for that Home.
# Times: a timed event takes UTC instants such as 2026-10-20T18:30:00Z (or with an offset: 2026-10-20T19:30:00+01:00;
# a time with no offset is refused). An all-day event takes plain dates (2026-10-20) and its end is the LAST day.
#
# Usage: agent-calendar-add.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID --title TITLE --start START
#                              [--end END] [--location TEXT] [--description TEXT] [--repeat daily|weekly|monthly|yearly]
#                              [--id URN] [--timeout SECONDS]
#   Without --end an all-day event is that one day and a timed one lasts an hour. --id re-sends a command (same id => applied once).
#
# Waits for Vesta's reply and prints "<start>TAB<end>TAB<title>TAB<eventId>"; keep the eventId to change or remove it later.
# Exit: 0 accepted | 1 error | 3 Vesta rejected (reason on stderr; "unknown event" => no such event in that calendar)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
# Needs: bash, curl, jq, base64.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" title="" start="" end="" location="" description="" repeat="" command_id="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --title)           title="${2:?--title needs a value}"; shift 2 ;;
    --start)           start="${2:?--start needs a value}"; shift 2 ;;
    --end)             end="${2:?--end needs a value}"; shift 2 ;;
    --location)        location="${2:?--location needs a value}"; shift 2 ;;
    --description)     description="${2:?--description needs a value}"; shift 2 ;;
    --repeat)          repeat="${2:?--repeat needs a value}"; shift 2 ;;
    --id)              command_id="${2:?--id needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,15p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" ]] || die "--agent-instance, --to and --home are required"
[[ -n "$title" && -n "$start" ]] || die "--title and --start are required"
case "$repeat" in ""|daily|weekly|monthly|yearly) ;; *) die "--repeat must be daily, weekly, monthly or yearly" ;; esac

agent_instance "$instance"
[[ -n "$command_id" ]] || command_id="$(new_command_id)"

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg title "$title" --arg start "$start" --arg end "$end" \
  --arg location "$location" --arg description "$description" --arg repeat "$repeat" '
  {"@context": $ctx, "type": "Add", "id": $id,
   "object": ({"type": "schema:Event", "schema:name": $title, "schema:startDate": $start}
     + (if $end != "" then {"schema:endDate": $end} else {} end)
     + (if $location != "" then {"schema:location": $location} else {} end)
     + (if $description != "" then {"schema:description": $description} else {} end)
     + (if $repeat != "" then {"schema:eventSchedule": {"type": "schema:Schedule", "schema:repeatFrequency": ({"daily": "P1D", "weekly": "P1W", "monthly": "P1M", "yearly": "P1Y"}[$repeat])}} else {} end)),
   "target": {"type": "Collection", "id": ("urn:vesta:document:" + $home + ":calendar")}}')"

start_listener
send_command "$to" "$payload" >/dev/null
echo "command id: $command_id" >&2

reply="$(await_reply "$to" "$command_id" "$timeout")" || {
  echo "no reply from Vesta within ${timeout}s (is the app running, and do the x0x daemons trust each other?)" >&2
  exit 4
}
report_reply "$reply" || exit 3
jq -r '.result | [."schema:startDate", ."schema:endDate", ."schema:name", ."schema:identifier"] | @tsv' <<<"$reply"
