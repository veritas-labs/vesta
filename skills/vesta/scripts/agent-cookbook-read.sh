#!/usr/bin/env bash
# Asks Vesta for a Home's cookbook, over x0x. Without --recipe it lists the recipes, one line each:
# "<recipeId>TAB<name>TAB<category>TAB<servings>TAB<total time>TAB<keywords>" (by name, at most 200).
# With --recipe <recipeId> it prints that one recipe in full (ingredients and method). Photos and videos are not sent.
# Needs the user to have switched on Cookbook > Read for that Home (Settings > Assistants).
#
# Usage: agent-cookbook-read.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID [--recipe RECIPE_ID] [--timeout SECONDS]
# Reading changes nothing, so re-running is always safe.
# Exit: 0 ok | 1 error | 3 Vesta rejected (reason on stderr; "not permitted" => the user hasn't granted Cookbook Read)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" recipe="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --recipe)         recipe="${2:?--recipe needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,10p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" ]] || die "--agent-instance, --to and --home are required"

agent_instance "$instance"
command_id="$(new_command_id)"
start_listener

object_id="urn:vesta:document:${home}:cookbook"
[[ -z "$recipe" ]] || object_id="${object_id}:${recipe}"
payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg object "$object_id" \
  '{"@context": $ctx, "type": "Read", "id": $id, "object": {"type": "Collection", "id": $object}}')"
send_command "$to" "$payload" >/dev/null

reply="$(await_reply "$to" "$command_id" "$timeout")" || {
  echo "no reply from Vesta within ${timeout}s (is the app running, and do the x0x daemons trust each other?)" >&2
  exit 4
}
report_reply "$reply" || exit 3

if [[ -n "$recipe" ]]; then
  jq -r '.result |
    "\(."schema:name")  [\(."schema:recipeCategory"), serves \(."schema:recipeYield"), total \(."schema:totalTime")]",
    (if ."schema:description" then "\n\(."schema:description")" else empty end),
    "\nIngredients:",
    (."schema:recipeIngredient"[] | "- \([(.["schema:value"] // empty | tostring), (."schema:unitText" // empty), ."schema:name"] | join(" "))\(if ."schema:description" then " (\(."schema:description"))" else "" end)"),
    "\nMethod:",
    (."schema:recipeInstructions" | to_entries[] | "\(.key + 1). \(.value["schema:text"])")' <<<"$reply"
else
  jq -r '.result | "\(.items | length) of \(.totalItems) recipes"' <<<"$reply" >&2
  jq -r '.result.items[] | [."schema:identifier", ."schema:name", ."schema:recipeCategory", ."schema:recipeYield", ."schema:totalTime", ((."schema:keywords" // []) | join(", "))] | @tsv' <<<"$reply"
fi
