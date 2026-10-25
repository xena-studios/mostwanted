# Resource pack

We want custom textures and items. The catch: **Java and Bedrock use different resource pack formats**, so a Java pack does nothing for Bedrock players. The plan is to make two packs from one source.

## How it works

```
                    ┌─▶ Java pack ──────▶ sent to Java players by the server (server.properties / plugin)
 our art + models ──┤
                    └─▶ Bedrock pack ──▶ sent to Bedrock players by Geyser (drop the .mcpack in Geyser's packs folder)
                                           + Geyser "mappings" that tell it which Java item = which Bedrock item
```

1. **Build the Java pack first.** It's the source of truth.
2. **Convert it to a Bedrock pack.** GeyserMC has tools for this (look at **Rainbow** and **java2bedrock.sh**). Check which one fits our Minecraft version.
3. **Custom items need mappings.** On Java, a custom item is a vanilla item with a custom model (e.g. a stick with an `item_model` / custom model data). Geyser needs a mapping file to turn that into a real custom item on Bedrock. The converter tools can generate these.
4. **Make the packs required** for both editions so everyone sees the same thing.
5. **Test on Bedrock**: phone, console and Windows.

## What translates well, and what doesn't

| Thing | Java | Bedrock (via Geyser) | Difficulty |
| --- | --- | --- | --- |
| Retexture vanilla items/blocks | Easy | Easy (same textures, different folder layout) | 🟢 |
| Custom 2D items (flat sprite, like a keycard or cash) | Easy | Works with mappings | 🟢 |
| Custom 3D item models (handcuffs, taser) | Easy | Usually converts; complex models may need fixing | 🟡 |
| Custom sounds (alarm, cuff click) | Easy | Works, separate sound definitions | 🟡 |
| Font icons / emoji in chat & scoreboard (cash $, bounty ⭐) | Easy | Different system, needs separate glyph files | 🟡 |
| GUI textures (custom menus) | Doable | Bedrock UI is very different; often doesn't match | 🔴 |
| Custom armor (jumpsuit, cop uniform) | Doable | Needs Bedrock "attachables"; fiddly | 🔴 |
| Custom blocks | Hacky on Java too (reused block states) | Possible with Geyser custom blocks; extra work | 🔴 |

**Rule of thumb:** flat textures and simple items are easy on both. Menus, armor and blocks are where Bedrock gets painful.

## Workarounds for the hard ones

- **Jumpsuits / uniforms:** use **leather armor dyed orange / blue**. That works on both editions with zero pack work. Add custom armor later if we want.
- **Menus:** keep chest GUIs simple, with icons made from items. Don't rely on a custom GUI background.
- **Blocks:** retexture a vanilla block we don't otherwise use, rather than making new blocks.

## The game must work without the pack

Every custom item is a vanilla item underneath with a custom name and lore (e.g. "Handcuffs" is a renamed item). If the pack fails to load, or a Bedrock texture breaks, the game still works and just looks plainer.

## Custom things we might want

| Item | Base vanilla item (idea) | Bedrock difficulty |
| --- | --- | --- |
| Handcuffs | | 🟡 3D model |
| Taser | | 🟡 3D model |
| Keycard | paper | 🟢 |
| Cash / money bag | | 🟢 |
| Lockpick / crowbar | | 🟢 |
| Police badge | | 🟢 |
| Bounty ⭐ / cash $ icons in HUD/chat | font glyphs | 🟡 |
| Robbery alarm sound | | 🟡 |
| Prison jumpsuit | dyed leather armor (no pack) | 🟢 now, 🔴 if custom |

## Tools to look into

- **Rainbow / java2bedrock.sh** (GeyserMC): convert a Java pack into a Bedrock pack + Geyser mappings
- **Item/pack plugins** like ItemsAdder, Oraxen or Nexo: manage custom items and build the Java pack. Check each one's Geyser/Bedrock support before picking
- **Blockbench**: free tool for making models and textures. It can export for both Java and Bedrock

Verify all of these against our Minecraft version before committing. The custom item system changed a lot in 1.21.x.
