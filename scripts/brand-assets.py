#!/usr/bin/env python3
"""Builds the brand images in brand/out/ from the sources in brand/source/.

The look: the pixel-art desert, blurred, with the logo centred on top.

    python3 scripts/brand-assets.py

Needs Pillow (pip install pillow).
"""
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "brand" / "source"
OUT = ROOT / "brand" / "out"

DESERT = Image.open(SOURCE / "desert.png").convert("RGB")
WORDMARK = Image.open(SOURCE / "logo-wordmark.png").convert("RGBA")
MONOGRAM = Image.open(SOURCE / "logo-monogram.png").convert("RGBA")


def background(width: int, height: int, blur: float) -> Image.Image:
    """The desert, scaled to cover width x height (pixels kept sharp), then blurred."""
    scale = max(width / DESERT.width, height / DESERT.height)
    size = (round(DESERT.width * scale), round(DESERT.height * scale))
    scene = DESERT.resize(size, Image.Resampling.NEAREST)
    left = (scene.width - width) // 2
    top = (scene.height - height) // 2
    scene = scene.crop((left, top, left + width, top + height))
    return scene.filter(ImageFilter.GaussianBlur(blur * width / 1280))


def place(canvas: Image.Image, logo: Image.Image, width_share: float, shadow: bool = True) -> Image.Image:
    """Centres the logo at width_share of the canvas width, with a soft drop shadow."""
    width = round(canvas.width * width_share)
    height = round(logo.height * width / logo.width)
    if height > canvas.height * 0.8:
        height = round(canvas.height * 0.8)
        width = round(logo.width * height / logo.height)
    logo = logo.resize((width, height), Image.Resampling.LANCZOS)
    x = (canvas.width - width) // 2
    y = (canvas.height - height) // 2
    out = canvas.convert("RGBA")
    if shadow:
        alpha = logo.getchannel("A").point(lambda a: a * 0.45)
        glow = Image.new("RGBA", logo.size, (40, 12, 0, 0))
        glow.putalpha(alpha)
        glow = glow.filter(ImageFilter.GaussianBlur(max(2, width / 120)))
        out.alpha_composite(glow, (x, y + max(2, height // 30)))
    out.alpha_composite(logo, (x, y))
    return out


def key_art(name: str, width: int, height: int, logo: Image.Image, share: float) -> None:
    image = place(background(width, height, blur=6), logo, share)
    image.convert("RGB").save(OUT / name, optimize=True)


def icon(name: str, size: int, blur: float) -> None:
    image = place(background(size, size, blur=blur), MONOGRAM, 0.8, shadow=size >= 256)
    image.convert("RGB").save(OUT / name, optimize=True)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    # Website hero, video thumbnails, Minehut listing.
    key_art("key-art-1920x1080.png", 1920, 1080, WORDMARK, 0.72)
    key_art("key-art-1280x720.png", 1280, 720, WORDMARK, 0.72)
    # Link previews (Discord, X, iMessage) and the Discord server banner.
    key_art("og-1200x630.png", 1200, 630, WORDMARK, 0.78)
    key_art("discord-banner-960x540.png", 960, 540, WORDMARK, 0.78)
    # Profile pictures and the server-list icon. The Minecraft server icon must be 64x64.
    icon("icon-1024.png", 1024, blur=10)
    icon("icon-512.png", 512, blur=10)
    icon("server-icon.png", 64, blur=10)
    for path in sorted(OUT.iterdir()):
        print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
