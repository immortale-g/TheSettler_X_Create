# GUI icon sources

The module tab icons under
`src/main/resources/assets/thesettler_x_create/textures/gui/modules/` are drawn by these scripts,
pixel by pixel, so a later change is a change to the script rather than a guess in an image editor.

| Script | Texture | Shows |
|---|---|---|
| `make_denied_icon.py` | `colony_denied.png` | A rack under a no entry sign, for the list of what the colony may not draw |
| `make_safe_icon.py` | `network_minimum.png` | A safe, for what the shop keeps back in its Create network |
| `make_logo.py` | `thesettler_x_create.png` | The mod list logo: a Create cogwheel driving a colony crate |

They all need Pillow. The two module icons write the texture plus a six times preview
next to themselves:

```
python tools/icons/make_denied_icon.py
```

Copy the result over the file in `textures/gui/modules/`. The icons are 20x20, the size
MineColonies' own module icons have.

`make_logo.py` writes `logo.png` instead, and no preview: it draws on a 64x64 grid and saves it at
four times that. It belongs in `src/main/resources/`, the root of the resource tree, because
NeoForge reads `logoFile` from the root of the jar.

MineColonies' `stock.png` shows the same kind of rack, which is where the idea comes from. Nothing
was copied from it: the shapes and the palette here are written out in the scripts. See
`docs/provenance.md`.
