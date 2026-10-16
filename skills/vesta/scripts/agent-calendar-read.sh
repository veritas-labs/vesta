#!/usr/bin/env bash
# Asks Vesta what is on a Home's shared calendar, over x0x (AS2 Read), and prints one line per event, earliest first:
# "<start>TAB<end>TAB<title>TAB<eventId>TAB<all-day yes|no>TAB<repeat>TAB<location>" (at most 200; stderr says how many of the total).
# Needs the user to have switched on Calendar > Read for that Home (Settings > Assistants). The user's PRIVATE calendar is never visible.
# Times: a timed event takes UTC instants such as 2026-10-20T18:30:00Z (or with an offset: 2026-10-20T19:30:00+01:00;
# a time with no offset is refused). An all-day event takes plain dates (2026-10-20) and its end is the LAST day.
#
# Usage: agent-calendar-read.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID [--from DATE] [--until DATE] [--timeout SECONDS]
#   --from/--until keep events touching that range, both ends inclusive; a plain date covers the whole day (UTC).
#   Reading changes nothing, so re-running is always safe.
# Exit: 0 accepted | 1 error | 3 Vesta rejected (reason on stderr; "unknown event" => no such event in that calendar)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
# Needs: bash, curl, jq, base64.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" from="" until="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --from)            from="${2:?--from needs a value}"; shift 2 ;;
    --until)           until="${2:?--until needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,13p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" ]] || die "--agent-instance, --to and --home are required"


agent_instance "$instance"
command_id="$(new_command_id)"
start_listener

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg from "$from" --arg until "$until" '
  {"@context": $ctx, "type": "Read", "id": $id,
   "object": ({"type": "Collection", "id": ("urn:vesta:document:" + $home + ":calendar")}
     + (if $from != "" then {"schema:startDate": $from} else {} end)
     + (if $until != "" then {"schema:endDate": $until} else {} end))}')"
send_command "$to" "$payload" >/dev/null

reply="$(await_reply "$to" "$command_id" "$timeout")" || {
  echo "no reply from Vesta within ${timeout}s (is the app running, and do the x0x daemons trust each other?)" >&2
  exit 4
}
report_reply "$reply" || exit 3
jq -r '.result | "\(.items | length) of \(.totalItems) events"' <<<"$reply" >&2
jq -r '.result.items[] | [."schema:startDate", ."schema:endDate", ."schema:name", ."schema:identifier", (if .allDay then "yes" else "no" end), (."schema:eventSchedule"."schema:repeatFrequency" // ""), (."schema:location" // "")] | @tsv' <<<"$reply"
