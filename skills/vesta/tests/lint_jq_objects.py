#!/usr/bin/env python3
"""Flags jq object values that are not a plain term or a parenthesised expression.

jq 1.6 and older only accept a term as an object value: `"k": {...} + (...)` or `"k": if ... end` fails to compile there
(jq 1.7+ is lenient, so it passes on a newer jq and breaks on an older one). Wrap such a value as `"k": ({...} + (...))`.
Usage: lint_jq_objects.py SCRIPT...   Exit 1 if any script has an offending value.
"""
import re
import sys

OPERATORS = "+-*/%|=<>!"


def jq_programs(text):
    """The single-quoted jq programs passed to `jq` in a bash script (every quoted block that starts a jq filter)."""
    programs = []
    for m in re.finditer(r"jq\b[^\n']*?(?:\\\n[^\n']*?)*'((?:[^'])*)'", text):
        programs.append((text[: m.start(1)].count("\n") + 1, m.group(1)))
    return programs


def problems(program, first_line):
    found = []
    i, n, line = 0, len(program), first_line
    stack = []  # '{' or '(' or '['
    value_starts = []  # positions of object values, found as `"key":` followed by non-space
    while i < n:
        c = program[i]
        if c == "\n":
            line += 1
        elif c == '"':  # string: skip, noting `"key":` object keys
            j = i + 1
            while j < n and program[j] != '"':
                j += 2 if program[j] == "\\" else 1
            end = j
            k = end + 1
            while k < n and program[k] in " \t\n":
                k += 1
            if stack and stack[-1] == "{" and k < n and program[k] == ":":
                value_starts.append((k + 1, line))
            line += program[i:end].count("\n")
            i = end
        elif c in "{([":
            stack.append(c)
        elif c in "})]":
            if stack:
                stack.pop()
        i += 1

    for start, at in value_starts:
        k = start
        while k < n and program[k] in " \t\n":
            k += 1
        if k >= n:
            continue
        if program.startswith("if", k) and not program[k + 2 : k + 3].isalnum():
            found.append((at, "object value starts with `if` — wrap it in parentheses"))
            continue
        if program[k] == "{" or program[k] == "[" or program[k] == "(":
            close = {"{": "}", "[": "]", "(": ")"}[program[k]]
            depth, j = 0, k
            while j < n:
                ch = program[j]
                if ch == '"':
                    j += 1
                    while j < n and program[j] != '"':
                        j += 2 if program[j] == "\\" else 1
                elif ch in "{([":
                    depth += 1
                elif ch in "})]":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            after = j + 1
            while after < n and program[after] in " \t\n":
                after += 1
            if after < n and program[after] in OPERATORS and program[after] != "=" and not program.startswith("//", after):
                if program[after] in ",}":
                    continue
                found.append((at, f"object value is followed by `{program[after]}` — wrap the whole value in parentheses"))
    return found


def main(paths):
    bad = 0
    for path in paths:
        text = open(path).read()
        for first_line, program in jq_programs(text):
            for line, message in problems(program, first_line):
                print(f"{path}:{line}: {message}")
                bad += 1
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
