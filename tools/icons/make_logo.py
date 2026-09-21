"""Draws the logo the mod list shows: a Create cogwheel driving a colony crate.

The grid is 64x64 and is written out at four times that, so the mod list shows
pixels rather than a blur. The palette is taken from the mod's own block
textures, the browns of the shop frame and the brass of the gauge panel.
"""

from PIL import Image

G = 64
SCALE = 4

EDGE = (38, 28, 18, 255)
BRASS_DARK = (113, 77, 30, 255)
BRASS = (163, 119, 46, 255)
BRASS_LIGHT = (199, 151, 64, 255)
BRASS_HI = (228, 182, 96, 255)
WOOD_DARK = (58, 46, 28, 255)
WOOD = (101, 69, 40, 255)
WOOD_LIGHT = (161, 104, 59, 255)
WOOD_HI = (193, 124, 73, 255)
ACCENT = (255, 180, 62, 255)


def box(px, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < G and 0 <= y < G:
                px[x, y] = color


def disc(px, cx, cy, radius, color):
    for y in range(G):
        for x in range(G):
            dx = x - cx
            dy = y - cy
            if dx * dx + dy * dy <= radius * radius:
                px[x, y] = color


def cog(px, cx, cy, body, tooth, teeth, color, edge):
    """A gear: a round body with square teeth standing out of it."""
    import math

    step = 360.0 / teeth
    for y in range(G):
        for x in range(G):
            dx = x - cx
            dy = y - cy
            r = (dx * dx + dy * dy) ** 0.5
            if r > tooth:
                continue
            angle = math.degrees(math.atan2(dy, dx)) % step
            on_tooth = angle < step * 0.34 or angle > step * 0.66
            if r <= body:
                px[x, y] = edge if r > body - 1.2 else color
            elif on_tooth:
                px[x, y] = edge if r > tooth - 1.2 else color


def main():
    image = Image.new("RGBA", (G, G), (0, 0, 0, 0))
    px = image.load()

    # the cogwheel, behind and to the upper left: the Create side
    cog(px, 25.0, 25.0, 15.0, 20.5, 8, BRASS, EDGE)
    # its lit edge, a band along the upper left of the body
    for y in range(G):
        for x in range(G):
            if px[x, y] != BRASS:
                continue
            dx = x - 25.0
            dy = y - 25.0
            r = (dx * dx + dy * dy) ** 0.5
            if r > 11.5 and dx + dy < -6:
                px[x, y] = BRASS_LIGHT
            elif r > 11.5 and dx + dy > 8:
                px[x, y] = BRASS_DARK
    # the hub and its shaft hole
    disc(px, 25.0, 25.0, 6.2, EDGE)
    disc(px, 25.0, 25.0, 5.2, BRASS_LIGHT)
    disc(px, 25.0, 25.0, 2.6, EDGE)
    box(px, 23, 23, 27, 27, EDGE)
    box(px, 24, 24, 26, 26, BRASS_HI)

    # the crate, in front and to the lower right: the colony side
    box(px, 27, 29, 59, 59, EDGE)
    box(px, 28, 30, 58, 58, WOOD)
    # planks, lit along the top
    box(px, 28, 30, 58, 33, WOOD_LIGHT)
    box(px, 28, 30, 58, 30, WOOD_HI)
    box(px, 28, 38, 58, 39, WOOD_DARK)
    box(px, 28, 48, 58, 49, WOOD_DARK)
    # the diagonal brace across the front
    for i in range(0, 26):
        box(px, 30 + i, 55 - i, 31 + i, 56 - i, WOOD_DARK)
    # brass bands down the sides
    box(px, 28, 30, 31, 58, BRASS)
    box(px, 55, 30, 58, 58, BRASS)
    box(px, 28, 30, 28, 58, BRASS_LIGHT)
    box(px, 58, 30, 58, 58, BRASS_DARK)
    box(px, 31, 30, 31, 58, EDGE)
    box(px, 55, 30, 55, 58, EDGE)
    # rivets
    for y in (34, 44, 54):
        box(px, 29, y, 30, y + 1, BRASS_HI)
        box(px, 56, y, 57, y + 1, BRASS_HI)

    # the gauge reading on the crate: what the colony owes the network
    box(px, 35, 42, 51, 47, EDGE)
    box(px, 36, 43, 50, 46, (24, 18, 12, 255))
    box(px, 37, 44, 46, 45, ACCENT)

    image.resize((G * SCALE, G * SCALE), Image.NEAREST).save("logo.png")
    print("written logo.png at %dx%d" % (G * SCALE, G * SCALE))


main()
