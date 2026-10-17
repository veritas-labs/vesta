#!/usr/bin/env python3
"""Turns the onboarding illustrations in branding/slides/*.svg into Android vector drawables, one for light and one for dark.

Illustro exports flat SVGs: the line work is black filled shapes, the paper is an off-white background plus off-white
"knockout" shapes that hide the blobs behind the lines, and the blobs are muted earth tones. A drawable cannot follow the
theme, so this script recolours by role instead:

  ink    -> the theme's text colour (black in light, warm white in dark); mid greys (shading) sit on the ramp between ink and paper
  paper  -> the theme's background colour (so the picture sits on the screen with no visible square)
  blobs  -> left alone in light; scaled darker in dark ("blob_scale"), so the warm-white line work shows against them

Run from anywhere:  python3 -I scripts/slides-to-vectors.py
Output goes to branding/compose/drawable/ (the brand-art folder) and is symlinked into
shared/src/commonMain/composeResources/drawable/ the same way the other brand art is (see branding/README.md).
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "branding" / "slides"
BRANDING_OUT = ROOT / "branding" / "compose" / "drawable"
RESOURCES_OUT = ROOT / "shared" / "src" / "commonMain" / "composeResources" / "drawable"

# Source file prefix -> drawable slug (output is onboarding_<slug>).
SLIDES = {
    "a-simple-house": "home",
    "a-jar-a-tin": "pantry",
    "an-open-recipe-book": "cookbook",
    "a-month-grid": "calendar",
    "a-speech-bubble": "assistants",
    "two-devices": "privacy",
    "an-open-door": "done",
}

# Illustrations for the empty states (output is empty_<slug>). Prefixes are long enough to be unique among branding/slides/*.svg.
EMPTY_STATES = {
    "an-empty-shelf-with-one": "pantry",
    "three-empty-crates": "pantry_categories",
    "an-empty-shopping-basket": "shopping",
    "a-closed-or-half-open": "cookbook",
    "a-small-market-stall": "merchants",
    "a-phone-and-a-tablet-side": "synced_devices",
    "a-small-desktop-computer": "hubs",
    "a-small-sparkling-helper": "assistants",
    "a-magnifying-glass": "no_results",
    "a-calendar-page-with": "quiet_day",
}

# The goddess for the empty home picker: her line work is shaded in many greys, so instead of one ink colour every neutral grey is
# mapped onto the ramp between the theme's ink and paper by its lightness. Output is <name>.xml / <name>_dark.xml.
RAMPED = {"vesta-the-virgin-goddess": "img_vesta_line"}

# Keep in step with VestaLightColorScheme / VestaDarkColorScheme in VestaTheme.kt.
THEMES = {
    "": {"ink": "#1D1B16", "paper": "#F5F4ED", "blob_scale": 1.0},
    "_dark": {"ink": "#EDE5D8", "paper": "#15130F", "blob_scale": 0.55},
}

PATH = re.compile(r'<path\s+transform="translate\(0,0\)"\s+fill="([^"]+)"\s+d="([^"]+)"\s*/>')
RGB = re.compile(r"rgb\((\d+),\s*(\d+),\s*(\d+)\)")
GRADIENT_STOP = re.compile(r'<stop[^>]*stop-color="(rgb\([^"]+\))"')


def classify(r: int, g: int, b: int) -> str:
    if max(r, g, b) < 70:
        return "ink"
    if min(r, g, b) >= 225 and max(r, g, b) - min(r, g, b) <= 25:
        return "paper"
    return "blob"


def hex_to_rgb(colour: str) -> tuple[int, int, int]:
    return int(colour[1:3], 16), int(colour[3:5], 16), int(colour[5:7], 16)


def ramp(theme: dict, r: int, g: int, b: int) -> str:
    """The grey (r, g, b) as a point between the theme's ink (black) and paper (white)."""
    t = (r + g + b) / 3 / 255
    ink, paper = hex_to_rgb(theme["ink"]), hex_to_rgb(theme["paper"])
    return "#" + "".join(f"{round(i + (p - i) * t):02X}" for i, p in zip(ink, paper))


