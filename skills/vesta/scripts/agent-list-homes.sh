#!/usr/bin/env bash
# Asks Vesta which Homes it has, over x0x, and prints one "<homeId><TAB><name>" line per Home.
# Ask your user which Home they mean, then pass its id as --home to agent-pantry-add.sh.
#
# Usage: agent-list-homes.sh --agent-instance NAME --to VESTA_AGENT_ID [--timeout SECONDS]
# Exit: 0 ok | 1 error | 3 Vesta rejected (reason on stderr; "not approved" => user must Allow in the app)
#       | 4 no reply in time (usually: daemons don't trust each other, or the app isn't running)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/x0x-agent-lib.sh"

instance="" to="" timeout=60
while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-instance) instance="${2:?--agent-instance needs a value}"; shift 2 ;;
    --to)             to="${2:?--to needs a value}"; shift 2 ;;
    --timeout)        timeout="${2:?--timeout needs a value}"; shift 2 ;;
    -h|--help)        sed -n '2,7p' "$0"; exit 0 ;;
    *)                die "unknown argument: $1" ;;
  esac
done
[[ -n "$instance" && -n "$to" ]] || die "--agent-instance and --to are required"

agent_instance "$instance"
command_id="$(new_command_id)"
start_listener

payload="$(jq -cn --argjson ctx "$CONTEXT" --arg id "$command_id" \
  '{"@context": $ctx, "type": "Read", "id": $id, "object": {"type": "Collection", "id": "urn:vesta:homes"}}')"
send_command "$to" "$payload" >/dev/null

reply="$(await_reply "$to" "$command_id" "$timeout")" || {
  echo "no reply from Vesta within ${timeout}s (is the app running, and do the x0x daemons trust each other?)" >&2
  exit 4
}
report_reply "$reply" || exit 3
jq -r '.result.items[] | [."schema:identifier", ."schema:name"] | @tsv' <<<"$reply"
