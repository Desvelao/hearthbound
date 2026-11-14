#!/usr/bin/env python3
"""Validate sprite grids, render them to PNG and build the contact sheet and avatar preview.

Run inside the Docker image:  docker compose run --rm dev python3 tools/sprites.py
Exit status is non-zero when any sprite breaks a rule, so CI can use it as a check.
"""
import argparse
import sys
from pathlib import Path

import yaml
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "art"
DEFAULT_OUT = ROOT / "feature" / "character" / "src" / "main" / "res" / "drawable-nodpi"
SCALE = 8
AVATAR_ORDER = ["body", "outfit", "armour", "hair", "headgear", "weapon"]  # draw order; later layers cover earlier ones
SHOP_LAYERS = ("armour", "headgear", "weapon", "pet")  # layers sold in the shop; each needs a price
GEAR_LAYERS = ("armour", "headgear", "weapon")  # these also need a tier
BODY_OUTFITS = {"body_default": "outfit_tunic", "body_male": "outfit_tunic_male", "body_female": "outfit_tunic_female"}
DEFAULT_HAIR = "hair_short_brown"
PREVIEW_LOOK = ["body_default", "outfit_tunic", "hair_short_brown"]


def hex_rgba(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def load():
    palette = yaml.safe_load((ART / "palette.yaml").read_text())["colors"]
    for ch in palette:
        if len(ch) != 1 or ch == ".":
            sys.exit(f"palette.yaml: '{ch}' must be one character and not '.'")
    manifest = yaml.safe_load((ART / "manifest.yaml").read_text())
    return {k: hex_rgba(v) for k, v in palette.items()}, manifest


def read_grid(path):
    return [line.rstrip("\n") for line in path.read_text().splitlines() if line and not line.startswith("#")]


def validate(item, grid, layer_type, colors):
    """Return a list of human-readable problems for one sprite."""
    problems = []
    w, h = layer_type["size"]
    if len(grid) != h:
        problems.append(f"{item['id']}: has {len(grid)} rows, {item['layer']} layers need {h}")
    for y, row in enumerate(grid):
        if len(row) != w:
            problems.append(f"{item['id']}: row {y} has {len(row)} columns, needs {w}")
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch != "." and ch not in colors:
                problems.append(f"{item['id']}: unknown palette character '{ch}' at x={x} y={y}")
    if item["layer"] in SHOP_LAYERS:
        price = item.get("price")
        if not isinstance(price, int) or price <= 0:
            problems.append(f"{item['id']}: {item['layer']} items need a positive integer price")
    if item["layer"] in GEAR_LAYERS and item.get("tier") not in (1, 2, 3):
        problems.append(f"{item['id']}: {item['layer']} items need a tier of 1, 2 or 3")
    anchor = layer_type.get("anchor")
    if anchor:
        x0, y0, x1, y1 = anchor
        for y, row in enumerate(grid):
            for x, ch in enumerate(row):
                if ch != "." and not (x0 <= x <= x1 and y0 <= y <= y1):
                    problems.append(f"{item['id']}: pixel at x={x} y={y} is outside the anchor box {anchor}")
    return problems


def render(grid, colors):
    h, w = len(grid), len(grid[0])
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), colors[ch])
    return img


def checker(size, cell=8):
    img = Image.new("RGBA", size, (58, 58, 70, 255))
    d = ImageDraw.Draw(img)
    for y in range(0, size[1], cell):
        for x in range(0, size[0], cell):
            if (x // cell + y // cell) % 2:
                d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(70, 70, 84, 255))
    return img


def upscale(img):
    return img.resize((img.width * SCALE, img.height * SCALE), Image.NEAREST)


