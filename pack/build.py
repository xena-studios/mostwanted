#!/usr/bin/env python3
"""Builds the Java resource pack: pack/out/mostwanted-pack.zip and its SHA-1.

Each of our items is a vanilla item carrying a custom model data string such as
"mostwanted:pistol" (core's CustomItems). The pack overrides each base item to show
our model when that string is there, and the vanilla model otherwise, so players
without the pack (and Bedrock players) still see a plain item, never a missing one.

Which vanilla item each gun and ammo uses comes from core's guns.yml, so the two
can't drift apart. Textures are pack/textures/<name>.png, 16x16. Every item must
have one, or the build fails.

    python3 pack/build.py            # builds pack/out/
    python3 pack/build.py --check    # builds, and fails if anything's missing
"""
import hashlib
import io
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
GUNS = REPO / "core/src/main/resources/guns.yml"
OUT = ROOT / "out"

# 26.3's resource pack format (version.json in the server jar: resource_major).
PACK_FORMAT = 97

# Items core makes that aren't in guns.yml: name -> (vanilla base, held like a tool?)
FIXED = {
    "handcuffs": ("tripwire_hook", True),
    "keycard": ("trial_key", False),
    # Menu icons: the hotbar item that opens the menu, and the buttons inside it.
    "menu": ("nether_star", False),
    "settings": ("comparator", False),
    "store": ("emerald", False),
    "leaderboards": ("golden_helmet", False),
    "account": ("name_tag", False),
}

# Gun skins from the store (core's Cosmetic): skin -> the gun it's for. A skin shows on
# the gun's own vanilla base, so it falls back to that like the gun does.
SKINS = {
    "pistol_gilded": "pistol",
    "shotgun_copper": "shotgun",
    "rifle_midnight": "rifle",
    "taser_diamond": "taser",
}

# A fixed date for every file, so the same sources always make the same zip (and SHA-1).
ZIP_DATE = (2026, 1, 1, 0, 0, 0)


def read_guns():
    """The guns and ammo in guns.yml, without a YAML library: its layout is simple and ours."""
    items, section, name = {}, None, None
    for raw in GUNS.read_text().splitlines():
        line = raw.split("#", 1)[0].rstrip()
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip())
        key, _, value = line.strip().partition(":")
        value = value.strip()
        if indent == 0:
            section = key if key in ("guns", "ammo") else None
        elif section and indent == 2:
            name = key
        elif section and indent == 4 and key == "item":
            model = name if section == "guns" else f"ammo_{name}"
            items[model] = (value.removeprefix("minecraft:"), section == "guns")
    if not items:
        sys.exit(f"no guns or ammo found in {GUNS}")
    return items


def build():
    items = {**read_guns(), **FIXED}
    for skin, gun in SKINS.items():
        if gun not in items:
            sys.exit(f"skin {skin} is for {gun}, which isn't in {GUNS}")
        items[skin] = items[gun]
    missing = [name for name in items if not (ROOT / "textures" / f"{name}.png").exists()]
    if missing:
        sys.exit("no texture for: " + ", ".join(missing) + " (add pack/textures/<name>.png, 16x16)")

    files = {
        "pack.mcmeta": json.dumps({"pack": {
            "description": "MostWanted",
            "min_format": PACK_FORMAT,
            "max_format": PACK_FORMAT,
        }}, indent=2),
        "pack.png": (ROOT / "pack.png").read_bytes(),
    }

    # One override per vanilla base: our models by custom model data, else vanilla's.
    by_base = {}
    for name, (base, held) in sorted(items.items()):
        by_base.setdefault(base, []).append(name)
        files[f"assets/mostwanted/models/item/{name}.json"] = json.dumps({
            "parent": "minecraft:item/handheld" if held else "minecraft:item/generated",
            "textures": {"layer0": f"mostwanted:item/{name}"},
        }, indent=2)
        files[f"assets/mostwanted/textures/item/{name}.png"] = (ROOT / "textures" / f"{name}.png").read_bytes()
    for base, names in sorted(by_base.items()):
        files[f"assets/minecraft/items/{base}.json"] = json.dumps({"model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [{"when": f"mostwanted:{name}",
                       "model": {"type": "minecraft:model", "model": f"mostwanted:item/{name}"}} for name in names],
            "fallback": {"type": "minecraft:model", "model": f"minecraft:item/{base}"},
        }}, indent=2)

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as zip_file:
        for path in sorted(files):
            info = zipfile.ZipInfo(path, ZIP_DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            data = files[path]
            zip_file.writestr(info, data.encode() if isinstance(data, str) else data)
    data = buffer.getvalue()
    sha1 = hashlib.sha1(data).hexdigest()

    OUT.mkdir(exist_ok=True)
    (OUT / "mostwanted-pack.zip").write_bytes(data)
    (OUT / "mostwanted-pack.sha1").write_text(sha1 + "\n")
    print(f"{len(items)} items, {len(data)} bytes, sha1 {sha1}")


if __name__ == "__main__":
    build()
