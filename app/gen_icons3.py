# -*- coding: utf-8 -*-
"""Generate launcher icons from the user-supplied artwork (icon_target.png).

Strategy
--------
* Legacy icon (API < 26): the artwork itself, rounded-square with transparent
  corners, downscaled to every mipmap density.
* Adaptive icon (API 26+): a background layer carrying the (corner-extended)
  gradient and a foreground layer carrying the rounded artwork.
  Both are drawn at the standard 108dp canvas; the artwork occupies the 72dp
  visible area (66.67%) so the masked icon looks exactly like the source
  artwork with its corners cropped by the launcher mask.
  The background's visible region is colour-identical to the artwork edges,
  so no seam is visible around the rounded corners.

The transparent corner caps of the source artwork are filled with an
onion-peel harmonic inpainting of the gradient, so the background layer
continues the gradient seamlessly into the corners.
"""
import os

import numpy as np
from PIL import Image, ImageDraw

PROJ = r"D:\Admin\Desktop\android\DSUManager"
SRC = os.path.join(PROJ, "icon_target.png")
RES = os.path.join(PROJ, "res")

BASE = 512
# measured from the source artwork: rounded-rectangle corner radius
RADIUS_RATIO = 103.0 / BASE

LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
ART_RATIO = 72.0 / 108.0  # artwork occupies the 72dp visible area


def load_source():
    im = Image.open(SRC).convert("RGBA")
    if im.size != (BASE, BASE):
        im = im.resize((BASE, BASE), Image.LANCZOS)
    return im


def fill_corners(im):
    """Return an opaque full-square image: the gradient is extended into the
    transparent rounded corners by peeling harmonic values inwards."""
    a = np.array(im).astype(np.float32)
    rgb = a[..., :3]
    alpha = a[..., 3]
    known = alpha >= 250.0
    fill = rgb.copy()
    kn = known.copy()
    invalid = ~kn
    h, w = kn.shape
    it = 0
    while invalid.any() and it < 400:
        pad_fill = np.pad(fill, ((1, 1), (1, 1), (0, 0)), mode="edge")
        pad_kn = np.pad(kn.astype(np.float32), ((1, 1), (1, 1)), mode="edge")
        s = np.zeros((h, w, 3), np.float32)
        c = np.zeros((h, w), np.float32)
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            s += pad_fill[1 + dy:1 + dy + h, 1 + dx:1 + dx + w] * \
                 pad_kn[1 + dy:1 + dy + h, 1 + dx:1 + dx + w][..., None]
            c += pad_kn[1 + dy:1 + dy + h, 1 + dx:1 + dx + w]
        new = invalid & (c > 0.5)
        fill[new] = s[new] / c[new][..., None]
        kn[new] = True
        invalid = ~kn
        it += 1
    out = np.dstack([np.clip(fill, 0, 255), np.full((h, w), 255.0)])
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def rounded_mask(size, supersample=8):
    s = size * supersample
    m = Image.new("L", (s, s), 0)
    r = max(1, int(round(RADIUS_RATIO * s)))
    ImageDraw.Draw(m).rounded_rectangle([0, 0, s - 1, s - 1], radius=r, fill=255)
    return m.resize((size, size), Image.LANCZOS)


def make_legacy(full, size):
    art = full.convert("RGBA").resize((size, size), Image.LANCZOS)
    art.putalpha(rounded_mask(size))
    return art


def make_background(full, canvas):
    art_px = int(round(canvas * ART_RATIO))
    off = (canvas - art_px) // 2
    art = np.array(full.convert("RGB").resize((art_px, art_px), Image.LANCZOS))
    out = np.pad(art, ((off, canvas - art_px - off), (off, canvas - art_px - off), (0, 0)),
                 mode="edge")
    return Image.fromarray(out, "RGB")


def make_foreground(full, canvas):
    art_px = int(round(canvas * ART_RATIO))
    off = (canvas - art_px) // 2
    art = full.convert("RGBA").resize((art_px, art_px), Image.LANCZOS)
    art.putalpha(rounded_mask(art_px))
    fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    fg.paste(art, (off, off))
    return fg


def main():
    src = load_source()
    full = fill_corners(src)
    full.save(os.path.join(PROJ, "icon_fullsquare.png"))

    for dpi, size in LEGACY.items():
        d = os.path.join(RES, "mipmap-" + dpi)
        os.makedirs(d, exist_ok=True)
        make_legacy(full, size).save(os.path.join(d, "ic_launcher.png"))
        print("legacy", dpi, size)

    for dpi, canvas in ADAPTIVE.items():
        d = os.path.join(RES, "mipmap-" + dpi)
        os.makedirs(d, exist_ok=True)
        make_background(full, canvas).save(os.path.join(d, "ic_launcher_background.png"))
        make_foreground(full, canvas).save(os.path.join(d, "ic_launcher_foreground.png"))
        print("adaptive", dpi, canvas)

    # adaptive-icon xml (kept referencing the mipmap layers)
    ad = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(ad, exist_ok=True)
    with open(os.path.join(ad, "ic_launcher.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@mipmap/ic_launcher_background"/>\n'
                '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
                '</adaptive-icon>\n')

    # canonical preview: legacy icon at 512
    make_legacy(full, BASE).save(os.path.join(PROJ, "icon_preview.png"))
    print("icons generated from", SRC)


if __name__ == "__main__":
    main()
