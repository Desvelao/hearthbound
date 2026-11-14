# Pixel art style

Every sprite is a text grid in `art/sprites/`. `tools/sprites.py` turns it into a PNG and checks these rules.

- **Outline:** every shape has a 1 px outline in `o` (dark blue-grey). No pure black.
- **Light** comes from the top left: highlight characters sit on the top and left edges, shade on the bottom and right.
- **Shading:** at most 3 tones per material (base, shade, highlight), taken from `palette.yaml`.
- **Canvas:** avatar layers (body, outfit, armour, hair, headgear, weapon) are 32x32. Icons and pets are 16x16.
- **Anchor box:** every avatar layer stays inside x 6..25, y 2..31, so gear lines up on the body. The base body
  occupies x 8..23, y 4..31. Weapons use the wider box x 2..29 so a blade can sit beside the body.
- **Armour is an overlay on the outfit**, one grid for all bodies (torso is x 11..20, y 16..23). Check
  `art/gear_preview.png`, which shows every gear item on all three bodies.
- **Metal** uses `s`, `S`, `z` (base, shade, highlight); tiers are bronze, steel and gold palette swaps.
- **Pets** are drawn with the hair ramp `h`, `i`, `j`, so each colour is a palette swap.
- **Variants** (hair colours, gear tiers) are palette swaps in `manifest.yaml`, not redrawn grids.
- **No anti-aliasing** and no semi-transparent pixels. A pixel is a palette colour or transparent.
- **In the app** layers are drawn in this order: body, outfit, armour, hair, headgear, weapon, scaled by whole
  multiples with filtering off. The pet is drawn beside the hero, never as a layer.
