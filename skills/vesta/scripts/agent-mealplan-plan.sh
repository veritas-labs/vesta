#!/usr/bin/env bash
# Sends Vesta a "plan this recipe" command (AS2 Add) as an x0x direct message: puts a cookbook recipe in a meal slot of a day.
# Needs the user to have switched on Meal planner > Write for that Home (Settings > Assistants).
# Vesta does not check that the recipe key exists in the cookbook; a key with no recipe shows as "removed" in the planner.
#
# Usage: agent-mealplan-plan.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID
#                               --date YYYY-MM-DD --slot Breakfast|Lunch|Dinner --recipe RECIPE_KEY
#                               [--id URN] [--timeout SECONDS]
#   --id re-sends a command (same id => answered once). Planning the same recipe in the same slot twice changes nothing.
#
# Waits for Vesta's reply and prints e.g. "Accepted: 2026-10-13 Dinner <recipe key>".
# Exit: 0 accepted | 1 error | 3 Vesta rejected (reason on stderr; "slot must be one of …" => use Breakfast, Lunch or Dinner)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
# Needs: bash, curl, jq, base64.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" date="" slot="" recipe="" command_id="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --date)           date="${2:?--date needs a value}"; shift 2 ;;
    --slot)           slot="${2:?--slot needs a value}"; shift 2 ;;
    --recipe)         recipe="${2:?--recipe needs a value}"; shift 2 ;;
    --id)             command_id="${2:?--id needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,12p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" && -n "$date" && -n "$slot" && -n "$recipe" ]] \
  || die "--agent-instance, --to, --home, --date, --slot and --recipe are required"

agent_instance "$instance"
[[ -n "$command_id" ]] || command_id="$(new_command_id)"

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg date "$date" --arg slot "$slot" --arg recipe "$recipe" '
  {
    "@context": $ctx,
    "type": "Add",
    "id": $id,
    "object": {"type": "Object", "schema:startDate": $date, "slot": $slot, "schema:recipe": {"type": "schema:Recipe", "id": $recipe}},
    "target": {"type": "Collection", "id": ("urn:vesta:document:" + $home + ":mealplan")}
  }')"

start_listener
send_command "$to" "$payload" >/dev/null
echo "command id: $command_id" >&2

reply="$(await_reply "$to" "$command_id" "$timeout")" || {
  echo "no reply from Vesta within ${timeout}s (is the app running, and do the x0x daemons trust each other?)" >&2
  exit 4
}
report_reply "$reply" || exit 3
jq -r '.result | "Accepted: \(."schema:startDate") \(.slot) \(."schema:recipe".id)"' <<<"$reply"
