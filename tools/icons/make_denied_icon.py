"""Draws the tab icon for the colony block list: a rack with a no-entry sign over it.

20x20, the size MineColonies' own module icons have. Drawn here rather than copied from theirs, so
the pixels are ours.
"""

from PIL import Image

W = H = 20

OUTLINE = (74, 53, 32, 255)
WOOD = (161, 122, 78, 255)
WOOD_LIGHT = (203, 166, 114, 255)
RED = (208, 32, 32, 255)
RED_DARK = (120, 16, 16, 255)


def box(px, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < W and 0 <= y < H:
                px[x, y] = color


def rack(px):
    # two little posts on top
    box(px, 5, 3, 6, 5, WOOD)
    box(px, 13, 3, 14, 5, WOOD)
    # top board
    box(px, 2, 6, 17, 6, WOOD_LIGHT)
    box(px, 2, 7, 17, 7, WOOD)
    box(px, 2, 8, 17, 8, OUTLINE)
    # legs, with the gap between the two boards left open
    box(px, 3, 9, 4, 18, WOOD)
    box(px, 15, 9, 16, 18, WOOD)
    # lower shelf
    box(px, 3, 13, 16, 13, WOOD_LIGHT)
    box(px, 3, 14, 16, 14, WOOD)
    box(px, 3, 15, 16, 15, OUTLINE)


def ring(px, cx, cy, radius, thickness, color):
    for y in range(H):
        for x in range(W):
            dx = x - cx
            dy = y - cy
            distance = (dx * dx + dy * dy) ** 0.5
            if radius - thickness <= distance <= radius:
                px[x, y] = color


def bar(px, thickness, color):
    # upper left to lower right, the way a no-smoking sign is struck through
    for y in range(H):
        for x in range(W):
            if abs((x - 9.5) - (y - 9.5)) < thickness / 2:
                dx = x - 9.5
                dy = y - 9.5
                if (dx * dx + dy * dy) ** 0.5 <= 8.5:
                    px[x, y] = color


def main():
    image = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = image.load()

    rack(px)

    # the sign sits on top, with a dark edge so it reads against the wood
    ring(px, 9.5, 9.5, 9.7, 2.4, RED_DARK)
    ring(px, 9.5, 9.5, 9.2, 1.4, RED)
    bar(px, 3.0, RED_DARK)
    bar(px, 1.7, RED)

    image.save("icon_colony_denied.png")

    preview = Image.new("RGBA", (20 * 6, 20 * 6), (230, 230, 230, 255))
    preview.alpha_composite(image.resize((120, 120), Image.NEAREST))
    preview.save("icon_colony_denied_preview.png")
    print("written")


main()
