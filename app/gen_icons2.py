import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

PROJ = r"D:\Admin\Desktop\android\DSUManager"
RES = os.path.join(PROJ, "res")

SIZE = 1024
C1 = np.array([46, 126, 235], dtype=float)   # 蓝
C2 = np.array([0, 196, 162], dtype=float)    # 蓝绿/青

# ---- 渐变背景 ----
x = np.linspace(0, 1, SIZE)
y = np.linspace(0, 1, SIZE)
t = (x[None, :] * 0.62 + y[:, None] * 0.38)
grad = (C1[None, None, :] * (1 - t[..., None]) + C2[None, None, :] * t[..., None]).astype(np.uint8)
bg = Image.fromarray(grad, "RGB").convert("RGBA")

# 顶部高光
hl = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
d = ImageDraw.Draw(hl)
d.ellipse([-SIZE * 0.3, -SIZE * 0.75, SIZE * 1.3, SIZE * 0.45], fill=(255, 255, 255, 42))
hl = hl.filter(ImageFilter.GaussianBlur(60))
bg = Image.alpha_composite(bg, hl)

# 圆角遮罩（传统图标用）
mask = Image.new("L", (SIZE, SIZE), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=232, fill=255)


def draw_glyph(canvas_size, scale=1.0):
    """三层圆角矩形：等大、等距、沿对角线对称叠放，代表多个系统。"""
    g = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    cx = canvas_size / 2.0
    card_w = 430 * scale
    card_h = 148 * scale
    radius = 42 * scale
    step = card_h * 0.76
    total_h = card_h + step * 2
    top = canvas_size / 2.0 - total_h / 2.0
    dxs = [-34 * scale, 0.0, 34 * scale]
    alphas = [92, 168, 255]
    for i in range(3):
        x0 = cx - card_w / 2.0 + dxs[i]
        y0 = top + i * step
        d.rounded_rectangle([x0 + 12 * scale, y0 + 16 * scale,
                             x0 + card_w + 12 * scale, y0 + card_h + 16 * scale],
                            radius=radius, fill=(0, 0, 0, 42))
        d.rounded_rectangle([x0, y0, x0 + card_w, y0 + card_h],
                            radius=radius, fill=(255, 255, 255, alphas[i]))
    return g


# ---- 传统图标（圆角渐变方块 + 图形）----
legacy = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for dpi, size in legacy.items():
    out = os.path.join(RES, "mipmap-" + dpi)
    os.makedirs(out, exist_ok=True)
    base = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    base.paste(bg, (0, 0))
    base.putalpha(mask)
    glyph = draw_glyph(SIZE, scale=1.15)
    base = Image.alpha_composite(base, glyph)
    base.resize((size, size), Image.LANCZOS).save(os.path.join(out, "ic_launcher.png"))

# ---- 自适应图标：背景（满幅渐变）+ 前景（透明底图形）----
fg_sizes = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
for dpi, size in fg_sizes.items():
    out = os.path.join(RES, "mipmap-" + dpi)
    os.makedirs(out, exist_ok=True)
    bg.resize((size, size), Image.LANCZOS).save(os.path.join(out, "ic_launcher_background.png"))
    # 前景：图形缩到 66% 居中
    fg = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    glyph = draw_glyph(SIZE, scale=1.3)
    fg = Image.alpha_composite(fg, glyph)
    fg.resize((size, size), Image.LANCZOS).save(os.path.join(out, "ic_launcher_foreground.png"))

# ---- adaptive-icon xml ----
ad = os.path.join(RES, "mipmap-anydpi-v26")
os.makedirs(ad, exist_ok=True)
with open(os.path.join(ad, "ic_launcher.xml"), "w", encoding="utf-8", newline="\n") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@mipmap/ic_launcher_background"/>\n'
            '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
            '</adaptive-icon>\n')

# 预览图 512（圆角）+ 圆形预览
prev = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
prev.paste(bg, (0, 0))
prev.putalpha(mask)
prev = Image.alpha_composite(prev, draw_glyph(SIZE, 1.15))
prev.resize((512, 512), Image.LANCZOS).save(os.path.join(PROJ, "icon_preview.png"))

print("new icon generated")
