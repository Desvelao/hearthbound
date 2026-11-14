#!/usr/bin/env python3
"""Turn a PNG back into a sprite grid, so a sprite touched up in a pixel editor stays in the pipeline.

Usage: python3 tools/png2sprite.py in.png art/sprites/out.sprite
Each pixel is matched to the nearest palette colour; fully transparent pixels become '.'.
"""
import sys
from pathlib import Path

import yaml
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent


def main(argv):
    if len(argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    palette = yaml.safe_load((ROOT / "art" / "palette.yaml").read_text())["colors"]
    rgb = {ch: tuple(int(v.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)) for ch, v in palette.items()}
    img = Image.open(argv[1]).convert("RGBA")
    rows = []
    for y in range(img.height):
        row = ""
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            if a < 128:
                row += "."
            else:
                row += min(rgb, key=lambda ch: sum((c1 - c2) ** 2 for c1, c2 in zip(rgb[ch], (r, g, b))))
        rows.append(row)
    Path(argv[2]).write_text("\n".join(rows) + "\n")
    print(f"wrote {argv[2]} ({img.width}x{img.height})")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
