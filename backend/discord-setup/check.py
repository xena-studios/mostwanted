#!/usr/bin/env python3
"""Read-only check of the MostWanted Discord app and server: app settings, bot profile,
server settings, roles, invites and AutoMod. Changes nothing.

    python3 -I check.py

The bot token is read from ~/.config/mostwanted/discord.env and never printed.
"""
import json, os, urllib.request
tok = None
for line in open(os.path.expanduser("~/.config/mostwanted/discord.env")):
    if line.startswith("DISCORD_TOKEN="): tok = line.split("=",1)[1].strip()
def get(p):
    r = urllib.request.Request("https://discord.com/api/v10"+p, headers={"Authorization":"Bot "+tok,"User-Agent":"DiscordBot (https://github.com/xena-studios/mostwanted, 1.0)"})
    return json.load(urllib.request.urlopen(r))
a = get("/applications/@me"); f = a.get("flags",0)
print("APP name:", a["name"], "| icon set:", bool(a.get("icon")))
print("  description:", repr(a.get("description","")[:80]), "| tags:", a.get("tags"))
print("  public bot:", a.get("bot_public"), "| code grant:", a.get("bot_require_code_grant"))
print("  intents: members", bool(f & (1<<14|1<<15)), "| presence", bool(f & (1<<12|1<<13)), "| message content", bool(f & (1<<18|1<<19)))
print("  install contexts:", list((a.get("integration_types_config") or {}).keys()), "(0=guild,1=user) | custom install url:", a.get("custom_install_url"), "| install params:", a.get("install_params"))
print("  redirect uris:", a.get("redirect_uris"), "| interactions url:", a.get("interactions_endpoint_url"))
print("  terms:", a.get("terms_of_service_url"), "| privacy:", a.get("privacy_policy_url"))
u = get("/users/@me"); print("BOT user:", u["username"], "| avatar:", bool(u.get("avatar")), "| banner:", bool(u.get("banner")))
gs = get("/users/@me/guilds"); print("GUILDS:", [g["name"] for g in gs])
g = get(f"/guilds/{gs[0]['id']}?with_counts=true")
print("  verification", g["verification_level"], "| filter", g["explicit_content_filter"], "| notif", g["default_message_notifications"], "| mfa", g["mfa_level"], "| boost", g["premium_tier"], "| members", g.get("approximate_member_count"))
roles = sorted(g["roles"], key=lambda r: -r["position"])
for r in roles: print(f"  role {r['position']:>2} {r['name']:<16} #{r['color']:06x} hoist={r['hoist']} mention={r['mentionable']} managed={r['managed']}")
inv = get(f"/guilds/{g['id']}/invites"); print("  invites:", [(i['code'], i.get('max_age'), i['channel']['name']) for i in inv])
for rule in get(f"/guilds/{g['id']}/auto-moderation/rules"):
    md = rule.get("trigger_metadata",{})
    print("  automod:", rule["name"], "| enabled", rule["enabled"], "| type", rule["trigger_type"], "| limit", md.get("mention_total_limit"), "| raid", md.get("mention_raid_protection_enabled"), "| actions", [a_["type"] for a_ in rule["actions"]], "| words", len(md.get("keyword_filter",[])))