def convert(svg: str, theme: dict, name: str, ramped: bool = False) -> tuple[str, dict]:
    view = re.search(r'viewBox="0 0 (\d+) (\d+)"', svg)
    if not view:
        sys.exit(f"{name}: no viewBox")
    width, height = int(view.group(1)), int(view.group(2))
    first_stop = GRADIENT_STOP.search(svg)
    paths = []
    counts = {"ink": 0, "paper": 0, "blob": 0}
    unmatched = svg.count("<path") - len(PATH.findall(svg))
    if unmatched:
        sys.exit(f"{name}: {unmatched} <path> element(s) in an unexpected shape; the script only reads fill + d with no transform")
    for fill, d in PATH.findall(svg):
        if fill.startswith("url("):
            # A single soft shadow gradient in the recipe picture; its first stop is close enough as a flat colour.
            fill = first_stop.group(1) if first_stop else sys.exit(f"{name}: gradient fill with no stop colours")
        match = RGB.fullmatch(fill)
        if not match:
            sys.exit(f"{name}: cannot read fill {fill!r}")
        r, g, b = (int(v) for v in match.groups())
        if ramped and max(r, g, b) - min(r, g, b) <= 8:
            role = "paper" if min(r, g, b) >= 245 else "ink"
            counts[role] += 1
            # Near-white is the paper itself: exactly the theme's colour, so the picture has no visible edge on the screen.
            colour = theme["paper"] if role == "paper" else ramp(theme, r, g, b)
        else:
            role = classify(r, g, b) if not ramped else "blob"
            counts[role] += 1
            if role == "blob" and max(r, g, b) - min(r, g, b) <= 10:
                # A grey that is not ink or paper is shading on the line work (a plate's shadow, say): follow the same ramp.
                colour = ramp(theme, r, g, b)
            elif role == "blob":
                k = theme["blob_scale"]
                colour = f"#{round(r * k):02X}{round(g * k):02X}{round(b * k):02X}"
            else:
                colour = theme[role]
        paths.append(f'    <path android:fillColor="{colour}" android:pathData="{" ".join(d.split())}"/>')
    # The very first shape is the full-canvas background; it must have been classified as paper.
    if counts["paper"] == 0:
        sys.exit(f"{name}: found no paper-coloured shapes; the colour thresholds need a look")
    header = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Generated by scripts/slides-to-vectors.py from branding/slides. Do not edit by hand. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{width * 360 // 2048}dp"\n    android:height="{height * 360 // 2048}dp"\n'
        f'    android:viewportWidth="{width}"\n    android:viewportHeight="{height}">\n'
    )
    return header + "\n".join(paths) + "\n</vector>\n", counts


def link(target: Path, link_path: Path) -> None:
    relative = Path("../../../../../branding/compose/drawable") / target.name
    if link_path.is_symlink() or link_path.exists():
        link_path.unlink()
    link_path.symlink_to(relative)


def main() -> None:
    BRANDING_OUT.mkdir(parents=True, exist_ok=True)
    jobs = [(prefix, f"onboarding_{slug}", False) for prefix, slug in SLIDES.items()]
    jobs += [(prefix, f"empty_{slug}", False) for prefix, slug in EMPTY_STATES.items()]
    jobs += [(prefix, name, True) for prefix, name in RAMPED.items()]
    for prefix, base, ramped in jobs:
        sources = sorted(SOURCE.glob(f"{prefix}*.svg"))
        if len(sources) != 1:
            sys.exit(f"{prefix}*.svg: expected exactly one file in {SOURCE}, found {len(sources)}")
        svg = sources[0].read_text()
        for suffix, theme in THEMES.items():
            name = f"{base}{suffix}"
            xml, counts = convert(svg, theme, name, ramped)
            out = BRANDING_OUT / f"{name}.xml"
            out.write_text(xml)
            link(out, RESOURCES_OUT / out.name)
            print(f"{out.name}: {counts['ink']} ink, {counts['paper']} paper, {counts['blob']} blob, {len(xml) // 1024} KB")


if __name__ == "__main__":
    main()
