#!/usr/bin/env python3
"""Build the MostWanted Discord server.

Idempotent: roles and channels are matched by name; missing things are created,
different things are updated, things not in the layout are deleted (the owner chose
to wipe the old server). Running it twice in a row must change nothing the second time.

    python3 -I build.py --dry-run    # read-only (GET only): print the inventory and the plan
    python3 -I build.py --apply      # make the changes

Python 3 standard library only. The bot token is read from ~/.config/mostwanted/discord.env
inside this script and is never printed, logged or written anywhere.
"""

import argparse
import base64
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# ---------------------------------------------------------------------------
# Settings
# ---------------------------------------------------------------------------

API = "https://discord.com/api/v10"
USER_AGENT = "DiscordBot (https://github.com/xena-studios/mostwanted, 1.0)"
TOKEN_FILE = os.path.expanduser("~/.config/mostwanted/discord.env")
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
STATE_FILE = os.path.join(HERE, ".build-state.json")  # image hashes only, never secrets; gitignored
APP_ID = "1564123260562440303"
AUDIT_REASON = "MostWanted build.py"
SITE = "https://mostwantedmc.com"
BRAND = 0xFF9933
MESSAGE_COUNT_CAP = 500  # stop counting old messages past this (cheap enough)

# ---------------------------------------------------------------------------
# Permissions (https://docs.discord.com/developers/topics/permissions)
# ---------------------------------------------------------------------------

P = {
    "CREATE_INSTANT_INVITE": 1 << 0, "KICK_MEMBERS": 1 << 1, "BAN_MEMBERS": 1 << 2,
    "ADMINISTRATOR": 1 << 3, "MANAGE_CHANNELS": 1 << 4, "MANAGE_GUILD": 1 << 5,
    "ADD_REACTIONS": 1 << 6, "VIEW_AUDIT_LOG": 1 << 7, "PRIORITY_SPEAKER": 1 << 8,
    "STREAM": 1 << 9, "VIEW_CHANNEL": 1 << 10, "SEND_MESSAGES": 1 << 11,
    "SEND_TTS_MESSAGES": 1 << 12, "MANAGE_MESSAGES": 1 << 13, "EMBED_LINKS": 1 << 14,
    "ATTACH_FILES": 1 << 15, "READ_MESSAGE_HISTORY": 1 << 16, "MENTION_EVERYONE": 1 << 17,
    "USE_EXTERNAL_EMOJIS": 1 << 18, "VIEW_GUILD_INSIGHTS": 1 << 19, "CONNECT": 1 << 20,
    "SPEAK": 1 << 21, "MUTE_MEMBERS": 1 << 22, "DEAFEN_MEMBERS": 1 << 23,
    "MOVE_MEMBERS": 1 << 24, "USE_VAD": 1 << 25, "CHANGE_NICKNAME": 1 << 26,
    "MANAGE_NICKNAMES": 1 << 27, "MANAGE_ROLES": 1 << 28, "MANAGE_WEBHOOKS": 1 << 29,
    "MANAGE_GUILD_EXPRESSIONS": 1 << 30, "USE_APPLICATION_COMMANDS": 1 << 31,
    "REQUEST_TO_SPEAK": 1 << 32, "MANAGE_EVENTS": 1 << 33, "MANAGE_THREADS": 1 << 34,
    "CREATE_PUBLIC_THREADS": 1 << 35, "CREATE_PRIVATE_THREADS": 1 << 36,
    "USE_EXTERNAL_STICKERS": 1 << 37, "SEND_MESSAGES_IN_THREADS": 1 << 38,
    "USE_EMBEDDED_ACTIVITIES": 1 << 39, "MODERATE_MEMBERS": 1 << 40,
    "VIEW_CREATOR_MONETIZATION_ANALYTICS": 1 << 41, "USE_SOUNDBOARD": 1 << 42,
    "CREATE_GUILD_EXPRESSIONS": 1 << 43, "CREATE_EVENTS": 1 << 44,
    "USE_EXTERNAL_SOUNDS": 1 << 45, "SEND_VOICE_MESSAGES": 1 << 46,
    "SET_VOICE_CHANNEL_STATUS": 1 << 48, "SEND_POLLS": 1 << 49, "USE_EXTERNAL_APPS": 1 << 50,
    "PIN_MESSAGES": 1 << 51, "BYPASS_SLOWMODE": 1 << 52,
}


def perms(*names):
    v = 0
    for n in names:
        v |= P[n]
    return v


def perm_names(bits):
    return [n for n, v in P.items() if bits & v] or ["none"]


VIEW = P["VIEW_CHANNEL"]
# "Read-only": read and react, but not send or start threads.
READ_ONLY = perms("SEND_MESSAGES", "SEND_MESSAGES_IN_THREADS", "CREATE_PUBLIC_THREADS",
                  "CREATE_PRIVATE_THREADS")

# ---------------------------------------------------------------------------
# Layout
# ---------------------------------------------------------------------------

SERVER = {
    "name": "MostWanted",
    "description": "Cops vs. prisoners on Java & Bedrock. mostwantedmc.com",
    "icon": REPO + "/brand/out/icon-512.png",
    "banner": REPO + "/brand/out/discord-banner-960x540.png",  # banner + invite splash, if boosts allow
    "verification_level": 2,            # Medium
    "explicit_content_filter": 2,       # all members
    "default_message_notifications": 1,  # only mentions
    "preferred_locale": "en-US",
    "rules_channel": "rules",
    "public_updates_channel": "admin",
    "system_channel": "general",
}
# System messages: joins and boosts on, the "wave/sticker reply" on joins off.
SYS_SUPPRESS_JOIN = 1 << 0
SYS_SUPPRESS_BOOST = 1 << 1
SYS_SUPPRESS_JOIN_REPLIES = 1 << 3

EVERYONE_PERMS = perms(
    "VIEW_CHANNEL", "SEND_MESSAGES", "READ_MESSAGE_HISTORY", "ADD_REACTIONS", "EMBED_LINKS",
    "ATTACH_FILES", "USE_EXTERNAL_EMOJIS", "USE_APPLICATION_COMMANDS", "SEND_MESSAGES_IN_THREADS",
    "CREATE_PUBLIC_THREADS", "CONNECT", "SPEAK", "USE_VAD", "STREAM", "CHANGE_NICKNAME",
)

# "manage messages" also covers pinning and bypassing slowmode, which Discord split
# into their own permissions (PIN_MESSAGES, BYPASS_SLOWMODE).
MANAGE_MSGS = ("MANAGE_MESSAGES", "PIN_MESSAGES", "BYPASS_SLOWMODE")

# Top to bottom. BOOSTER and OTHER_BOTS are placeholders for managed roles we don't edit.
BOOSTER = "<Server Booster>"
OTHER_BOTS = "<other bots' roles>"
ROLES = [
    {"name": "Owner", "color": 0xFF3399, "hoist": True, "mentionable": False,
     "permissions": perms("ADMINISTRATOR")},
    {"name": "Admin", "color": 0xFF33FF, "hoist": True, "mentionable": False,
     "permissions": perms("ADMINISTRATOR")},
    {"name": "Moderator", "color": 0xCC33FF, "hoist": True, "mentionable": True,
     "permissions": perms("KICK_MEMBERS", "BAN_MEMBERS", "MODERATE_MEMBERS", *MANAGE_MSGS,
                          "MANAGE_THREADS", "MANAGE_NICKNAMES", "MUTE_MEMBERS",
                          "DEAFEN_MEMBERS", "MOVE_MEMBERS", "VIEW_AUDIT_LOG")},
    {"name": "Helper", "color": 0x9933FF, "hoist": True, "mentionable": True,
     "permissions": perms("MODERATE_MEMBERS", *MANAGE_MSGS, "MANAGE_THREADS")},
    {"name": "Guard", "color": 0x3399FF, "hoist": True, "mentionable": False, "permissions": 0},
    {"name": "Creator", "color": 0xFFCC33, "hoist": True, "mentionable": False, "permissions": 0},
    BOOSTER,
    OTHER_BOTS,
    {"name": "Linked", "color": 0x999999, "hoist": False, "mentionable": False, "permissions": 0},
    {"name": "Updates", "color": 0, "hoist": False, "mentionable": False, "permissions": 0},  # admins can still ping it
]
LAYOUT_ROLES = [r for r in ROLES if isinstance(r, dict)]
STAFF_ROLES = ["Owner", "Admin", "Moderator", "Helper"]

