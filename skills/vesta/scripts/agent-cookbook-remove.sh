#!/usr/bin/env bash
# Removes a recipe from a Home's cookbook, over x0x (AS2 Remove of a schema:Recipe), named by --recipe (its id from
# agent-cookbook-read.sh) or, failing that, --title (ignoring case). Removing is permanent for every device in the Home:
# only do it when the user asked you to, and say which recipe you removed.
# Needs the user to have switched on Cookbook > Write for that Home (Settings > Assistants).
#
# Usage: agent-cookbook-remove.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID (--recipe RECIPE_ID | --title TITLE)
#                                 [--id URN] [--timeout SECONDS]
#
# Waits for Vesta's reply and prints "Removed: <name> (<recipeId>)".
# Exit: 0 accepted | 1 error | 3 Vesta rejected (reason on stderr; "unknown recipe" => no such recipe in that cookbook)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
# Needs: bash, curl, jq, base64.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" recipe="" title="" command_id="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --recipe)         recipe="${2:?--recipe needs a value}"; shift 2 ;;
    --title)          title="${2:?--title needs a value}"; shift 2 ;;
    --id)             command_id="${2:?--id needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,12p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" ]] || die "--agent-instance, --to and --home are required"
[[ -n "$recipe" || -n "$title" ]] || die "--recipe or --title is required"

agent_instance "$instance"
[[ -n "$command_id" ]] || command_id="$(new_command_id)"

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" --arg recipe "$recipe" --arg title "$title" '
  {
    "@context": $ctx,
    "type": "Remove",
    "id": $id,
    "object": ({"type": "schema:Recipe"}
      + (if $recipe != "" then {"schema:identifier": $recipe} else {} end)
      + (if $title != "" then {"schema:name": $title} else {} end)),
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
jq -r '.result | "Removed: \(."schema:name") (\(."schema:identifier"))"' <<<"$reply"
