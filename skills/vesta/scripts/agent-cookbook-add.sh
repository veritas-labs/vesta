#!/usr/bin/env bash
# Adds a recipe to a Home's cookbook, over x0x (AS2 Add of a schema:Recipe).
# Needs the user to have switched on Cookbook > Write for that Home (Settings > Assistants).
#
# Usage: agent-cookbook-add.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID --title TITLE
#          [--description TEXT] [--category Breakfast|Lunch|Dinner|Starter|Side|Dessert|Baking|Snack|Drink|Other]
#          [--servings N] [--prep PT15M] [--cook PT1H30M] [--source URL]
#          [--ingredient TEXT]... [--step TEXT]... [--keyword TEXT]... [--id URN] [--timeout SECONDS]
#   --ingredient and --step repeat, in order; an ingredient is free text such as "200g plain flour".
#   --prep/--cook are ISO 8601 durations (at most 7 days). Recipe names are unique per cookbook (ignoring case).
#   --id re-sends a command (same id => applied once). Without it every run is a NEW add.
#
# Waits for Vesta's reply and prints "Added: <name> (<recipeId>)".
# Exit: 0 accepted | 1 error | 3 Vesta rejected (reason on stderr; "recipe already exists" => it's there already)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
# Needs: bash, curl, jq, base64.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" title="" description="" category="" servings="" prep="" cook="" source_url="" command_id="" timeout=60
ingredients=() steps=() keywords=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --title)          title="${2:?--title needs a value}"; shift 2 ;;
    --description)    description="${2:?--description needs a value}"; shift 2 ;;
    --category)       category="${2:?--category needs a value}"; shift 2 ;;
    --servings)       servings="${2:?--servings needs a value}"; shift 2 ;;
    --prep)           prep="${2:?--prep needs a value}"; shift 2 ;;
    --cook)           cook="${2:?--cook needs a value}"; shift 2 ;;
    --source)         source_url="${2:?--source needs a value}"; shift 2 ;;
    --ingredient)     ingredients+=("${2:?--ingredient needs a value}"); shift 2 ;;
    --step)           steps+=("${2:?--step needs a value}"); shift 2 ;;
    --keyword)        keywords+=("${2:?--keyword needs a value}"); shift 2 ;;
    --id)             command_id="${2:?--id needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,15p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" && -n "$title" ]] || die "--agent-instance, --to, --home and --title are required"
[[ -z "$servings" || "$servings" =~ ^[0-9]+$ ]] || die "--servings must be a whole number"

agent_instance "$instance"
[[ -n "$command_id" ]] || command_id="$(new_command_id)"

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg title "$title" \
  --arg description "$description" --arg category "$category" --arg servings "$servings" \
  --arg prep "$prep" --arg cook "$cook" --arg source "$source_url" \
  --argjson ingredients "$(printf '%s\n' "${ingredients[@]:-}" | jq -R . | jq -s 'map(select(. != ""))')" \
  --argjson steps "$(printf '%s\n' "${steps[@]:-}" | jq -R . | jq -s 'map(select(. != ""))')" \
  --argjson keywords "$(printf '%s\n' "${keywords[@]:-}" | jq -R . | jq -s 'map(select(. != ""))')" '
  {
    "@context": $ctx,
    "type": "Add",
    "id": $id,
    "object": ({"type": "schema:Recipe", "schema:name": $title}
      + (if $description != "" then {"schema:description": $description} else {} end)
      + (if $category != "" then {"schema:recipeCategory": $category} else {} end)
      + (if $servings != "" then {"schema:recipeYield": ($servings | tonumber)} else {} end)
      + (if $prep != "" then {"schema:prepTime": $prep} else {} end)
      + (if $cook != "" then {"schema:cookTime": $cook} else {} end)
      + (if $source != "" then {"schema:isBasedOn": $source} else {} end)
      + (if ($ingredients | length) > 0 then {"schema:recipeIngredient": $ingredients} else {} end)
      + (if ($steps | length) > 0 then {"schema:recipeInstructions": $steps} else {} end)
      + (if ($keywords | length) > 0 then {"schema:keywords": $keywords} else {} end)),
    "target": {"type": "Collection", "id": ("urn:vesta:document:" + $home + ":cookbook")}
  }')"

start_listener
send_command "$to" "$payload" >/dev/null
echo "command id: $command_id" >&2

reply="$(await_reply "$to" "$command_id" "$timeout")" || {
  echo "no reply from Vesta within ${timeout}s (is the app running, and do the x0x daemons trust each other?)" >&2
  exit 4
}
report_reply "$reply" || exit 3
jq -r '.result | "Added: \(."schema:name") (\(."schema:identifier"))"' <<<"$reply"
