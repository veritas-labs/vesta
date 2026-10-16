#!/usr/bin/env bash
# Asks Vesta what a Home's meal plan holds, over x0x, and prints one line per planned recipe, earliest day first:
# "<date>TAB<slot>TAB<recipe key>" (at most 200). The recipe key is the cookbook's key for the recipe
# (agent-cookbook-read.sh, if the user granted Cookbook Read, shows what each key is).
# Needs the user to have switched on Meal planner > Read for that Home (Settings > Assistants).
#
# Usage: agent-mealplan-read.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID
#                               [--from YYYY-MM-DD] [--to-date YYYY-MM-DD] [--timeout SECONDS]
# --from / --to-date limit the days shown (inclusive). Prints "<n> of <total> meals" on stderr. Reading changes nothing.
# Exit: 0 ok | 1 error | 3 Vesta rejected (reason on stderr; "not permitted" => the user hasn't granted Meal planner Read)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" from="" until="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --from)           from="${2:?--from needs a value}"; shift 2 ;;
    --to-date)        until="${2:?--to-date needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,11p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" ]] || die "--agent-instance, --to and --home are required"

agent_instance "$instance"
command_id="$(new_command_id)"
start_listener

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg from "$from" --arg until "$until" '
  {"@context": $ctx, "type": "Read", "id": $id,
   "object": ({"type": "Collection", "id": ("urn:vesta:document:" + $home + ":mealplan")}
     + (if $from != "" then {"schema:startDate": $from} else {} end)
     + (if $until != "" then {"schema:endDate": $until} else {} end))}')"
send_command "$to" "$payload" >/dev/null

reply="$(await_reply "$to" "$command_id" "$timeout")" || {
  echo "no reply from Vesta within ${timeout}s (is the app running, and do the x0x daemons trust each other?)" >&2
  exit 4
}
report_reply "$reply" || exit 3
jq -r '.result | "\(.items | length) of \(.totalItems) meals"' <<<"$reply" >&2
jq -r '.result.items[] | [."schema:startDate", .slot, ."schema:recipe".id] | @tsv' <<<"$reply"