# Permission overwrites: role name (or "@everyone") -> (allow, deny)
RO = {"@everyone": (0, READ_ONLY)}
STAFF_ONLY = {"@everyone": (0, VIEW), "Helper": (VIEW, 0), "Moderator": (VIEW, 0)}
ADMIN_ONLY = {"@everyone": (0, VIEW)}
ADMIN_FEED = {"@everyone": (0, VIEW | READ_ONLY)}

TEXT, VOICE, CATEGORY, NEWS, FORUM = 0, 2, 4, 5, 15
TYPE_NAMES = {0: "text", 2: "voice", 4: "category", 5: "announcement", 13: "stage", 15: "forum",
              16: "media"}


# Display names: channels "<emoji>│<name>" (U+2502, no spaces), categories "<emoji> <NAME>".
# The plain name stays the key used everywhere in this file, and existing channels are matched
# by either the plain or the display name, so older builds get renamed, never duplicated.
SEP = "\u2502"
CATEGORY_EMOJI = {"INFORMATION": "📌", "COMMUNITY": "💬", "GAME": "🎮", "SUPPORT": "🎫",
                  "STAFF": "🛡️", "ADMIN": "🔒"}
CHANNEL_EMOJI = {
    "welcome": "👋", "rules": "📜", "announcements": "📢", "updates": "🛠️", "link": "🔗",
    "general": "💬", "crews": "🤝", "media": "📸", "suggestions": "💡", "bot-commands": "🤖",
    "Lounge": "🎧", "Crew 1": "🔊", "Crew 2": "🔊", "Crew 3": "🔊",
    "server-status": "🟢", "wanted": "🚨",
    "support": "🎫", "bug-reports": "🐛", "apply": "📝",
    "staff-chat": "💬", "anticheat": "🕵️", "Staff": "🔊",
    "admin": "🔒", "bot-log": "🧾",
}


def display_name(name, type_):
    if type_ == CATEGORY:
        return f"{CATEGORY_EMOJI[name]} {name}"
    return f"{CHANNEL_EMOJI[name]}{SEP}{name}"


def ch(name, type_=TEXT, topic=None, ow=None, slowmode=0, user_limit=0, tags=None):
    return {"name": name, "display": display_name(name, type_), "type": type_, "topic": topic,
            "ow": ow, "slowmode": slowmode,
            "user_limit": user_limit, "tags": tags or []}


CATEGORIES = [
    {"name": "INFORMATION", "ow": RO, "channels": [
        ch("welcome", topic="What MostWanted is, how to join, and links."),
        ch("rules", topic="The rules, in game and here. Read them before you play."),
        ch("announcements", NEWS, topic="News from the MostWanted team. Follow it to get it in your own server."),
        ch("updates", NEWS, topic="Patch notes. Posts here ping the Updates role."),
        ch("link", topic="Link your Discord and Minecraft accounts on mostwantedmc.com."),
    ]},
    {"name": "COMMUNITY", "ow": {}, "channels": [
        ch("general", topic="Talk about MostWanted, or anything else. English, please."),
        ch("crews", topic="Find people to escape with or patrol with."),
        ch("media", topic="Screenshots and clips from MostWanted. 30s slowmode.", slowmode=30),
        ch("suggestions", FORUM,
           topic=("One idea per post. Search first: if it's already there, react and add to that "
                  "post instead. Say what you want and why it makes the game better, and pick a tag."),
           tags=[("Gameplay", False), ("Guards", False), ("Website", False), ("Discord", False),
                 ("Other", False)]),
        ch("bot-commands", topic="Use bot commands here."),
        ch("Lounge", VOICE),
        ch("Crew 1", VOICE, user_limit=6),
        ch("Crew 2", VOICE, user_limit=6),
        ch("Crew 3", VOICE, user_limit=6),
    ]},
    {"name": "GAME", "ow": RO, "channels": [
        ch("server-status", topic="Live server status, kept up to date by the bot."),
        ch("wanted", topic="Robberies, riots and new Most Wanted, live from the game."),
    ]},
    {"name": "SUPPORT", "ow": {}, "channels": [
        ch("support", topic="Report a player or a bug, or appeal a punishment: on mostwantedmc.com/support.",
           ow=RO),
        ch("bug-reports", FORUM,
           topic=("Bugs that aren't private. Say what happened, where, and how to make it happen "
                  "again, with a screenshot or clip if you can. Exploits (duping, gaps in walls, "
                  "anything that gives an advantage) go in a private ticket on "
                  "mostwantedmc.com/support, never here."),
           tags=[("Game", False), ("Website", False), ("Discord", False), ("Bedrock", False),
                 ("Fixed", True)]),
        ch("apply", topic="Apply for guard, staff or creator on mostwantedmc.com/apply.", ow=RO),
    ]},
    {"name": "STAFF", "ow": STAFF_ONLY, "channels": [
        ch("staff-chat", topic="Staff talk. Player info stays here, never in public."),
        ch("anticheat", topic="Grim anticheat alerts (coming later)."),
        ch("Staff", VOICE),
    ]},
    {"name": "ADMIN", "ow": ADMIN_ONLY, "channels": [
        ch("admin", topic="Admins only. Discord's community updates land here too."),
        ch("bot-log", topic="What the bot did, and AutoMod alerts (bot feed).", ow=ADMIN_FEED),
    ]},
]

# ---------------------------------------------------------------------------
# Posts (embeds + link buttons). "{#name}" becomes a channel mention.
# No thumbnails or footers; the only image is the banner at the top of #welcome.
# ---------------------------------------------------------------------------

OG_URL = SITE + "/og.png"


def link(label, url, emoji):
    return {"type": 2, "style": 5, "label": label, "url": url, "emoji": {"name": emoji}}


def row(*buttons):
    return {"type": 1, "components": list(buttons)}


def embed(title=None, description=None, image=None):
    e = {"color": BRAND}
    if title:
        e["title"] = title
    if description:
        e["description"] = description
    if image:
        e["image"] = {"url": image}
    return e


def rule_list(items, section):
    return "\n".join(f"**{section}.{i} {t}** — {b}" for i, (t, b) in enumerate(items, 1))


