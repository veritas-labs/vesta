#!/usr/bin/env bash
# Changes an event on a Home's shared calendar, over x0x (AS2 Update of a schema:Event): only the fields you give change. Moving
# --start alone moves the whole event (same length). Needs Calendar > Write for that Home. Find the id with agent-calendar-read.sh.
# Times: a timed event takes UTC instants such as 2026-10-20T18:30:00Z (or with an offset: 2026-10-20T19:30:00+01:00;
# a time with no offset is refused). An all-day event takes plain dates (2026-10-20) and its end is the LAST day.
#
# Usage: agent-calendar-update.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID --event EVENT_ID
#                                 [--title TEXT] [--start START] [--end END] [--location TEXT] [--description TEXT]
#                                 [--repeat none|daily|weekly|monthly|yearly] [--id URN] [--timeout SECONDS]
#   Giving a blank --location or --description clears it. Changing the same fields to the same values again is harmless.
#
# Waits for Vesta's reply and prints "<start>TAB<end>TAB<title>TAB<eventId>" as it is afterwards.
# Exit: 0 accepted | 1 error | 3 Vesta rejected (reason on stderr; "unknown event" => no such event in that calendar)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
# Needs: bash, curl, jq, base64.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" event="" command_id="" timeout=60
set_title=0 set_start=0 set_end=0 set_location=0 set_description=0 set_repeat=0
title="" start="" end="" location="" description="" repeat=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --event)           event="${2:?--event needs a value}"; shift 2 ;;
    --title)           title="${2?--title needs a value}"; set_title=1; shift 2 ;;
    --start)           start="${2?--start needs a value}"; set_start=1; shift 2 ;;
    --end)             end="${2?--end needs a value}"; set_end=1; shift 2 ;;
    --location)        location="${2?--location needs a value}"; set_location=1; shift 2 ;;
    --description)     description="${2?--description needs a value}"; set_description=1; shift 2 ;;
    --repeat)          repeat="${2?--repeat needs a value}"; set_repeat=1; shift 2 ;;
    --id)              command_id="${2:?--id needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,15p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" ]] || die "--agent-instance, --to and --home are required"
[[ -n "$event" ]] || die "--event is required"
(( set_title + set_start + set_end + set_location + set_description + set_repeat > 0 )) || die "give at least one field to change"
case "$repeat" in ""|none|daily|weekly|monthly|yearly) ;; *) die "--repeat must be none, daily, weekly, monthly or yearly" ;; esac

agent_instance "$instance"
[[ -n "$command_id" ]] || command_id="$(new_command_id)"

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg event "$event" \
  --arg title "$title" --arg start "$start" --arg end "$end" --arg location "$location" --arg description "$description" --arg repeat "$repeat" \
  --argjson st "$set_title" --argjson ss "$set_start" --argjson se "$set_end" --argjson sl "$set_location" --argjson sd "$set_description" --argjson sr "$set_repeat" '
  {"@context": $ctx, "type": "Update", "id": $id,
   "object": ({"type": "schema:Event", "schema:identifier": $event}
     + (if $st == 1 then {"schema:name": $title} else {} end)
     + (if $ss == 1 then {"schema:startDate": $start} else {} end)
     + (if $se == 1 then {"schema:endDate": $end} else {} end)
     + (if $sl == 1 then {"schema:location": $location} else {} end)
     + (if $sd == 1 then {"schema:description": $description} else {} end)
     + (if $sr == 1 then {"schema:eventSchedule": ({"type": "schema:Schedule"} + (if $repeat == "none" then {} else {"schema:repeatFrequency": ({"daily": "P1D", "weekly": "P1W", "monthly": "P1M", "yearly": "P1Y"}[$repeat])} end))} else {} end)),
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
