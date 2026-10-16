#!/usr/bin/env bash
# Checks every scripts/agent-*.sh without a daemon:
#   1. the jq object-value lint (tests/lint_jq_objects.py): catches what jq 1.6 and older refuse to compile
#      but a newer jq accepts;
#   2. each script is run against a stub x0x-agent-lib.sh that records the payload instead of sending it, and the
#      payload must be valid JSON with the expected AS2 type and the shared @context;
#   3. every script has a case here, so a new script can't skip the test.
#
# Usage: tests/test-scripts.sh      Exit 0 when all pass. Needs: bash, jq, python3.
set -uo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
scripts="$here/../scripts"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fail=0
pass() { echo "ok    $1"; }
bad() { echo "FAIL  $1: $2"; fail=1; }

# --- 1. lint
if lint="$(python3 "$here/lint_jq_objects.py" "$scripts"/agent-*.sh)"; then pass "jq object-value lint"; else bad "jq object-value lint" "$lint"; fi

# --- 2. stub run
cp "$scripts"/agent-*.sh "$work/"
cat > "$work/x0x-agent-lib.sh" <<'STUB'
CONTEXT='["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]'
die() { echo "error: $*" >&2; exit 1; }
agent_instance() { :; }
new_command_id() { echo urn:uuid:test; }
start_listener() { :; }
send_command() { printf '%s\n' "$2" > "$PAYLOAD_OUT"; }
await_reply() { return 1; }
report_reply() { :; }
STUB
export PAYLOAD_OUT="$work/payload.json"

# case SCRIPT EXPECTED_TYPE ARGS... — the common --agent-instance/--to are added here.
declare -A ran=()
case_() {
  local script="$1" type="$2"; shift 2
  ran["$script"]=1
  rm -f "$PAYLOAD_OUT"
  if ! (cd "$work" && bash "./$script" --agent-instance a --to b "$@" --timeout 1 >/dev/null 2>"$work/err.txt"); then
    : # the stub never replies, so a non-zero exit after sending is expected; only the payload matters
  fi
  if [[ ! -s "$PAYLOAD_OUT" ]]; then bad "$script $*" "no payload sent: $(head -c 200 "$work/err.txt" | tr '\n' ' ')"; return; fi
  if ! jq -e . "$PAYLOAD_OUT" >/dev/null 2>&1; then bad "$script $*" "payload is not valid JSON"; return; fi
  local got ctx
  got="$(jq -r '.type' "$PAYLOAD_OUT")"
  ctx="$(jq -c '."@context"' "$PAYLOAD_OUT")"
  [[ "$got" == "$type" ]] || { bad "$script $*" "type is $got, expected $type"; return; }
  [[ "$ctx" == '["https://www.w3.org/ns/activitystreams",{"schema":"https://schema.org/"}]' ]] || { bad "$script $*" "wrong @context: $ctx"; return; }
  pass "$script $*"
}

case_ agent-list-homes.sh       Read
case_ agent-pantry-read.sh      Read   --home h
case_ agent-pantry-add.sh       Add    --home h --title Milk
case_ agent-pantry-add.sh       Add    --home h --title Milk --category Dairy --category Milk --size "1l" --gtin 123 --qty 2
case_ agent-pantry-reduce.sh    Remove --home h --title Milk
case_ agent-pantry-reduce.sh    Remove --home h --title Milk --gtin 123 --qty 2
case_ agent-pantry-set.sh       Update --home h --title Milk --qty 0
case_ agent-pantry-set.sh       Update --home h --title Milk --gtin 123 --qty 5
case_ agent-shopping-read.sh    Read   --home h
case_ agent-shopping-add.sh     Add    --home h --title Milk
case_ agent-shopping-add.sh     Add    --home h --title Milk --category Dairy --size "1l" --gtin 123 --qty 2
case_ agent-shopping-tick.sh    Update --home h --title Milk --qty 1
case_ agent-shopping-tick.sh    Update --home h --title Milk --gtin 123 --qty 0
case_ agent-shopping-remove.sh  Remove --home h --title Milk
case_ agent-shopping-remove.sh  Remove --home h --title Milk --gtin 123
case_ agent-cookbook-read.sh    Read   --home h
case_ agent-cookbook-read.sh    Read   --home h --recipe r1
case_ agent-cookbook-add.sh     Add    --home h --title Pancakes
case_ agent-cookbook-add.sh     Add    --home h --title Pancakes --category Breakfast --servings 4 --cook PT15M --ingredient "2 eggs" --step Mix
case_ agent-cookbook-remove.sh  Remove --home h --recipe r1
case_ agent-cookbook-remove.sh  Remove --home h --title Pancakes
case_ agent-mealplan-read.sh    Read   --home h
case_ agent-mealplan-plan.sh    Add    --home h --date 2026-10-20 --slot dinner --recipe r1
case_ agent-mealplan-unplan.sh  Remove --home h --date 2026-10-20 --slot dinner --recipe r1
case_ agent-tasks-read.sh       Read   --home h
case_ agent-tasks-add.sh        Add    --home h --title "Take bins out"
case_ agent-tasks-add.sh        Add    --home h --title "Take bins out" --due 2026-10-20 --description "Green bin"
case_ agent-tasks-done.sh       Update --home h --title "Take bins out"
case_ agent-tasks-done.sh       Update --home h --task t1 --undo
case_ agent-tasks-remove.sh     Remove --home h --task t1
case_ agent-calendar-read.sh    Read   --home h
case_ agent-calendar-add.sh     Add    --home h --title Dentist --start 2026-10-20T09:00:00+01:00
case_ agent-calendar-update.sh  Update --home h --event e1 --title Dentist
case_ agent-calendar-remove.sh  Remove --home h --event e1

# --- 3. completeness
for f in "$scripts"/agent-*.sh; do
  name="$(basename "$f")"
  [[ -n "${ran[$name]:-}" ]] || bad "$name" "has no case in tests/test-scripts.sh"
done

(( fail == 0 )) && echo "all script checks passed" || echo "SOME SCRIPT CHECKS FAILED"
exit $fail