def contact_sheet(rendered, path):
    font = ImageFont.load_default()
    cols, pad, label_h = 4, 12, 16
    cell_w = max(i.width for i in rendered.values()) * SCALE + pad
    cell_h = max(i.height for i in rendered.values()) * SCALE + pad + label_h
    rows = -(-len(rendered) // cols)
    sheet = Image.new("RGBA", (cols * cell_w + pad, rows * cell_h + pad), (40, 40, 50, 255))
    d = ImageDraw.Draw(sheet)
    for n, (name, img) in enumerate(rendered.items()):
        x, y = pad + (n % cols) * cell_w, pad + (n // cols) * cell_h
        big = upscale(img)
        bg = checker(big.size)
        bg.alpha_composite(big)
        sheet.paste(bg, (x, y))
        d.text((x, y + big.height + 2), name, fill=(235, 235, 245, 255), font=font)
    sheet.convert("RGB").save(path)


def avatar_preview(rendered, path):
    layers = [rendered[i] for i in PREVIEW_LOOK if i in rendered]
    if not layers:
        return
    base = Image.new("RGBA", layers[0].size, (0, 0, 0, 0))
    for layer in layers:
        base.alpha_composite(layer)
    big = upscale(base)
    bg = checker(big.size)
    bg.alpha_composite(big)
    bg.convert("RGB").save(path)


def gear_preview(rendered, manifest, path):
    """Every gear item on all three bodies (with default hair), so fit is reviewed rather than assumed.

    One row per design, tiers side by side, each tier shown on the neutral, male and female body."""
    scale = 4
    gear = [i for i in manifest["items"] if i["layer"] in GEAR_LAYERS and i["id"] in rendered]
    designs = {}
    for i in gear:
        designs.setdefault(i["source"], []).append(i)
    if not designs or DEFAULT_HAIR not in rendered:
        return
    cell, pad = 32 * scale, 8
    cols = max(len(v) for v in designs.values()) * len(BODY_OUTFITS)
    sheet = Image.new("RGBA", (cols * (cell + pad) + pad, len(designs) * (cell + pad + 14) + pad), (40, 40, 50, 255))
    d = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for row, (source, tiers) in enumerate(designs.items()):
        n = 0
        for item in sorted(tiers, key=lambda i: i["tier"]):
            for body, outfit in BODY_OUTFITS.items():
                stack = {"body": rendered[body], "outfit": rendered[outfit], "hair": rendered[DEFAULT_HAIR],
                         item["layer"]: rendered[item["id"]]}
                base = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
                for layer in AVATAR_ORDER:
                    if layer in stack:
                        base.alpha_composite(stack[layer])
                big = base.resize((cell, cell), Image.NEAREST)
                bg = checker(big.size)
                bg.alpha_composite(big)
                x, y = pad + n * (cell + pad), pad + row * (cell + pad + 14)
                sheet.paste(bg, (x, y))
                d.text((x, y + cell + 2), f"{item['id']} / {body[5:] or 'neutral'}"[:22], fill=(235, 235, 245, 255), font=font)
                n += 1
    sheet.convert("RGB").save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate only; write nothing")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="folder for the PNGs")
    args = parser.parse_args()

    palette, manifest = load()
    types = manifest["layer_types"]
    problems, rendered = [], {}
    ids = set()
    for item in manifest["items"]:
        if item["id"] in ids:
            problems.append(f"{item['id']}: duplicate id")
        ids.add(item["id"])
        if item["layer"] not in types:
            problems.append(f"{item['id']}: unknown layer '{item['layer']}'")
            continue
        src = ART / "sprites" / item["source"]
        if not src.exists():
            problems.append(f"{item['id']}: missing source {src.name}")
            continue
        colors = dict(palette)
        for ch, value in item.get("swaps", {}).items():
            if ch not in palette:
                problems.append(f"{item['id']}: swap for unknown palette character '{ch}'")
            colors[ch] = hex_rgba(value)
        grid = read_grid(src)
        found = validate(item, grid, types[item["layer"]], palette)
        problems += found
        if not found:
            rendered[item["id"]] = render(grid, colors)

    if problems:
        print("\n".join(problems), file=sys.stderr)
        print(f"\n{len(problems)} problem(s). Nothing was written.", file=sys.stderr)
        return 1
    if args.check:
        print(f"{len(rendered)} sprites OK")
        return 0

    args.out.mkdir(parents=True, exist_ok=True)
    for name, img in rendered.items():
        img.save(args.out / f"sprite_{name}.png")
    contact_sheet(rendered, ART / "contact_sheet.png")
    avatar_preview(rendered, ART / "avatar_preview.png")
    gear_preview(rendered, manifest, ART / "gear_preview.png")
    print(f"{len(rendered)} sprites written to {args.out.relative_to(ROOT)}; see art/contact_sheet.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())
