"""Draws the tab icon for the network minimum: a safe, for what the shop keeps locked away.

20x20, the size MineColonies' module icons have.
"""

from PIL import Image

W = H = 20

EDGE = (35, 38, 43, 255)
BODY = (90, 98, 112, 255)
BODY_LIGHT = (123, 132, 146, 255)
DOOR = (74, 81, 93, 255)
BRASS = (201, 162, 39, 255)
BRASS_DARK = (138, 106, 20, 255)


def box(px, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < W and 0 <= y < H:
                px[x, y] = color


def ring(px, cx, cy, radius, thickness, color):
    for y in range(H):
        for x in range(W):
            dx = x - cx
            dy = y - cy
            if radius - thickness <= (dx * dx + dy * dy) ** 0.5 <= radius:
                px[x, y] = color


def main():
    image = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = image.load()

    # the safe itself
    box(px, 2, 2, 17, 18, EDGE)
    box(px, 3, 3, 16, 17, BODY)
    box(px, 3, 3, 16, 3, BODY_LIGHT)
    box(px, 3, 3, 3, 17, BODY_LIGHT)

    # the door, inset, with the hinges on the left
    box(px, 5, 5, 15, 15, EDGE)
    box(px, 6, 6, 14, 14, DOOR)
    box(px, 4, 6, 4, 7, EDGE)
    box(px, 4, 13, 4, 14, EDGE)

    # the dial
    ring(px, 10.0, 10.0, 3.4, 1.4, BRASS)
    box(px, 10, 10, 10, 10, BRASS_DARK)
    # its spokes
    box(px, 10, 7, 10, 8, BRASS_DARK)
    box(px, 10, 12, 10, 13, BRASS_DARK)
    box(px, 7, 10, 8, 10, BRASS_DARK)
    box(px, 12, 10, 13, 10, BRASS_DARK)

    # the handle
    box(px, 15, 9, 16, 11, BRASS)
    box(px, 15, 10, 16, 10, BRASS_DARK)

    # feet
    box(px, 3, 19, 4, 19, EDGE)
    box(px, 15, 19, 16, 19, EDGE)

    image.save("icon_network_minimum.png")

    preview = Image.new("RGBA", (20 * 6, 20 * 6), (230, 230, 230, 255))
    preview.alpha_composite(image.resize((120, 120), Image.NEAREST))
    preview.save("icon_network_minimum_preview.png")
    print("written")


main()
