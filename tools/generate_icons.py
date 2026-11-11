#!/usr/bin/env python3
"""
生成 Android 启动图标（无需联网、无外部素材依赖）。

设计：绿色圆角底 + 白色「图片卡片」+ 白色下载箭头。
- 传统图标：mipmap-*/ic_launcher.png、ic_launcher_round.png
- 自适应图标前景：mipmap-*/ic_launcher_foreground.png（透明底，内容在安全区内）
- 通知栏小图标：res/drawable/ic_stat_download.xml（手写在仓库里，不由此脚本生成）

用法（需 Pillow）：
    python tools/generate_icons.py
"""

from __future__ import annotations

import os
from PIL import Image, ImageChops, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

GREEN = (46, 125, 50, 255)          # #2E7D32
GREEN_DARK = (27, 94, 32, 255)      # #1B5E20
WHITE = (255, 255, 255, 255)
SS = 4                              # 超采样倍数

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

ADAPTIVE_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}


def _glyph(size: int, scale: float = 1.0) -> Image.Image:
    """白卡片 + 挖空的绿色太阳/山 + 下载箭头，返回带透明通道的图层。"""
    w = size * SS
    layer = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)

    cx = w / 2
    # 以 1.0 为设计基准，整体按 scale 缩放
    def sx(v: float) -> float:
        return cx + (v - 0.5) * w * scale

    def sy(v: float) -> float:
        return cx + (v - 0.5) * w * scale

    # 图片卡片
    card = [sx(0.16), sy(0.12), sx(0.84), sy(0.53)]
    radius = w * 0.055 * scale
    draw.rounded_rectangle(card, radius=radius, fill=WHITE)

    # 卡片内的山与太阳（用 alpha 挖空）
    holes = Image.new("L", (w, w), 0)
    hd = ImageDraw.Draw(holes)
    sun_r = w * 0.042 * scale
    sun_x, sun_y = sx(0.29), sy(0.225)
    hd.ellipse([sun_x - sun_r, sun_y - sun_r, sun_x + sun_r, sun_y + sun_r], fill=255)
    hd.polygon(
        [
            (sx(0.19), sy(0.505)),
            (sx(0.39), sy(0.295)),
            (sx(0.51), sy(0.415)),
            (sx(0.60), sy(0.335)),
            (sx(0.81), sy(0.505)),
        ],
        fill=255,
    )
    alpha = ImageChops.subtract(layer.getchannel("A"), holes)
    layer.putalpha(alpha)

    # 下载箭头
    shaft_w = w * 0.072 * scale
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle(
        [cx - shaft_w / 2, sy(0.60), cx + shaft_w / 2, sy(0.735)],
        radius=shaft_w / 2,
        fill=WHITE,
    )
    draw.polygon(
        [
            (cx - w * 0.125 * scale, sy(0.715)),
            (cx + w * 0.125 * scale, sy(0.715)),
            (cx, sy(0.865)),
        ],
        fill=WHITE,
    )
    return layer


def _background(size: int, round_shape: bool) -> Image.Image:
    """竖向渐变底（#2E7D32 -> #1B5E20），再裁成圆角或圆形，避免分层接缝。"""
    w = size * SS
    gradient = Image.new("RGBA", (w, w))
    px = gradient.load()
    for y in range(w):
        t = y / max(1, w - 1)
        color = tuple(
            int(GREEN[i] + (GREEN_DARK[i] - GREEN[i]) * t) for i in range(3)
        ) + (255,)
        for x in range(w):
            px[x, y] = color

    mask = Image.new("L", (w, w), 0)
    md = ImageDraw.Draw(mask)
    if round_shape:
        md.ellipse([0, 0, w - 1, w - 1], fill=255)
    else:
        md.rounded_rectangle([0, 0, w - 1, w - 1], radius=int(w * 0.22), fill=255)

    out = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    out.paste(gradient, (0, 0), mask)
    return out


def _downscale(img: Image.Image, size: int) -> Image.Image:
    return img.resize((size, size), Image.LANCZOS)


def build_legacy(size: int, round_shape: bool) -> Image.Image:
    bg = _background(size, round_shape)
    glyph = _glyph(size, scale=0.90)
    out = Image.alpha_composite(bg, glyph)
    return _downscale(out, size)


def build_foreground(size: int) -> Image.Image:
    # 自适应图标内容需落在中心 66/108 安全区内
    glyph = _glyph(size, scale=0.62)
    return _downscale(glyph, size)


def ensure_dir(path: str) -> None:
    os.makedirs(path, exist_ok=True)


def main() -> None:
    for density, size in DENSITIES.items():
        folder = os.path.join(RES, f"mipmap-{density}")
        ensure_dir(folder)

        legacy = build_legacy(size, round_shape=False)
        legacy.save(os.path.join(folder, "ic_launcher.png"), optimize=True)

        rounded = build_legacy(size, round_shape=True)
        rounded.save(os.path.join(folder, "ic_launcher_round.png"), optimize=True)

        fg = build_foreground(ADAPTIVE_SIZES[density])
        fg.save(os.path.join(folder, "ic_launcher_foreground.png"), optimize=True)

        print(f"{density}: launcher {size}px, foreground {ADAPTIVE_SIZES[density]}px")

    print("done")


if __name__ == "__main__":
    main()