POSTS = {
    "welcome": {
        "embeds": [
            # Discord draws an embed's image below its text, so the banner gets its own
            # image-only embed to sit at the top.
            embed(image=OG_URL),
            embed("🚔 Welcome to MostWanted",
                  "**Inmates break out. Guards hunt them down.**\n"
                  "One persistent prison town on **Java & Bedrock**.\n\n"
                  "**🎮 How to join**\n"
                  "Open **Minehut** and search **MostWanted**.\n"
                  "Opening soon — follow {#announcements}.\n\n"
                  "**🧭 Start here**\n"
                  "{#rules} — the rules\n"
                  "{#updates} — patch notes\n"
                  "{#link} — link Discord and Minecraft\n"
                  "{#general} — chat\n"
                  "{#crews} — find a crew\n"
                  "{#support} — help, reports and appeals\n"
                  "{#apply} — become a guard, staff or creator"),
        ],
        "components": [row(link("Website", SITE, "🌐"), link("Rules", SITE + "/rules", "📜"),
                           link("Store", SITE + "/store", "🛒"), link("Wiki", SITE + "/wiki", "📖"))],
    },
    "rules": {
        "embeds": [
            embed("📜 Rules",
                  "Fighting, robbing and breaking out are the game. These rules keep it fair, "
                  "**in game and here**. Staff may act on anything that ruins the game for others."),
            embed("⚖️ Play fair", rule_list([
                ("No cheats", "no hacked clients, x-ray, auto-clickers, macros or advantage mods."),
                ("No exploits", "found a bug, a gap in a wall or a dupe? Report it. Using it is a ban."),
                ("One account", "no alts to dodge a ban, farm bounties or help yourself."),
                ("No real-money trading", "cash, items and accounts aren't for sale."),
            ], 1)),
            embed("🤝 Respect people", rule_list([
                ("No harassment or hate", "no hate speech, threats or sharing personal info."),
                ("No spam or ads", "don't spam or advertise other servers."),
                ("Don't impersonate staff", "pretending to be staff is a ban."),
                ("Trash talk the game, not the person", "banter is fine, personal attacks aren't."),
            ], 2)),
            embed("🛡️ Guards", rule_list([
                ("Never help criminals", "no letting friends escape, tip-offs or passing gear."),
                ("Only the fair targets", "arrest or taser players who are escaping, rioting or wanted."),
                ("Off duty is off duty", "an off-duty guard is just another player."),
            ], 3)),
            embed("💬 Discord", rule_list([
                ("Keep it on topic", "post in the channel that fits."),
                ("English in public channels", "so everyone can follow along."),
                ("No DM advertising", "no ads or invites in members' DMs."),
                ("Staff decisions are final here", "appeal on the website, not in chat."),
            ], 4)),
            embed("🔨 Minehut & punishments",
                  "MostWanted runs through **Minehut**, so Minehut's rules apply too.\n"
                  "Punishments fit the offence: a **warning**, **mute**, **temporary** or **permanent ban**.\n"
                  "Cheating and exploiting are a **ban the first time**.\n"
                  "Think we got it wrong? **Appeal on mostwantedmc.com/support**."),
        ],
        "components": [row(link("Full rules", SITE + "/rules", "📜"))],
    },
    "link": {
        "embeds": [embed(
            "🔗 Link your accounts",
            "Connect **Discord** and **Minecraft** once and you get:\n"
            "✔ the **Linked** role\n"
            "✔ your **guard and staff ranks**, synced here and in game\n"
            "✔ **notifications** in Discord and in game\n\n"
            "Sign in with Discord, then connect your **Java** or **Bedrock** account.",
        )],
        "components": [row(link("Link your accounts", SITE + "/login", "🔗"))],
    },
    "support": {
        "embeds": [embed(
            "🎫 Support",
            "Open a ticket on **mostwantedmc.com** and staff handle it there.\n\n"
            "🚩 **Report a player** — cheating, abuse, guards breaking the code\n"
            "🐛 **Bugs** — public ones in {#bug-reports}, exploits in a private ticket\n"
            "⚖️ **Appeals** — think a punishment was wrong?\n"
            "💳 **Store help** — purchases and payments\n\n"
            "✉ Replies reach you on the site, by Discord DM and in game.",
        )],
        "components": [row(link("Get support", SITE + "/support", "🎫"))],
    },
    "apply": {
        "embeds": [embed(
            "📝 Join the team",
            "Guards, staff and creators are picked by application on **mostwantedmc.com**.\n\n"
            "🛡️ **Guard** — know the game, clean record, follow the guard code\n"
            "⭐ **Staff** — 13+, active, calm and fair\n"
            "🎥 **Creator** — regular MostWanted videos or streams\n\n"
            "✉ Decisions reach you on the site, by Discord DM and in game.",
        )],
        "components": [row(link("Guard", SITE + "/apply#guard", "🛡️"),
                           link("Staff", SITE + "/apply#staff", "⭐"),
                           link("Creator", SITE + "/apply#creator", "🎥"))],
    },
}

# ---------------------------------------------------------------------------
# AutoMod
# ---------------------------------------------------------------------------

INVITE_REGEX = r"discord(?:\.gg|(?:app)?\.com/invite)/"


def automod_rules(alert_channel, role_id):
    alert = {"type": 2, "metadata": {"channel_id": alert_channel}}
    block = {"type": 1, "metadata": {}}
    return [
        {"name": "Block spam", "trigger_type": 3, "trigger_metadata": {},
         "actions": [block, alert], "exempt_roles": []},
        {"name": "Block Mention Spam", "trigger_type": 5,
         "trigger_metadata": {"mention_total_limit": 5, "mention_raid_protection_enabled": True},
         "actions": [block, {"type": 3, "metadata": {"duration_seconds": 600}}, alert],
         "exempt_roles": []},
        {"name": "Invite links", "trigger_type": 1,
         "trigger_metadata": {"keyword_filter": [], "regex_patterns": [INVITE_REGEX], "allow_list": []},
         "actions": [block, alert], "exempt_roles": [role_id(n) for n in STAFF_ROLES]},
        {"name": "Slurs and sexual content", "trigger_type": 4,
         "trigger_metadata": {"presets": [2, 3], "allow_list": []},
         "actions": [block, alert], "exempt_roles": []},
    ]


# Old rules the owner chose to delete (matched by name).
AUTOMOD_DELETE = ["Block Custom Words"]

SINGLETON_TRIGGERS = {3, 4, 5}
# Rules created by Discord itself (e.g. Community's built-in mention-spam rule): bots get 404
# on PATCH and can't delete them, so the owner edits them in Server Settings > AutoMod.
DISCORD_AUTOMOD_USER = "1008776202191634432"  # Discord allows one rule per guild of these types
TRIGGER_NAMES = {1: "keyword", 3: "spam", 4: "keyword preset", 5: "mention spam", 6: "member profile"}

# ---------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------


class ApiError(Exception):
    def __init__(self, method, path, status, body):
        self.status = status
        try:
            self.json = json.loads(body)
        except ValueError:
            self.json = {}
        super().__init__(f"{method} {path} -> HTTP {status}: {body[:400]}")


