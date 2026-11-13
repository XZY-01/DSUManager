import os
from PIL import Image

PROJ = r"D:\Admin\Desktop\android\DSUManager"
SRC = os.path.join(PROJ, "icon_source.png")
RES = os.path.join(PROJ, "res")

im = Image.open(SRC).convert("RGBA")
bg = im.getpixel((60, 262))[:3]
print("source", im.size, "bg", bg)

# 传统图标（mipmap）
legacy = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for dpi, size in legacy.items():
    d = os.path.join(RES, "mipmap-" + dpi)
    os.makedirs(d, exist_ok=True)
    im.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))

# 自适应图标前景（透明背景，图形按 66% 居中）
fg_sizes = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
for dpi, size in fg_sizes.items():
    d = os.path.join(RES, "mipmap-" + dpi)
    os.makedirs(d, exist_ok=True)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = int(size * 0.66)
    scaled = im.resize((inner, inner), Image.LANCZOS)
    off = (size - inner) // 2
    canvas.paste(scaled, (off, off), scaled)
    canvas.save(os.path.join(d, "ic_launcher_foreground.png"))

# 自适应图标 xml
d = os.path.join(RES, "mipmap-anydpi-v26")
os.makedirs(d, exist_ok=True)
with open(os.path.join(d, "ic_launcher.xml"), "w", encoding="utf-8", newline="\n") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_launcher_background"/>\n'
            '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
            '</adaptive-icon>\n')

# 背景色
vd = os.path.join(RES, "values")
os.makedirs(vd, exist_ok=True)
with open(os.path.join(vd, "colors.xml"), "w", encoding="utf-8", newline="\n") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            '    <color name="ic_launcher_background">#%02X%02X%02X</color>\n</resources>\n' % bg)
print("icons generated")
