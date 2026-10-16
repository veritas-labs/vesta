#!/usr/bin/env bash
# Asks Vesta what is on a Home's shopping list, over x0x, and prints one line per item:
# "<wanted>TAB<got>TAB<title>TAB<categories>TAB<size>TAB<gtin>TAB<out-of-stock yes|no>" (at most 200).
# Needs the user to have switched on Shopping list > Read for that Home (Settings > Assistants).
#
# Usage: agent-shopping-read.sh --agent-instance NAME --to VESTA_AGENT_ID --home HOME_ID [--timeout SECONDS]
# Prints "<n> of <total> items" on stderr. Reading changes nothing, so re-running is always safe.
# Exit: 0 ok | 1 error | 3 Vesta rejected (reason on stderr; "not permitted" => the user hasn't granted Shopping list Read)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" home="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --home)           home="${2:?--home needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,9p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" && -n "$home" ]] || die "--agent-instance, --to and --home are required"

agent_instance "$instance"
command_id="$(new_command_id)"
start_listener

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" --arg home "$home" \
  '{"@context": $ctx, "type": "Read", "id": $id, "object": {"type": "Collection", "id": ("urn:vesta:document:" + $home + ":shopping-list")}}')"
send_command "$to" "$payload" >/dev/null

reply="$(await_reply "$to" "$command_id" "$timeout")" || {
  echo "no reply from Vesta within ${timeout}s (is the app running, and do the x0x daemons trust each other?)" >&2
  exit 4
}
report_reply "$reply" || exit 3
jq -r '.result | "\(.items | length) of \(.totalItems) items"' <<<"$reply" >&2
jq -r '.result.items[] | [."schema:amountOfThisGood", .got, ."schema:name", (."schema:category" | join(" > ")), (."schema:size" // ""), (."schema:gtin" // ""), (if .outOfStock then "yes" else "no" end)] | @tsv' <<<"$reply"