def read_token():
    if not os.path.exists(TOKEN_FILE):
        sys.exit(f"Token file missing: {TOKEN_FILE}. Stopping.")
    with open(TOKEN_FILE, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line.startswith("export "):
                line = line[7:].strip()
            if line.startswith("DISCORD_TOKEN="):
                tok = line.split("=", 1)[1].strip().strip('"').strip("'")
                if tok:
                    return tok
    sys.exit(f"No DISCORD_TOKEN in {TOKEN_FILE}. Stopping.")


class Discord:
    def __init__(self, token, dry_run):
        self._auth = "Bot " + token
        self.dry_run = dry_run

    def req(self, method, path, body=None, query=None):
        if self.dry_run and method != "GET":
            raise RuntimeError(f"dry run: refusing to send {method} {path}")
        url = API + path + ("?" + urllib.parse.urlencode(query) if query else "")
        data = json.dumps(body).encode() if body is not None else None
        headers = {"Authorization": self._auth, "User-Agent": USER_AGENT}
        if data is not None:
            headers["Content-Type"] = "application/json"
        if method != "GET":
            headers["X-Audit-Log-Reason"] = urllib.parse.quote(AUDIT_REASON)
        for attempt in range(8):
            request = urllib.request.Request(url, data=data, method=method, headers=headers)
            try:
                with urllib.request.urlopen(request, timeout=30) as resp:
                    raw = resp.read()
                    self._pace(resp.headers)
                    return json.loads(raw) if raw else None
            except urllib.error.HTTPError as e:
                raw = e.read().decode(errors="replace")
                if e.code == 429:
                    try:
                        wait = float(json.loads(raw).get("retry_after", 1))
                    except ValueError:
                        wait = float(e.headers.get("Retry-After", 1))
                    time.sleep(wait + 0.25)
                    continue
                if e.code >= 500 and attempt < 3:
                    time.sleep(2 * (attempt + 1))
                    continue
                raise ApiError(method, path, e.code, raw) from None
            except urllib.error.URLError as e:
                if attempt < 3:
                    time.sleep(2 * (attempt + 1))
                    continue
                raise ApiError(method, path, 0, str(e.reason)) from None
        raise ApiError(method, path, 429, "still rate limited after retries")

    @staticmethod
    def _pace(h):
        if h.get("X-RateLimit-Remaining") == "0":
            time.sleep(float(h.get("X-RateLimit-Reset-After", "1")) + 0.1)

    def get(self, path, query=None, ok_errors=()):
        try:
            return self.req("GET", path, query=query)
        except ApiError as e:
            if e.status in ok_errors:
                return e
            raise


# ---------------------------------------------------------------------------
# Plan / execution
# ---------------------------------------------------------------------------


class Plan:
    """Collects actions. In a dry run nothing is sent; created objects get fake ids."""

    def __init__(self, api):
        self.api = api
        self.dry = api.dry_run
        self.actions = []   # (group, text)
        self.failed = []    # (group, text, reason)
        self.cannot = []    # things the bot can't do, with why
        self.owner_todo = []
        self.notes = []
        self._fake = 0

    def act(self, group, text, method, path, body=None):
        self.actions.append((group, text))
        if self.dry:
            self._fake += 1
            return {"id": f"new{self._fake}", "_fake": True}
        print(f"  [{group}] {text}", flush=True)
        try:
            res = self.api.req(method, path, body)
            time.sleep(0.25)  # be gentle on top of the rate-limit headers
            return res if res is not None else {}
        except ApiError as e:
            self.failed.append((group, text, str(e)))
            print(f"    FAILED: {e}", flush=True)
            return None


def hexcol(c):
    return f"#{c:06x}" if c else "none"


def norm(s):
    return (s or "").casefold()


def load_state():
    try:
        with open(STATE_FILE, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def save_state(state):
    with open(STATE_FILE, "w", encoding="utf-8") as f:
        json.dump(state, f, indent=2)


def image_data(path):
    with open(path, "rb") as f:
        raw = f.read()
    return hashlib.sha256(raw).hexdigest(), "data:image/png;base64," + base64.b64encode(raw).decode()


# ---------------------------------------------------------------------------
# Inventory
# ---------------------------------------------------------------------------


def count_messages(api, cid, cap=MESSAGE_COUNT_CAP):
    n, before = 0, None
    while n < cap:
        q = {"limit": 100}
        if before:
            q["before"] = before
        r = api.get(f"/channels/{cid}/messages", q, ok_errors=(403, 404))
        if isinstance(r, ApiError):
            return None
        n += len(r)
        if len(r) < 100:
            return n
        before = r[-1]["id"]
    return f"{cap}+"


def count_forum_posts(api, cid, active_threads):
    n = sum(1 for t in active_threads if t.get("parent_id") == cid)
    before = None
    for _ in range(5):
        q = {"limit": 100}
        if before:
            q["before"] = before
        r = api.get(f"/channels/{cid}/threads/archived/public", q, ok_errors=(403, 404))
        if isinstance(r, ApiError):
            break
        n += len(r.get("threads", []))
        if not r.get("has_more"):
            return n
        before = r["threads"][-1]["thread_metadata"]["archive_timestamp"]
    return n


def inventory(api):
    me = api.get("/users/@me")
    guilds = api.get("/users/@me/guilds")
    if len(guilds) != 1:
        names = ", ".join(g["name"] for g in guilds) or "none"
        sys.exit(f"The bot must be in exactly one server; it's in {len(guilds)}: {names}. Stopping.")
    gid = guilds[0]["id"]
    inv = {"me": me, "gid": gid}
    inv["guild"] = api.get(f"/guilds/{gid}", {"with_counts": "true"})
    inv["roles"] = api.get(f"/guilds/{gid}/roles")
    inv["channels"] = api.get(f"/guilds/{gid}/channels")
    inv["bot_member"] = api.get(f"/guilds/{gid}/members/{me['id']}")
    owner_id = inv["guild"]["owner_id"]
    inv["owner_member"] = api.get(f"/guilds/{gid}/members/{owner_id}", ok_errors=(403, 404))
    for key, path in [("emojis", "emojis"), ("stickers", "stickers"), ("webhooks", "webhooks"),
                      ("events", "scheduled-events"), ("invites", "invites"),
                      ("integrations", "integrations"), ("automod", "auto-moderation/rules"),
                      ("soundboard", "soundboard-sounds"), ("onboarding", "onboarding"),
                      ("active_threads", "threads/active")]:
        r = api.get(f"/guilds/{gid}/{path}", ok_errors=(400, 403, 404))
        if isinstance(r, ApiError):
            r = {"_error": f"HTTP {r.status} {r.json.get('message', '')}".strip()}
        inv[key] = r
    if isinstance(inv["soundboard"], dict) and "items" in inv["soundboard"]:
        inv["soundboard"] = inv["soundboard"]["items"]
    if isinstance(inv["active_threads"], dict):
        inv["active_threads"] = inv["active_threads"].get("threads", [])
    rc = api.get(f"/guilds/{gid}/roles/member-counts", ok_errors=(400, 403, 404))
    inv["role_counts"] = {} if isinstance(rc, ApiError) else rc
    members = api.get(f"/guilds/{gid}/members", {"limit": 1000}, ok_errors=(400, 403))
    inv["members"] = None if isinstance(members, ApiError) else members
    # Old messages per channel (capped).
    counts = {}
    for c in inv["channels"]:
        if c["type"] in (0, 2, 5, 13):
            counts[c["id"]] = count_messages(api, c["id"])
        elif c["type"] in (15, 16):
            counts[c["id"]] = count_forum_posts(api, c["id"], inv["active_threads"])
    inv["counts"] = counts
    return inv


def print_inventory(inv):
    g, roles, chans = inv["guild"], inv["roles"], inv["channels"]
    bot_id = inv["me"]["id"]
    print("=" * 72)
    print(f"SERVER: {g['name']}  (id {g['id']})")
    print("=" * 72)
    owner = inv["owner_member"]
    owner_name = owner["user"]["username"] if isinstance(owner, dict) else g["owner_id"]
    print(f"Owner: {owner_name} ({g['owner_id']})   Members: ~{g.get('approximate_member_count')} "
          f"(~{g.get('approximate_presence_count')} online)")
    print(f"Boost level: {g['premium_tier']} ({g.get('premium_subscription_count', 0)} boosts)")
    print(f"Community: {'ON' if 'COMMUNITY' in g['features'] else 'off'}   Features: "
          f"{', '.join(sorted(g['features'])) or 'none'}")
    print(f"Verification {g['verification_level']}, content filter {g['explicit_content_filter']}, "
          f"notifications {g['default_message_notifications']}, locale {g['preferred_locale']}, "
          f"2FA for moderation {'on' if g['mfa_level'] else 'off'}")
    print(f"Description: {g.get('description')!r}  Icon: {'set' if g.get('icon') else 'none'}  "
          f"Banner: {'set' if g.get('banner') else 'none'}  Splash: {'set' if g.get('splash') else 'none'}")
    by_id = {c["id"]: c for c in chans}
    for label, key in [("System channel", "system_channel_id"), ("Rules channel", "rules_channel_id"),
                       ("Community updates", "public_updates_channel_id"),
                       ("Safety alerts", "safety_alerts_channel_id"), ("AFK channel", "afk_channel_id")]:
        if g.get(key):
            print(f"{label}: #{by_id.get(g[key], {}).get('name', g[key])}")

    print("\nChannels (old messages counted up to %d; forums: posts):" % MESSAGE_COUNT_CAP)
    cats = sorted([c for c in chans if c["type"] == 4], key=lambda c: (c["position"], c["id"]))
    def kids(pid):
        return sorted([c for c in chans if c.get("parent_id") == pid and c["type"] != 4],
                      key=lambda c: (c["type"] in (2, 13), c["position"], c["id"]))
    def line(c, indent):
        n = inv["counts"].get(c["id"])
        extra = f", {n} msgs" if n not in (None, 0) else (", empty" if n == 0 else "")
        print(f"{indent}{'#' if c['type'] != 2 else '(voice) '}{c['name']}  [{TYPE_NAMES.get(c['type'], c['type'])}{extra}]")
    for c in kids(None):
        line(c, "  ")
    for cat in cats:
        print(f"  {cat['name']}  [category]")
        for c in kids(cat["id"]):
            line(c, "    ")
    if inv["active_threads"]:
        print(f"  Active threads: {len(inv['active_threads'])}")

    bot_top = max((r["position"] for r in roles if r["id"] in inv["bot_member"]["roles"]), default=0)
    top = max(r["position"] for r in roles)
    print("\nRoles (top to bottom):")
    for r in sorted(roles, key=lambda r: (-r["position"], r["id"])):
        tags = r.get("tags") or {}
        kind = ""
        if "bot_id" in tags:
            kind = " [THIS BOT]" if tags["bot_id"] == bot_id else f" [bot {tags['bot_id']}]"
        elif "premium_subscriber" in tags:
            kind = " [Server Booster]"
        elif "integration_id" in tags:
            kind = " [integration]"
        elif r["managed"]:
            kind = " [managed]"
        p = int(r["permissions"])
        pdesc = "ADMINISTRATOR" if p & P["ADMINISTRATOR"] else f"{bin(p).count('1')} perms"
        held = inv["role_counts"].get(r["id"])
        held = f"  [{held} member{'s' if held != 1 else ''}]" if held is not None and r["id"] != g["id"] else ""
        print(f"  {r['position']:>3} {r['name']}  {hexcol(r['color'])}  hoist={r['hoist']} "
              f"mention={r['mentionable']}  {pdesc}{kind}{held}")
    bot_role = next((r for r in roles if (r.get("tags") or {}).get("bot_id") == bot_id), None)
    print(f"Bot's highest role position {bot_top} of {top}: "
          f"{'AT THE TOP' if bot_top == top else 'NOT at the top'}"
          + (f" (managed role '{bot_role['name']}')" if bot_role else ""))

    def show(title, items, fmt):
        if isinstance(items, dict) and "_error" in items:
            print(f"\n{title}: couldn't read ({items['_error']})")
            return
        print(f"\n{title}: {len(items) if items else 'none'}")
        for it in items or []:
            print("  " + fmt(it))

    show("Emojis", inv["emojis"], lambda e: f":{e['name']}:{' (animated)' if e.get('animated') else ''}")
    show("Stickers", inv["stickers"], lambda s: s["name"])
    show("Soundboard sounds", inv["soundboard"], lambda s: s.get("name", "?"))
    show("Webhooks", inv["webhooks"], lambda w: f"{w.get('name')} in #{by_id.get(w.get('channel_id'), {}).get('name', w.get('channel_id'))}"
         + (f" (app {w['application_id']})" if w.get("application_id") else ""))
    show("Scheduled events", inv["events"], lambda e: f"{e['name']} ({e.get('scheduled_start_time')})")
    show("Invites", inv["invites"], lambda i: f"{i['code']} -> #{(i.get('channel') or {}).get('name')} by "
         f"{(i.get('inviter') or {}).get('username', '?')}, uses {i.get('uses')}/{i.get('max_uses') or 'inf'}")
    show("Integrations", inv["integrations"], lambda i: f"{i.get('name')} [{i.get('type')}]"
         + (" (bot)" if (i.get("application") or {}).get("bot") else ""))
    show("AutoMod rules", inv["automod"], lambda a: f"{a['name']} [{TRIGGER_NAMES.get(a['trigger_type'], a['trigger_type'])}]"
         f" {'enabled' if a['enabled'] else 'disabled'}")
    ob = inv["onboarding"]
    if isinstance(ob, dict) and "_error" not in ob:
        print(f"\nOnboarding: {'enabled' if ob.get('enabled') else 'off'}, {len(ob.get('prompts', []))} prompts")
    if inv["members"] is None:
        print("\nMembers: can't list (Server Members intent off); other bots found via roles/integrations only")
    else:
        bots = [m for m in inv["members"] if m["user"].get("bot") and m["user"]["id"] != bot_id]
        print(f"\nMembers listed: {len(inv['members'])}; other bots: "
              + (", ".join(m["user"]["username"] for m in bots) or "none"))


# ---------------------------------------------------------------------------
# Build steps
# ---------------------------------------------------------------------------


class Builder:
    def __init__(self, api, inv):
        self.api = api
        self.inv = inv
        self.plan = Plan(api)
        self.gid = inv["gid"]
        self.guild = inv["guild"]
        self.bot_id = inv["me"]["id"]
        self.roles = {r["id"]: r for r in inv["roles"]}
        self.channels = {c["id"]: c for c in inv["channels"]}
        self.role_ids = {}     # layout name -> id (real or fake)
        self.chan_ids = {}     # layout name -> id (real or fake)
        self.cat_ids = {}
        self.kept_channels = set()
        self.names = {self.gid: "@everyone"}
        for r in inv["roles"]:
            self.names[r["id"]] = r["name"]
        self.bot_top = max((self.roles[r]["position"] for r in inv["bot_member"]["roles"]
                            if r in self.roles), default=0)
        self.state = load_state()

    # -- helpers -----------------------------------------------------------

    def below_bot(self, role):
        return role["position"] < self.bot_top

    def role_id(self, name):
        return self.gid if name == "@everyone" else self.role_ids.get(name)

    def overwrites(self, ow):
        out = []
        for name, (allow, deny) in ow.items():
            rid = self.role_id(name)
            if rid:
                out.append({"id": rid, "type": 0, "allow": str(allow), "deny": str(deny)})
        return out

    @staticmethod
    def ow_map(lst):
        return {o["id"]: (int(o["allow"]), int(o["deny"])) for o in lst or []}

    def ow_desc(self, m):
        parts = []
        for i, (a, d) in sorted(m.items(), key=lambda kv: self.names.get(kv[0], kv[0])):
            s = self.names.get(i, i)
            if a:
                s += " +" + "/".join(perm_names(a))
            if d:
                s += " -" + "/".join(perm_names(d))
            parts.append(s)
        return "; ".join(parts) or "none"

    # -- server basics -----------------------------------------------------

    def server_basics(self):
        g, want, body, what = self.guild, SERVER, {}, []
        for key in ("name", "verification_level", "explicit_content_filter",
                    "default_message_notifications", "preferred_locale"):
            if g.get(key) != want[key]:
                body[key] = want[key]
                what.append(f"{key}: {g.get(key)!r} -> {want[key]!r}")
        sha, data = image_data(want["icon"])
        if not g.get("icon") or self.state.get("icon") != {"sha": sha, "hash": g.get("icon")}:
            body["icon"] = data
            what.append("icon: set to brand/out/icon-512.png")
        if body:
            res = self.plan.act("Server", "Update server: " + "; ".join(what), "PATCH",
                                f"/guilds/{self.gid}", body)
            if res and not res.get("_fake") and "icon" in body:
                self.state["icon"] = {"sha": sha, "hash": res.get("icon")}

    def everyone(self):
        r = self.roles[self.gid]
        cur = int(r["permissions"])
        if cur != EVERYONE_PERMS:
            add, rem = EVERYONE_PERMS & ~cur, cur & ~EVERYONE_PERMS
            self.plan.act("Roles", f"@everyone permissions: add {', '.join(perm_names(add)) if add else '-'}; "
                          f"remove {', '.join(perm_names(rem)) if rem else '-'}",
                          "PATCH", f"/guilds/{self.gid}/roles/{self.gid}",
                          {"permissions": str(EVERYONE_PERMS)})

    # -- roles --------------------------------------------------------------

    def roles_step(self):
        existing = sorted(self.inv["roles"], key=lambda r: (-r["position"], r["id"]))
        used = set()
        for want in LAYOUT_ROLES:
            cands = [r for r in existing if not r["managed"] and r["id"] != self.gid
                     and norm(r["name"]) == norm(want["name"]) and r["id"] not in used]
            # Prefer one the bot can manage.
            cands.sort(key=lambda r: (not self.below_bot(r), -r["position"]))
            body = {"name": want["name"], "color": want["color"], "hoist": want["hoist"],
                    "mentionable": want["mentionable"], "permissions": str(want["permissions"])}
            if not cands:
                res = self.plan.act("Roles", f"Create role {want['name']} ({hexcol(want['color'])}, "
                                    f"shown separately={want['hoist']}, mentionable={want['mentionable']}, "
                                    f"perms: {', '.join(perm_names(want['permissions']))})",
                                    "POST", f"/guilds/{self.gid}/roles", body)
                if res:
                    self.role_ids[want["name"]] = res["id"]
                    self.names[res["id"]] = want["name"]
                continue
            r = cands[0]
            used.add(r["id"])
            self.role_ids[want["name"]] = r["id"]
            diffs = []
            if r["name"] != want["name"]:
                diffs.append(f"name {r['name']!r} -> {want['name']!r}")
            if r["color"] != want["color"]:
                diffs.append(f"colour {hexcol(r['color'])} -> {hexcol(want['color'])}")
            if r["hoist"] != want["hoist"]:
                diffs.append(f"shown separately {r['hoist']} -> {want['hoist']}")
            if r["mentionable"] != want["mentionable"]:
                diffs.append(f"mentionable {r['mentionable']} -> {want['mentionable']}")
            cur = int(r["permissions"])
            if cur != want["permissions"]:
                add, rem = want["permissions"] & ~cur, cur & ~want["permissions"]
                diffs.append("perms" + (f" +{','.join(perm_names(add))}" if add else "")
                             + (f" -{','.join(perm_names(rem))}" if rem else ""))
            held = self.inv.get("role_counts", {}).get(r["id"])
            if held:
                self.plan.notes.append(f"Existing role {r['name']} is reused (matched by name) and "
                                       f"{held} member(s) already hold it; they keep it.")
            if not diffs:
                continue
            if not self.below_bot(r):
                self.plan.cannot.append(f"Role {r['name']} is above the bot's role, so it can't be "
                                        f"updated ({'; '.join(diffs)}). Move the bot's role to the top.")
                continue
            self.plan.act("Roles", f"Update role {r['name']}: {'; '.join(diffs)}", "PATCH",
                          f"/guilds/{self.gid}/roles/{r['id']}", body)
        self._used_roles = used

    def role_deletions(self):
        for r in sorted(self.inv["roles"], key=lambda r: -r["position"]):
            if r["id"] == self.gid or r["managed"] or r["id"] in self._used_roles:
                continue
            if not self.below_bot(r):
                self.plan.cannot.append(f"Role {r['name']} isn't in the layout but is above the bot's "
                                        f"role, so it can't be deleted.")
                continue
            self.plan.act("Delete", f"Delete role {r['name']}", "DELETE",
                          f"/guilds/{self.gid}/roles/{r['id']}")

    def role_order(self, roles_now):
        """Order the roles the bot can move. roles_now: current role objects (refetched)."""
        bot_id = self.bot_id
        # Positions shift after deletions: recompute the bot's top from the current roles.
        mine = set(self.inv["bot_member"]["roles"])
        bot_top = max((r["position"] for r in roles_now if r["id"] in mine), default=0)
        movable = [r for r in roles_now if r["id"] != self.gid and r["id"] not in mine
                   and r["position"] < bot_top]
        others = [r for r in roles_now if r["managed"] and r["id"] != self.gid
                  and "premium_subscriber" not in (r.get("tags") or {})
                  and (r.get("tags") or {}).get("bot_id") != bot_id]
        booster = [r for r in roles_now if "premium_subscriber" in (r.get("tags") or {})]
        desired = []  # top to bottom, ids
        for item in ROLES:
            if item == BOOSTER:
                desired += [r["id"] for r in booster]
            elif item == OTHER_BOTS:
                desired += [r["id"] for r in sorted(others, key=lambda r: -r["position"])]
            else:
                rid = self.role_ids.get(item["name"])
                if rid:
                    desired.append(rid)
        movable_ids = {r["id"] for r in movable}
        blocked = [self.names.get(i, i) for i in desired if i not in movable_ids
                   and not str(i).startswith("new")]
        if blocked:
            self.plan.cannot.append("Can't move these roles (at or above the bot's role): "
                                    + ", ".join(blocked))
        want = [i for i in desired if i in movable_ids or str(i).startswith("new")]
        current = [r["id"] for r in sorted(movable, key=lambda r: (-r["position"], r["id"]))
                   if r["id"] in set(want)]
        # Anything movable that isn't in the layout sits below (it is about to be deleted).
        if current == want and not any(str(i).startswith("new") for i in want):
            return
        names = " > ".join(self.names.get(i, i) for i in want)
        body = [{"id": i, "position": len(want) - k} for k, i in enumerate(want)]
        self.plan.act("Order", f"Order roles (top to bottom, below the bot's role): {names}",
                      "PATCH", f"/guilds/{self.gid}/roles", body)

    # -- channels ------------------------------------------------------------

    def match_channel(self, want, parent_id, used):
        name, type_ = want["name"], want["type"]
        names = {norm(want["name"]), norm(want["display"])}
        textlike = {0, 5, 15}
        group = textlike if type_ in textlike else ({2, 13} if type_ == 2 else {4})
        cands = [c for c in self.inv["channels"] if c["type"] in group and c["id"] not in used
                 and norm(c["name"]) in names]
        ok = [c for c in cands if c["type"] == type_ or {c["type"], type_} == {0, 5}]
        ok.sort(key=lambda c: (norm(c["name"]) != norm(want["display"]),
                               c.get("parent_id") != parent_id, c["position"], c["id"]))
        return ok[0] if ok else None

    def channel_body(self, want, parent_id, ow, existing=None):
        b = {"name": want["display"], "type": want["type"], "permission_overwrites": self.overwrites(ow)}
        if parent_id is not None:
            b["parent_id"] = parent_id
        if want["type"] in (0, 5, 15):
            b["topic"] = want["topic"] or ""
        if want["type"] in (0, 15):
            b["rate_limit_per_user"] = want["slowmode"]
        if want["type"] == 2:
            b["user_limit"] = want["user_limit"]
        if want["type"] == 15:
            old = {norm(t["name"]): t for t in (existing or {}).get("available_tags", [])}
            tags = []
            for name, moderated in want["tags"]:
                t = {"name": name, "moderated": moderated}
                if norm(name) in old:
                    t["id"] = old[norm(name)]["id"]
                tags.append(t)
            b["available_tags"] = tags
        return b

    def channel_diff(self, c, body):
        d = []
        if c["name"] != body["name"]:
            d.append(f"rename {c['name']!r} -> {body['name']!r}")
        if c["type"] != body["type"]:
            d.append(f"type {TYPE_NAMES[c['type']]} -> {TYPE_NAMES[body['type']]}")
        if "parent_id" in body and c.get("parent_id") != body["parent_id"]:
            old = self.channels.get(c.get("parent_id"), {}).get("name", "no category")
            d.append(f"move from {old}")
        if "topic" in body and (c.get("topic") or "") != body["topic"]:
            d.append("topic")
        if "rate_limit_per_user" in body and (c.get("rate_limit_per_user") or 0) != body["rate_limit_per_user"]:
            d.append(f"slowmode {c.get('rate_limit_per_user') or 0}s -> {body['rate_limit_per_user']}s")
        if "user_limit" in body and (c.get("user_limit") or 0) != body["user_limit"]:
            d.append(f"user limit {c.get('user_limit') or 0} -> {body['user_limit']}")
        if "available_tags" in body:
            cur = sorted((t["name"], bool(t.get("moderated"))) for t in c.get("available_tags", []))
            new = sorted((t["name"], t["moderated"]) for t in body["available_tags"])
            if cur != new:
                d.append("tags")
        cur_ow, new_ow = self.ow_map(c.get("permission_overwrites")), self.ow_map(body["permission_overwrites"])
        if cur_ow != new_ow:
            d.append(f"permissions [{self.ow_desc(cur_ow)}] -> [{self.ow_desc(new_ow)}]")
        return d

    def ensure_channel(self, want, parent_id, ow, used, label):
        c = self.match_channel(want, parent_id, used)
        body = self.channel_body(want, parent_id, ow, c)
        kind = TYPE_NAMES[want["type"]]
        if c is None:
            extra = []
            if want["slowmode"]:
                extra.append(f"slowmode {want['slowmode']}s")
            if want["user_limit"]:
                extra.append(f"limit {want['user_limit']}")
            if want["tags"]:
                extra.append("tags " + ", ".join(t for t, _ in want["tags"]))
            body_ow = self.ow_map(body["permission_overwrites"])
            if body_ow:
                extra.append("perms " + self.ow_desc(body_ow))
            res = self.plan.act("Channels", f"Create {kind} {label}" + (f" ({'; '.join(extra)})" if extra else ""),
                                "POST", f"/guilds/{self.gid}/channels", body)
            return res["id"] if res else None
        used.add(c["id"])
        self.kept_channels.add(c["id"])
        diffs = self.channel_diff(c, body)
        if diffs:
            if c["type"] != want["type"]:
                pass  # conversion text <-> announcement is part of the PATCH
            self.plan.act("Channels", f"Update {kind} {label} (keeps its "
                          f"{self.inv['counts'].get(c['id'], 0)} old msgs): {'; '.join(diffs)}",
                          "PATCH", f"/channels/{c['id']}", body)
        return c["id"]

    def channels_step(self, community_types):
        """Create/update categories and the channels whose type is in community_types' complement
        (stage 1) or in community_types (stage 2)."""
        used = self._used_chans
        for cat in CATEGORIES:
            if cat["name"] not in self.cat_ids:
                want = ch(cat["name"], CATEGORY)
                self.cat_ids[cat["name"]] = self.ensure_channel(want, None, cat["ow"], used, want["display"])
            pid = self.cat_ids[cat["name"]]
            for want in cat["channels"]:
                needs_community = want["type"] in (NEWS, FORUM)
                if needs_community != community_types:
                    continue
                ow = dict(cat["ow"])
                if want["ow"] is not None:
                    ow = dict(want["ow"])
                label = ("#" if want["type"] != VOICE else "") + want["display"] + f" in {display_name(cat['name'], CATEGORY)}"
                self.chan_ids[want["name"]] = self.ensure_channel(want, pid, ow, used, label)

    def community_step(self):
        g = self.guild
        body, what = {}, []
        rules, upd, sysc = (self.chan_ids.get(SERVER["rules_channel"]),
                            self.chan_ids.get(SERVER["public_updates_channel"]),
                            self.chan_ids.get(SERVER["system_channel"]))
        if "COMMUNITY" not in g["features"]:
            body["features"] = sorted(set(g["features"]) | {"COMMUNITY"})
            what.append("turn on Community")
        if g.get("rules_channel_id") != rules:
            body["rules_channel_id"] = rules
            what.append("rules channel #rules")
        if g.get("public_updates_channel_id") != upd:
            body["public_updates_channel_id"] = upd
            what.append("community updates channel #admin")
        if (g.get("description") or "") != SERVER["description"]:
            body["description"] = SERVER["description"]
            what.append(f"description {SERVER['description']!r}")
        # Raid/safety alerts follow the community updates channel (#admin).
        if g.get("safety_alerts_channel_id") and g.get("safety_alerts_channel_id") != upd:
            body["safety_alerts_channel_id"] = upd
            what.append("safety alerts channel #admin")
        if g.get("system_channel_id") != sysc:
            body["system_channel_id"] = sysc
            what.append("system messages to #general")
        flags = g.get("system_channel_flags", 0)
        want_flags = (flags & ~(SYS_SUPPRESS_JOIN | SYS_SUPPRESS_BOOST)) | SYS_SUPPRESS_JOIN_REPLIES
        if flags != want_flags:
            body["system_channel_flags"] = want_flags
            what.append("join + boost messages on, join sticker reply off")
        if ("rules_channel_id" in body or "public_updates_channel_id" in body) and "features" not in body:
            # Discord silently ignores community channel changes unless features is sent too.
            body["features"] = list(g["features"])
        if body:
            if "COMMUNITY" not in g["features"]:
                # Community needs these in the same request.
                body["verification_level"] = SERVER["verification_level"]
                body["explicit_content_filter"] = SERVER["explicit_content_filter"]
            self.plan.act("Community", "Update server: " + "; ".join(what), "PATCH",
                          f"/guilds/{self.gid}", body)

    def channel_deletions(self):
        doomed = [c for c in self.inv["channels"] if c["id"] not in self.kept_channels]
        # Channels first, categories last.
        doomed.sort(key=lambda c: (c["type"] == 4, c["position"]))
        hooks = self.inv["webhooks"] if isinstance(self.inv["webhooks"], list) else []
        events = self.inv["events"] if isinstance(self.inv["events"], list) else []
        for c in doomed:
            n = self.inv["counts"].get(c["id"])
            what = "category" if c["type"] == 4 else TYPE_NAMES.get(c["type"], str(c["type"]))
            extra = ""
            if c["type"] in (15, 16) and n is not None:
                extra = f", {n} posts"
            elif n is not None:
                extra = f", {n} msgs"
            hk = [w.get("name") for w in hooks if w.get("channel_id") == c["id"]]
            ev = [e["name"] for e in events if e.get("channel_id") == c["id"]]
            if hk:
                extra += f"; ALSO deletes webhooks: {', '.join(hk)}"
            if ev:
                extra += f"; affects events: {', '.join(ev)}"
            parent = self.channels.get(c.get("parent_id"), {}).get("name")
            loc = f" (in {parent})" if parent else ""
            self.plan.act("Delete", f"Delete {what} {c['name']}{loc} [{extra.lstrip(', ') or 'no msgs'}]",
                          "DELETE", f"/channels/{c['id']}")

    def channel_order(self, chans_now):
        if self.plan.dry:
            self.plan.actions.append(("Order", "Set channel/category order to the layout (only sent "
                                      "if it differs after the creates and deletes)"))
            return
        by_id = {c["id"]: c for c in chans_now}
        body, mismatch = [], False
        cats_now = sorted([c for c in chans_now if c["type"] == 4], key=lambda c: (c["position"], c["id"]))
        want_cats = [self.cat_ids[c["name"]] for c in CATEGORIES if self.cat_ids.get(c["name"]) in by_id]
        if [c["id"] for c in cats_now] != want_cats:
            mismatch = True
        for i, cid in enumerate(want_cats):
            body.append({"id": cid, "position": i})
        for cat in CATEGORIES:
            pid = self.cat_ids.get(cat["name"])
            want = [self.chan_ids[w["name"]] for w in cat["channels"] if self.chan_ids.get(w["name"]) in by_id]
            kids = [c for c in chans_now if c.get("parent_id") == pid and c["type"] != 4]
            text = [c["id"] for c in sorted(kids, key=lambda c: (c["type"] in (2, 13), c["position"], c["id"]))]
            want_sorted = sorted(want, key=lambda i: (by_id[i]["type"] in (2, 13), want.index(i)))
            if text != want_sorted:
                mismatch = True
            for i, cid in enumerate(want):
                # Parents are already right (set on create/update); Discord allows only one
                # parent_id change per request, so send positions only.
                body.append({"id": cid, "position": i})
        if mismatch:
            self.plan.act("Order", "Set channel/category order to the layout", "PATCH",
                          f"/guilds/{self.gid}/channels", body)

    # -- members -------------------------------------------------------------

    def owner_role(self):
        rid = self.role_ids.get("Owner")
        owner = self.inv["owner_member"]
        if not rid:
            return
        if isinstance(owner, dict) and rid in owner.get("roles", []):
            return
        r = self.roles.get(rid)
        if r and not self.below_bot(r):
            self.plan.cannot.append("Can't give the Owner role to the owner: it's above the bot's role.")
            return
        name = owner["user"]["username"] if isinstance(owner, dict) else self.guild["owner_id"]
        self.plan.act("Members", f"Give the Owner role to the server owner ({name})", "PUT",
                      f"/guilds/{self.gid}/members/{self.guild['owner_id']}/roles/{rid}")

    # -- posts ---------------------------------------------------------------

    def render(self, obj):
        if isinstance(obj, str):
            out = obj
            for name, cid in self.chan_ids.items():
                if f"{{#{name}}}" in out:
                    out = out.replace(f"{{#{name}}}", f"<#{cid}>" if cid and not str(cid).startswith("new") else f"#{display_name(name, TEXT)}")
            return out
        if isinstance(obj, list):
            return [self.render(x) for x in obj]
        if isinstance(obj, dict):
            return {k: self.render(v) for k, v in obj.items()}
        return obj

    @staticmethod
    def norm_msg(m):
        embeds = [{"title": e.get("title") or "", "description": (e.get("description") or "").strip(),
                   "color": e.get("color"),
                   "fields": [(f["name"], f["value"], bool(f.get("inline"))) for f in e.get("fields", [])],
                   "footer": (e.get("footer") or {}).get("text", ""),
                   "footer_icon": (e.get("footer") or {}).get("icon_url"),
                   "thumbnail": (e.get("thumbnail") or {}).get("url"),
                   "image": (e.get("image") or {}).get("url")} for e in m.get("embeds", [])]
        def emoji(c):
            return ((c.get("emoji") or {}).get("name") or "").replace("\ufe0f", "")
        comps = [[(c.get("style"), c.get("label"), c.get("url"), emoji(c)) for c in r.get("components", [])]
                 for r in m.get("components", [])]
        return {"content": m.get("content") or "", "embeds": embeds, "components": comps}

    def posts(self):
        for name, msg in POSTS.items():
            cid = self.chan_ids.get(name)
            if not cid:
                continue
            body = self.render(msg)
            body["content"] = ""
            body["allowed_mentions"] = {"parse": []}
            if str(cid).startswith("new"):
                self.plan.act("Posts", f"Post in #{name} (new channel): {len(body['embeds'])} embed(s), "
                              f"buttons: {', '.join(b['label'] for r in body['components'] for b in r['components'])}",
                              "POST", f"/channels/{cid}/messages", body)
                continue
            msgs = self.api.get(f"/channels/{cid}/messages", {"limit": 50}, ok_errors=(403, 404))
            mine = [] if isinstance(msgs, ApiError) else [m for m in msgs if m["author"]["id"] == self.bot_id]
            buttons = ", ".join(b["label"] for r in body["components"] for b in r["components"])
            if not mine:
                self.plan.act("Posts", f"Post in #{name}: {len(body['embeds'])} embed(s), buttons: {buttons}",
                              "POST", f"/channels/{cid}/messages", body)
                continue
            old = mine[-1]  # the bot's first post there
            if self.norm_msg(old) != self.norm_msg(body):
                self.plan.act("Posts", f"Edit the bot's post in #{name}", "PATCH",
                              f"/channels/{cid}/messages/{old['id']}", body)
            if len(mine) > 1:
                self.plan.notes.append(f"#{name} has {len(mine)} bot posts; only the first is kept up to date.")

    # -- automod -------------------------------------------------------------

    @staticmethod
    def norm_rule(r):
        md = r.get("trigger_metadata") or {}
        keys = {1: ("keyword_filter", "regex_patterns", "allow_list"), 3: (),
                4: ("presets", "allow_list"), 5: ("mention_total_limit", "mention_raid_protection_enabled")}
        scalars = {"mention_total_limit": None, "mention_raid_protection_enabled": False}
        tm = {}
        for k in keys.get(r["trigger_type"], ()):
            tm[k] = md.get(k, scalars[k]) if k in scalars else sorted(md.get(k) or [])
        acts = []
        for a in r.get("actions", []):
            m = a.get("metadata") or {}
            if a["type"] == 2:
                acts.append((2, str(m.get("channel_id"))))
            elif a["type"] == 3:
                acts.append((3, m.get("duration_seconds")))
            else:
                acts.append((a["type"], None))
        return {"name": r["name"], "event_type": r.get("event_type", 1), "trigger_type": r["trigger_type"],
                "trigger_metadata": tm, "actions": sorted(acts, key=str),
                "enabled": r.get("enabled", True), "exempt_roles": sorted(r.get("exempt_roles") or []),
                "exempt_channels": sorted(r.get("exempt_channels") or [])}

    def automod(self):
        existing = self.inv["automod"] if isinstance(self.inv["automod"], list) else []
        alert = self.chan_ids.get("bot-log")
        used = set()
        for want in automod_rules(alert, self.role_id):
            want = dict(want, event_type=1, enabled=True, exempt_channels=[])
            want["exempt_roles"] = [r for r in want["exempt_roles"] if r]
            if want["trigger_type"] in SINGLETON_TRIGGERS:
                cur = next((r for r in existing if r["trigger_type"] == want["trigger_type"]), None)
            else:
                cur = next((r for r in existing if r["trigger_type"] == want["trigger_type"]
                            and norm(r["name"]) == norm(want["name"])), None)
            what = {3: "block spam", 4: "block slurs + sexual content (Discord preset lists)",
                    5: "block >5 mentions + 10 min timeout", 1: "block invite links, staff exempt"}[want["trigger_type"]]
            if cur is None:
                self.plan.act("AutoMod", f"Create rule '{want['name']}': {what}, alert in #bot-log",
                              "POST", f"/guilds/{self.gid}/auto-moderation/rules", want)
                continue
            used.add(cur["id"])
            # Discord's settings page exempts the alert channel itself when the owner edits a rule there.
            if set(cur.get("exempt_channels") or []) <= {alert}:
                want["exempt_channels"] = list(cur.get("exempt_channels") or [])
            if cur.get("creator_id") == DISCORD_AUTOMOD_USER:
                if self.norm_rule(dict(cur, name=want["name"])) != self.norm_rule(want):
                    self.plan.cannot.append(f"AutoMod rule '{cur['name']}' belongs to Discord: bots can't "
                                            f"edit (404) or delete it. Owner sets it by hand.")
                    self.plan.owner_todo.append(
                        f"Server Settings > AutoMod > '{cur['name']}': limit 5 mentions, keep raid "
                        f"protection on, actions: block message, time out 10 minutes, send alert to #bot-log.")
                continue
            if self.norm_rule(cur) != self.norm_rule(want):
                body = {k: v for k, v in want.items() if k != "trigger_type"}
                self.plan.act("AutoMod", f"Update rule '{cur['name']}' -> '{want['name']}': {what}, alert in #bot-log",
                              "PATCH", f"/guilds/{self.gid}/auto-moderation/rules/{cur['id']}", body)
        for r in existing:
            if r["id"] in used:
                continue
            if any(norm(r["name"]) == norm(n) for n in AUTOMOD_DELETE):
                self.plan.act("AutoMod", f"Delete old rule '{r['name']}'", "DELETE",
                              f"/guilds/{self.gid}/auto-moderation/rules/{r['id']}")
            else:
                self.plan.notes.append(f"Existing AutoMod rule '{r['name']}' isn't in the layout; left as is "
                                       f"(delete it by hand if unwanted).")

    # -- banner ----------------------------------------------------------------

    def banner(self):
        g = self.guild
        feats = set(g["features"])
        sha, data = image_data(SERVER["banner"])
        for key, feat, level in [("banner", "BANNER", 2), ("splash", "INVITE_SPLASH", 1)]:
            if feat not in feats:
                self.plan.cannot.append(f"Server {key}: needs boost level {level} (server is level "
                                        f"{g['premium_tier']}); skipped.")
                continue
            if g.get(key) and self.state.get(key) == {"sha": sha, "hash": g.get(key)}:
                continue
            res = self.plan.act("Server", f"Set server {key} to brand/out/discord-banner-960x540.png",
                                "PATCH", f"/guilds/{self.gid}", {key: data})
            if res and not res.get("_fake"):
                self.state[key] = {"sha": sha, "hash": res.get(key)}

    def avatar(self):
        """The bot's own avatar: brand icon, only if it doesn't already match."""
        sha, data = image_data(SERVER["icon"])
        cur = self.inv["me"].get("avatar")
        if cur and self.state.get("avatar") == {"sha": sha, "hash": cur}:
            return
        res = self.plan.act("Server", "Set the bot's avatar to brand/out/icon-512.png", "PATCH",
                            "/users/@me", {"avatar": data})
        if res and not res.get("_fake"):
            self.state["avatar"] = {"sha": sha, "hash": res.get("avatar")}

    # -- run -------------------------------------------------------------------

    def run(self):
        g = self.guild
        self._used_chans = set()
        self.server_basics()
        self.everyone()
        self.roles_step()
        self.channels_step(community_types=False)
        self.community_step()
        self.channels_step(community_types=True)
        self.channel_deletions()
        self.role_deletions()
        roles_now = self.inv["roles"] if self.plan.dry else self.api.get(f"/guilds/{self.gid}/roles")
        if self.plan.dry:
            roles_now = [r for r in roles_now if r["id"] == self.gid or r["managed"] or r["id"] in self._used_roles]
        self.role_order(roles_now)
        chans_now = self.inv["channels"] if self.plan.dry else self.api.get(f"/guilds/{self.gid}/channels")
        self.channel_order(chans_now)
        self.owner_role()
        self.posts()
        self.automod()
        self.banner()
        self.avatar()
        if not self.plan.dry:
            save_state(self.state)
        # Owner to-dos that the API can't do.
        bot_role = next((r for r in self.inv["roles"] if (r.get("tags") or {}).get("bot_id") == self.bot_id), None)
        top = max(r["position"] for r in self.inv["roles"])
        if bot_role:
            todo = []
            if self.bot_top != top:
                todo.append("drag it to the top of the role list")
            if bot_role["color"] != 0xFF9933:
                todo.append("set its colour to #ff9933")
            if not bot_role["hoist"]:
                todo.append("turn on 'Display role members separately'")
            if todo:
                self.plan.owner_todo.append(f"Bot role '{bot_role['name']}': " + ", ".join(todo)
                                            + " (a bot can't edit its own role).")
        if not g["mfa_level"]:
            self.plan.owner_todo.append("Turn on 'Require 2FA for moderator actions' (Server Settings > "
                                        "Safety Setup); only the owner can.")
        return self.plan


# ---------------------------------------------------------------------------


def print_plan(plan):
    print("\n" + "=" * 72)
    print("PLAN" if plan.dry else "DONE")
    print("=" * 72)
    groups = ["Server", "Roles", "Channels", "Community", "Delete", "Order", "Members", "Posts", "AutoMod"]
    titles = {"Delete": "Deletions", "Order": "Ordering"}
    if not plan.actions:
        print("No changes: the server already matches the layout.")
    for gname in groups:
        items = [t for g, t in plan.actions if g == gname]
        if items:
            print(f"\n{titles.get(gname, gname)} ({len(items)}):")
            for t in items:
                print(f"  - {t}")
    for title, items in [("Can't do (and why)", plan.cannot), ("Failed", [f"{t}: {r}" for _, t, r in plan.failed]),
                         ("Notes", plan.notes), ("Owner to-do", plan.owner_todo)]:
        if items:
            print(f"\n{title}:")
            for t in items:
                print(f"  - {t}")
    print(f"\n{len(plan.actions)} change(s){' planned' if plan.dry else ''}, {len(plan.failed)} failed.")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = ap.add_mutually_exclusive_group(required=True)
    mode.add_argument("--dry-run", action="store_true", help="read only: print the inventory and the plan")
    mode.add_argument("--apply", action="store_true", help="make the changes")
    args = ap.parse_args()
    api = Discord(read_token(), dry_run=args.dry_run)
    try:
        inv = inventory(api)
        print_inventory(inv)
        plan = Builder(api, inv).run()
    except ApiError as e:
        sys.exit(f"Discord API error: {e}")
    print_plan(plan)
    sys.exit(1 if plan.failed else 0)


if __name__ == "__main__":
    main()
