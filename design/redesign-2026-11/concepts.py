"""Five whole-app redesign concepts for Duet, 4 screens each (Home, Search, Library, Now Playing).

Usage: python3 concepts.py OUT_DIR   ->  OUT_DIR/<n>-<concept>/<screen>.html  (390x844 each)
Covers are gradients and names are invented: nothing is copied from another app.
"""
import os, sys

OUT = sys.argv[1]

I = {
    "home": '<path d="M4 11 12 4l8 7v9h-5v-6H9v6H4z"/>',
    "search": '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
    "lib": '<path d="M4 4v16M9 4v16M14 5l5 14"/>',
    "play": '<path d="M8 5.5v13l10.5-6.5z" fill="currentColor"/>',
    "pause": '<path d="M7 5h3v14H7zM14 5h3v14h-3z" fill="currentColor" stroke="none"/>',
    "next": '<path d="M6 5.5v13l9-6.5zM17 5v14" fill="currentColor"/>',
    "prev": '<path d="M18 5.5v13l-9-6.5zM7 5v14" fill="currentColor"/>',
    "shuffle": '<path d="M3 6h3.5a5 5 0 0 1 4 2l3 4a5 5 0 0 0 4 2H21M18 11l3 3-3 3M3 18h3.5a5 5 0 0 0 3-1M14 7a5 5 0 0 1 3-1h4M18 3l3 3-3 3"/>',
    "repeat": '<path d="M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3"/>',
    "queue": '<path d="M3 6h12M3 11h12M3 16h7M18 14v6M18 14l3-1"/>',
    "lyrics": '<path d="M4 5h16v11H9l-5 4z"/><path d="M8 9h8M8 12h5"/>',
    "down": '<path d="M12 4v11M7 10l5 5 5-5M5 20h14"/>',
    "heart": '<path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"/>',
    "heartf": '<path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z" fill="currentColor"/>',
    "more": '<circle cx="5" cy="12" r="1.4" fill="currentColor"/><circle cx="12" cy="12" r="1.4" fill="currentColor"/><circle cx="19" cy="12" r="1.4" fill="currentColor"/>',
    "chevd": '<path d="m6 9 6 6 6-6"/>',
    "chev": '<path d="m9 5 7 7-7 7"/>',
    "arrow": '<path d="M5 12h14M13 6l6 6-6 6"/>',
    "bt": '<path d="m7 7 10 10-5 4V3l5 4L7 17"/>',
    "mic": '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/>',
    "x": '<path d="M6 6l12 12M18 6 6 18"/>',
    "sort": '<path d="M7 4v16M3 16l4 4 4-4M17 20V4M13 8l4-4 4 4"/>',
    "plus": '<path d="M12 5v14M5 12h14"/>',
    "radio": '<path d="M4 9h16v11H4zM8 4l9 5"/><circle cx="9" cy="14.5" r="2"/><path d="M14 13h3M14 16h3"/>',
    "gear": '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1"/>',
}


def ic(name, size=24, sw=2, color="currentColor"):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="{color}" stroke-width="{sw}" '
            f'stroke-linecap="round" stroke-linejoin="round" style="flex:none">{I[name]}</svg>')


ART = [
    "linear-gradient(135deg,#ff8a5b,#c2185b 60%,#3a0d2e)",
    "linear-gradient(160deg,#c8f26b,#2f8f5b 55%,#0c2a23)",
    "linear-gradient(135deg,#8f7bff,#3b2a8f 60%,#120b33)",
    "linear-gradient(200deg,#ffd36b,#ff6b6b 50%,#4a1030)",
    "linear-gradient(135deg,#5ee0d0,#1d6e8f 55%,#0b1d33)",
    "linear-gradient(120deg,#f1f2f7,#9aa0b4 50%,#2c3040)",
    "radial-gradient(circle at 30% 30%,#ffb84d,#a33b12 55%,#2a0c05)",
    "linear-gradient(145deg,#ff6bd5,#6b3bff 60%,#14093a)",
    "radial-gradient(circle at 70% 20%,#9ef0ff,#3060ff 50%,#0a0f33)",
]


def art(i, w, h=None, r=0, extra=""):
    return f'<div style="flex:none;width:{w}px;height:{h or w}px;border-radius:{r}px;background:{ART[i % len(ART)]};{extra}"></div>'


SONGS = [("Teeth", "Marlowe Finch", "yt"), ("Glasshouse", "Vela", "yt"), ("Night Bus", "Kestrel", "sp"),
         ("Saltwater", "Low Tide Choir", "yt"), ("Orchard Lights", "Neon Orchard", "sp"), ("Sundial", "Sundial Kids", "sp"),
         ("Paper Moons", "Vela", "yt"), ("Hollow Bell", "Marlowe Finch", "sp")]
SP, YT = "#3ecf7a", "#ff6b6b"
SRC = {"sp": (SP, "Spotify"), "yt": (YT, "YT Music")}
GENRES = ["Pop", "Hip-hop", "Chill", "Rock", "Electronic", "Indie", "Focus", "Workout"]

BASE = """*{box-sizing:border-box;margin:0;padding:0}html,body{width:390px;height:844px;overflow:hidden}
.scr{position:relative;width:390px;height:844px;overflow:hidden}.row{display:flex;align-items:center}.col{display:flex;flex-direction:column}
.ell{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"""


def status(color="#fff"):
    return (f'<div class="row" style="height:44px;justify-content:space-between;padding:0 26px;font:600 15px system-ui;color:{color};position:relative;z-index:30">'
            f'<span>9:41</span><span class="row" style="gap:6px">{ic("bt", 14, 2.2, color)}<span style="width:24px;height:12px;border:1.5px solid {color};border-radius:4px;padding:1.5px;display:inline-block">'
            f'<span style="display:block;width:70%;height:100%;background:{color};border-radius:1px"></span></span></span></div>')


def page(folder, name, fonts, css, body, bg):
    d = os.path.join(OUT, folder)
    os.makedirs(d, exist_ok=True)
    link = f'<link rel="stylesheet" href="https://fonts.googleapis.com/css2?{fonts}&display=swap">'
    html = f'<!doctype html><html><head><meta charset="utf-8">{link}<style>{BASE}{css}</style></head><body style="background:{bg}"><div class="scr" style="background:{bg}">{body}</div></body></html>'
    open(os.path.join(d, name + ".html"), "w").write(html)


# =====================================================================================
# 1. AURORA: frosted glass over a moving colour mesh; one floating dock that is also the player.
# =====================================================================================
F1 = "family=Outfit:wght@300;400;500;600;700;800"
C1 = """body,.scr{font-family:Outfit,sans-serif;color:#fff}
.mesh{position:absolute;inset:0;background:radial-gradient(60% 40% at 15% 10%,#7b5cff 0%,transparent 70%),radial-gradient(50% 40% at 90% 30%,#2de2c3 0%,transparent 70%),radial-gradient(60% 50% at 30% 90%,#ff7ab6 0%,transparent 70%),#0b0a1a;filter:saturate(1.2)}
.glass{background:rgba(255,255,255,.09);backdrop-filter:blur(24px);-webkit-backdrop-filter:blur(24px);border:1px solid rgba(255,255,255,.16)}
.dock{position:absolute;left:50%;transform:translateX(-50%);bottom:26px;width:300px;height:72px;border-radius:36px;display:flex;align-items:center;justify-content:space-around;padding:0 14px;z-index:20;box-shadow:0 20px 40px rgba(0,0,0,.35)}
.dock .ico{width:46px;height:46px;border-radius:23px;display:flex;align-items:center;justify-content:center;color:rgba(255,255,255,.6)}
.dock .ico.on{background:rgba(255,255,255,.18);color:#fff}
.orb{width:58px;height:58px;border-radius:29px;position:relative;margin-top:-28px;box-shadow:0 10px 30px rgba(123,92,255,.6)}
.pill{height:36px;padding:0 16px;border-radius:18px;display:inline-flex;align-items:center;gap:8px;font-weight:500;font-size:14px}
.h{font-weight:700;letter-spacing:-.5px}"""


def dock1(on):
    ring = ('<svg width="66" height="66" viewBox="0 0 66 66" style="position:absolute;left:-4px;top:-4px"><circle cx="33" cy="33" r="31" fill="none" stroke="rgba(255,255,255,.2)" stroke-width="3"/>'
            '<circle cx="33" cy="33" r="31" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round" stroke-dasharray="75 200" transform="rotate(-90 33 33)"/></svg>')
    orb = f'<div class="orb">{art(0, 58, r=29)}{ring}<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center">{ic("pause", 20)}</div></div>'
    items = [("home", "Home"), ("search", "Search"), None, ("lib", "Library"), ("radio", "Radio")]
    return '<div class="dock glass">' + "".join(orb if it is None else f'<div class="ico {"on" if it[1] == on else ""}">{ic(it[0], 22)}</div>' for it in items) + "</div>"


def c1_home():
    moods = "".join(f'<span class="pill glass">{m}</span>' for m in ["Calm", "Energy", "Focus", "Late night"])
    recent = "".join(f'<div style="position:relative;width:150px;height:190px;border-radius:26px;overflow:hidden;flex:none">{art(i + 2, 150, 190)}'
                     f'<div class="glass" style="position:absolute;left:8px;right:8px;bottom:8px;border-radius:18px;padding:10px 12px"><div class="ell" style="font-weight:600">{t}</div><div class="ell" style="font-size:12px;opacity:.75">{a}</div></div></div>'
                     for i, (t, a, s) in enumerate(SONGS[1:4]))
    page("1-aurora", "1-home", F1, C1, f"""<div class="mesh"></div>{status()}
<div class="row" style="padding:8px 22px 0;justify-content:space-between;position:relative"><div class="col"><span style="opacity:.7;font-size:15px">Tuesday night</span><span class="h" style="font-size:34px">Hi Szymon</span></div>
<div class="glass" style="width:48px;height:48px;border-radius:24px;display:flex;align-items:center;justify-content:center;font-weight:700">S</div></div>
<div style="padding:18px 22px 0;position:relative"><div class="glass" style="border-radius:32px;padding:18px;height:220px;position:relative;overflow:hidden">
 <div style="position:absolute;right:-30px;top:-30px;transform:rotate(12deg)">{art(6, 170, r=30)}</div><div style="position:absolute;right:70px;top:60px;transform:rotate(-10deg)">{art(7, 120, r=24)}</div>
 <div class="col" style="position:absolute;left:18px;bottom:18px;gap:4px"><span class="pill glass" style="height:28px;font-size:12px;align-self:flex-start">Spotify + YT Music</span><span class="h" style="font-size:30px">Duet Mix</span><span style="opacity:.75">42 songs made for tonight</span></div>
 <div style="position:absolute;right:18px;bottom:18px;width:60px;height:60px;border-radius:30px;background:#fff;color:#0b0a1a;display:flex;align-items:center;justify-content:center">{ic("play", 26)}</div></div></div>
<div class="row" style="gap:8px;padding:16px 22px 0;position:relative">{moods}</div>
<div class="h" style="font-size:20px;padding:22px 22px 10px;position:relative">Pick up again</div>
<div class="row" style="gap:12px;padding:0 22px;position:relative">{recent}</div>{dock1("Home")}""", "#0b0a1a")


def c1_search():
    bubbles = [(GENRES[0], 130, 22, 20, 0), (GENRES[2], 100, 170, 0, 4), (GENRES[1], 116, 262, 40, 3), (GENRES[4], 150, 60, 150, 7),
               (GENRES[3], 96, 230, 170, 6), (GENRES[5], 110, 10, 300, 1), (GENRES[6], 124, 130, 320, 8), (GENRES[7], 100, 268, 290, 2)]
    b = "".join(f'<div style="position:absolute;left:{x}px;top:{y}px;width:{s}px;height:{s}px;border-radius:50%;background:{ART[c]};display:flex;align-items:center;justify-content:center;font-weight:600;font-size:{14 + s // 30}px;box-shadow:0 12px 30px rgba(0,0,0,.3),inset 0 0 0 1px rgba(255,255,255,.25)">{n}</div>' for n, s, x, y, c in bubbles)
    page("1-aurora", "2-search", F1, C1, f"""<div class="mesh"></div>{status()}
<div style="padding:24px 22px 0;position:relative;text-align:center"><div class="h" style="font-size:30px;line-height:1.1">What do you<br>feel like?</div></div>
<div style="padding:20px 22px 0;position:relative"><div class="glass row" style="height:58px;border-radius:29px;padding:0 20px;gap:12px">{ic("search", 22)}<span style="flex:1;opacity:.6;font-size:16px">Songs, artists, moods</span>{ic("mic", 22)}</div></div>
<div class="row" style="gap:8px;padding:14px 22px 0;position:relative;justify-content:center;flex-wrap:wrap"><span class="pill glass" style="height:30px;font-size:13px">marlowe finch</span><span class="pill glass" style="height:30px;font-size:13px">night drive</span><span class="pill glass" style="height:30px;font-size:13px">vela</span></div>
<div style="position:relative;height:440px;margin:22px 0 0">{b}</div>{dock1("Search")}""", "#0b0a1a")


def c1_library():
    rows = "".join(f'<div class="glass row" style="border-radius:22px;padding:10px;gap:12px;margin-bottom:8px">{art(i + 3, 48, r=14)}<div class="col" style="flex:1;min-width:0"><span class="ell" style="font-weight:600">{t}</span><span style="font-size:12px;opacity:.7">{k}</span></div>{ic("chev", 18)}</div>'
                   for i, (t, k) in enumerate([("Night Drive", "Playlist · 50 songs"), ("Paper Moons", "Album · Vela"), ("Low Tide Choir", "Artist")]))
    tile = lambda w, h, bg, ico, t, s: (f'<div class="glass" style="grid-column:span {w};grid-row:span {h};border-radius:26px;padding:16px;display:flex;flex-direction:column;justify-content:space-between;background:{bg}">'
                                        f'<div style="width:40px;height:40px;border-radius:20px;background:rgba(255,255,255,.2);display:flex;align-items:center;justify-content:center">{ic(ico, 20)}</div>'
                                        f'<div><div class="h" style="font-size:{22 if h > 1 else 16}px">{t}</div><div style="font-size:12px;opacity:.75">{s}</div></div></div>')
    page("1-aurora", "3-library", F1, C1, f"""<div class="mesh"></div>{status()}
<div class="row" style="padding:8px 22px 0;justify-content:space-between;position:relative"><span class="h" style="font-size:34px">Library</span><div class="glass" style="width:44px;height:44px;border-radius:22px;display:flex;align-items:center;justify-content:center">{ic("plus", 22)}</div></div>
<div style="display:grid;grid-template-columns:repeat(4,1fr);grid-auto-rows:84px;gap:10px;padding:16px 22px 0;position:relative">
 {tile(2, 2, "linear-gradient(150deg,rgba(255,122,182,.55),rgba(123,92,255,.35))", "heartf", "Liked", "326 songs · both services")}
 {tile(2, 1, "rgba(45,226,195,.25)", "down", "Downloaded", "48 songs · 412 MB")}
 {tile(2, 1, "rgba(255,255,255,.08)", "radio", "Cached", "112 songs")}
 {tile(4, 1, "rgba(123,92,255,.25)", "lib", "Playlists", "18 from Spotify · 9 from YT Music")}
</div>
<div style="padding:16px 22px 0;position:relative">{rows}</div>{dock1("Library")}""", "#0b0a1a")


def c1_now():
    page("1-aurora", "4-now-playing", F1, C1, f"""<div style="position:absolute;inset:-60px;background:{ART[6]};filter:blur(60px) saturate(1.4);opacity:.9"></div><div style="position:absolute;inset:0;background:rgba(11,10,26,.35)"></div>{status()}
<div class="row" style="padding:6px 18px;justify-content:space-between;position:relative"><div class="glass" style="width:44px;height:44px;border-radius:22px;display:flex;align-items:center;justify-content:center">{ic("chevd", 24)}</div><span class="pill glass" style="font-size:13px"><i style="width:7px;height:7px;border-radius:50%;background:{YT}"></i>YT Music · 256k</span><div class="glass" style="width:44px;height:44px;border-radius:22px;display:flex;align-items:center;justify-content:center">{ic("more", 22)}</div></div>
<div style="position:relative;width:300px;height:300px;margin:26px auto 0">
 <svg width="300" height="300" viewBox="0 0 300 300" style="position:absolute;inset:0"><circle cx="150" cy="150" r="144" fill="none" stroke="rgba(255,255,255,.18)" stroke-width="6"/><circle cx="150" cy="150" r="144" fill="none" stroke="#fff" stroke-width="6" stroke-linecap="round" stroke-dasharray="340 905" transform="rotate(-90 150 150)"/><circle cx="{150 + 144 * 0.94:.0f}" cy="{150 - 144 * 0.34:.0f}" r="10" fill="#fff"/></svg>
 <div style="position:absolute;inset:22px;border-radius:50%;background:{ART[6]};box-shadow:0 30px 60px rgba(0,0,0,.4)"></div><div style="position:absolute;left:130px;top:130px;width:40px;height:40px;border-radius:20px;background:rgba(11,10,26,.6);backdrop-filter:blur(6px)"></div></div>
<div style="text-align:center;margin-top:24px;position:relative"><div class="h" style="font-size:30px">Teeth</div><div style="opacity:.75;font-size:17px">Marlowe Finch</div><div style="font-size:13px;opacity:.65;margin-top:6px">1:16 / 3:20</div></div>
<div class="row" style="justify-content:center;gap:22px;margin-top:22px;position:relative">
 <div class="glass" style="width:54px;height:54px;border-radius:27px;display:flex;align-items:center;justify-content:center">{ic("shuffle", 20)}</div>
 <div class="glass" style="width:64px;height:64px;border-radius:32px;display:flex;align-items:center;justify-content:center">{ic("prev", 26)}</div>
 <div style="width:84px;height:84px;border-radius:42px;background:#fff;color:#0b0a1a;display:flex;align-items:center;justify-content:center;box-shadow:0 14px 40px rgba(255,255,255,.35)">{ic("pause", 34)}</div>
 <div class="glass" style="width:64px;height:64px;border-radius:32px;display:flex;align-items:center;justify-content:center">{ic("next", 26)}</div>
 <div class="glass" style="width:54px;height:54px;border-radius:27px;display:flex;align-items:center;justify-content:center">{ic("heartf", 20)}</div></div>
<div class="glass row" style="position:absolute;left:40px;right:40px;bottom:34px;height:56px;border-radius:28px;justify-content:space-around">{ic("lyrics", 22)}<span class="row" style="gap:6px;font-size:13px">{ic("bt", 16)}WF-1000XM3</span>{ic("queue", 22)}</div>""", "#0b0a1a")


# =====================================================================================
# 2. BOLD: neo-brutalist poster. Light paper, black borders, hard shadows, huge type, nav on top.
# =====================================================================================
F2 = "family=Archivo+Black&family=Space+Grotesk:wght@400;500;600;700"
C2 = """body,.scr{font-family:'Space Grotesk',sans-serif;color:#111}
.big{font-family:'Archivo Black',sans-serif;text-transform:uppercase;letter-spacing:-1px;line-height:.92}
.box{border:2.5px solid #111;box-shadow:4px 4px 0 #111;background:#fff}
.tag{display:inline-flex;align-items:center;height:22px;padding:0 7px;border:2px solid #111;font-size:11px;font-weight:700;letter-spacing:.5px}
.nav{display:flex;gap:6px;padding:0 18px}.nav span{flex:1;height:40px;border:2.5px solid #111;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:14px;background:#fff}
.nav span.on{background:#111;color:#ffd400}
.mini{position:absolute;left:0;right:0;bottom:0;height:86px;background:#ffd400;border-top:2.5px solid #111;display:flex;align-items:center;gap:12px;padding:0 18px 14px}"""
TAG = {"sp": ('<span class="tag" style="background:#3ecf7a">SPOTIFY</span>'), "yt": ('<span class="tag" style="background:#ff6b6b">YT MUSIC</span>')}


def head2(on):
    return (f'{status("#111")}<div class="row" style="justify-content:space-between;padding:4px 18px 12px"><span class="big" style="font-size:26px">Duet®</span>'
            f'<div class="box" style="width:40px;height:40px;display:flex;align-items:center;justify-content:center;box-shadow:3px 3px 0 #111">{ic("gear", 20)}</div></div>'
            f'<div class="nav">' + "".join(f'<span class="{"on" if n == on else ""}">{n}</span>' for n in ["Home", "Search", "Library"]) + "</div>")


def mini2():
    return (f'<div class="mini">{art(0, 52, extra="border:2.5px solid #111")}<div class="col" style="flex:1;min-width:0"><span class="ell" style="font-weight:700;font-size:16px">TEETH</span><span style="font-size:13px">Marlowe Finch · 1:16</span></div>'
            f'<div style="width:52px;height:52px;background:#111;color:#ffd400;display:flex;align-items:center;justify-content:center">{ic("pause", 24)}</div></div>')


def c2_home():
    tl = "".join(f'<div class="row" style="gap:10px;height:46px;border-top:2px solid #111"><span class="big" style="font-size:18px;width:30px">0{i + 1}</span>{art(i, 32, extra="border:2px solid #111")}<span class="ell" style="flex:1;font-weight:700">{t}</span>{TAG[s]}</div>' for i, (t, a, s) in enumerate(SONGS[:4]))
    cards = "".join(f'<div class="box" style="padding:0">{art(i + 5, 160, 118)}<div style="border-top:2.5px solid #111;padding:8px 10px;background:{c}"><div class="ell" style="font-weight:700">{t}</div><div style="font-size:12px">{a}</div></div></div>'
                    for i, ((t, a, s), c) in enumerate(zip(SONGS[4:6], ["#ff5c39", "#3d5afe"])))
    page("2-bold", "1-home", F2, C2, f"""{head2("Home")}
<div style="padding:18px 18px 0"><div class="big" style="font-size:54px">Good<br>evening.</div></div>
<div style="padding:16px 18px 0"><div class="box" style="background:#ffd400;padding:14px 14px 4px">
 <div class="row" style="justify-content:space-between;margin-bottom:8px"><span class="big" style="font-size:22px">Today's mix</span><div style="width:44px;height:44px;background:#111;color:#ffd400;display:flex;align-items:center;justify-content:center">{ic("play", 22)}</div></div>{tl}</div></div>
<div class="row" style="padding:18px 18px 8px;justify-content:space-between"><span class="big" style="font-size:20px">Jump back in</span><span style="font-weight:700;text-decoration:underline">ALL →</span></div>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:14px;padding:0 18px">{cards}</div>{mini2()}""", "#f4f1ea")


def c2_search():
    colors = ["#ffd400", "#ff5c39", "#3d5afe", "#3ecf7a", "#ff9ad5", "#fff"]
    bars = "".join(f'<div class="row" style="height:52px;border:2.5px solid #111;border-top:{"2.5px solid #111" if i == 0 else "none"};background:{c};padding:0 14px;gap:14px;color:{"#fff" if c == "#3d5afe" else "#111"}"><span class="big" style="font-size:16px;width:26px">{i + 1:02d}</span><span class="big" style="font-size:24px;flex:1">{g}</span>{ic("arrow", 26, 2.6)}</div>'
                   for i, (g, c) in enumerate(zip(GENRES, colors)))
    page("2-bold", "2-search", F2, C2, f"""{head2("Search")}
<div style="padding:18px 18px 0"><div class="box row" style="height:64px;padding:0 16px;gap:12px">{ic("search", 26, 2.6)}<span class="big" style="font-size:22px;color:#aaa;flex:1">Type to search</span></div></div>
<div class="row" style="gap:8px;padding:14px 18px 0;flex-wrap:wrap"><span class="tag" style="height:28px;background:#111;color:#fff">RECENT</span><span class="tag" style="height:28px;background:#fff">MARLOWE FINCH ×</span><span class="tag" style="height:28px;background:#fff">VELA ×</span></div>
<div style="padding:18px 18px 0"><div class="big" style="font-size:20px;margin-bottom:8px">Browse</div>{bars}</div>{mini2()}""", "#f4f1ea")


def c2_library():
    rows = "".join(f'<div class="row" style="height:50px;border-bottom:2px solid #111;gap:10px"><span style="width:22px;font-weight:700">{i + 1}</span><div class="col" style="flex:1;min-width:0"><span class="ell" style="font-weight:700">{t}</span><span style="font-size:12px">{a}</span></div>{TAG[s]}<span style="width:36px;text-align:right;font-weight:600">3:{20 + i * 7}</span></div>' for i, (t, a, s) in enumerate(SONGS[:7]))
    page("2-bold", "3-library", F2, C2, f"""{head2("Library")}
<div class="row" style="padding:18px 18px 0;align-items:flex-end;justify-content:space-between"><div class="big" style="font-size:72px">326</div><div style="font-weight:700;text-align:right;padding-bottom:6px">SONGS<br>ON 2 SERVICES</div></div>
<div class="row" style="gap:6px;padding:12px 18px 0;flex-wrap:wrap"><span class="tag" style="height:30px;background:#111;color:#ffd400">ALL</span><span class="tag" style="height:30px;background:#fff">PLAYLISTS 27</span><span class="tag" style="height:30px;background:#fff">DOWNLOADED 48</span><span class="tag" style="height:30px;background:#fff">ARTISTS</span></div>
<div style="padding:12px 18px 0"><div class="row" style="height:30px;border-bottom:2.5px solid #111;font-size:12px;font-weight:700;gap:10px"><span style="width:22px">#</span><span style="flex:1">TITLE</span><span>SOURCE</span><span style="width:36px;text-align:right">TIME</span></div>{rows}</div>{mini2()}""", "#f4f1ea")


def c2_now():
    page("2-bold", "4-now-playing", F2, C2, f"""{status("#111")}
<div class="row" style="justify-content:space-between;padding:4px 18px"><div class="box" style="width:44px;height:44px;display:flex;align-items:center;justify-content:center">{ic("chevd", 24, 2.6)}</div><span class="tag" style="height:30px;background:#fff">PLAYING FROM: DUET MIX</span></div>
<div style="position:relative;height:330px;margin-top:16px">
 <div style="position:absolute;left:18px;top:0;border:3px solid #111;box-shadow:8px 8px 0 #111">{art(6, 250)}</div>
 <div class="box" style="position:absolute;right:18px;top:200px;background:#ffd400;padding:6px 10px;transform:rotate(4deg)"><span class="big" style="font-size:16px">YT Music<br>256 kbps</span></div></div>
<div style="padding:0 18px"><div class="big" style="font-size:74px">Teeth</div><div class="row" style="justify-content:space-between;margin-top:4px"><span style="font-size:20px;font-weight:600">Marlowe Finch</span>{ic("heartf", 28)}</div></div>
<div style="padding:18px 18px 0"><div style="height:16px;border:2.5px solid #111;background:#fff;position:relative"><div style="position:absolute;left:0;top:0;bottom:0;width:38%;background:#111"></div></div>
<div class="row" style="justify-content:space-between;font-weight:700;margin-top:6px"><span>1:16</span><span>3:20</span></div></div>
<div class="row" style="gap:12px;padding:14px 18px 0">
 <div class="box" style="flex:1;height:70px;display:flex;align-items:center;justify-content:center">{ic("prev", 30)}</div>
 <div class="box" style="flex:1.4;height:70px;display:flex;align-items:center;justify-content:center;background:#111;color:#ffd400">{ic("pause", 34)}</div>
 <div class="box" style="flex:1;height:70px;display:flex;align-items:center;justify-content:center">{ic("next", 30)}</div></div>
<div class="row" style="gap:10px;padding:16px 18px 0"><span class="tag" style="height:34px;flex:1;justify-content:center;background:#fff">LYRICS</span><span class="tag" style="height:34px;flex:1;justify-content:center;background:#fff">QUEUE (12)</span><span class="tag" style="height:34px;flex:1;justify-content:center;background:#3ecf7a">SAVED ✓</span></div>""", "#ff5c39")


# =====================================================================================
# 3. SPLIT: a side rail instead of a tab bar, and the two services side by side everywhere.
# =====================================================================================
F3 = "family=Inter:wght@400;500;600;700;800"
C3 = """body,.scr{font-family:Inter,sans-serif;color:#eceef2}
.rail{position:absolute;left:0;top:0;bottom:0;width:64px;background:#08090b;border-right:1px solid #1d1f25;display:flex;flex-direction:column;align-items:center;padding-top:56px;gap:10px;z-index:10}
.rail .i{width:44px;height:44px;border-radius:12px;display:flex;align-items:center;justify-content:center;color:#6b7080}
.rail .i.on{background:#ff8a3d;color:#101114}
.main{position:absolute;left:64px;right:0;top:0;bottom:0;padding:0 16px}
.lbl{font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:#6b7080}
.card{background:#181a1f;border:1px solid #23262d;border-radius:14px}
.mini3{position:absolute;left:64px;right:0;bottom:0;height:80px;background:#181a1f;border-top:1px solid #23262d;display:flex;align-items:center;gap:10px;padding:0 14px 12px;z-index:9}"""


def rail(on):
    items = [("home", "Home"), ("search", "Search"), ("lib", "Library"), ("down", "Downloads"), ("radio", "Radio")]
    return ('<div class="rail"><div style="width:30px;height:30px;border-radius:9px;background:linear-gradient(135deg,#3ecf7a 50%,#ff6b6b 50%);margin-bottom:18px"></div>'
            + "".join(f'<div class="i {"on" if n == on else ""}">{ic(i, 22)}</div>' for i, n in items)
            + '<div style="flex:1"></div><div style="width:36px;height:36px;border-radius:18px;background:#2a2d35;display:flex;align-items:center;justify-content:center;font-weight:700;margin-bottom:30px">S</div></div>')


def mini3():
    wave = "".join(f'<i style="display:inline-block;width:3px;margin-right:2px;border-radius:2px;height:{h}px;background:{"#ff8a3d" if k < 14 else "#3a3e48"}"></i>' for k, h in enumerate([6, 12, 18, 10, 20, 14, 8, 16, 22, 12, 6, 14, 18, 10, 8, 16, 12, 20, 8, 14, 10, 6, 12, 16, 10, 8, 14, 6]))
    return (f'<div class="mini3">{art(0, 46, r=10)}<div class="col" style="flex:1;min-width:0;gap:4px"><span class="ell" style="font-weight:600;font-size:14px">Teeth · Marlowe Finch</span><div class="row" style="align-items:center;height:22px">{wave}</div></div>'
            f'<div style="width:44px;height:44px;border-radius:22px;background:#ff8a3d;color:#101114;display:flex;align-items:center;justify-content:center">{ic("pause", 20)}</div></div>')


def c3_home():
    def colm(key):
        c, n = SRC[key]
        items = [s for s in SONGS if s[2] == key][:4]
        return (f'<div class="card" style="flex:1;padding:10px;min-width:0"><div class="row" style="gap:6px;margin-bottom:8px"><i style="width:8px;height:8px;border-radius:4px;background:{c}"></i><span style="font-weight:700;font-size:13px">{n}</span></div>'
                + "".join(f'<div class="row" style="gap:8px;margin-bottom:8px">{art(SONGS.index(s), 34, r=7)}<div class="col" style="min-width:0"><span class="ell" style="font-size:12px;font-weight:600">{s[0]}</span><span class="ell" style="font-size:11px;color:#7d8292">{s[1]}</span></div></div>' for s in items) + "</div>")
    page("3-split", "1-home", F3, C3, f"""{rail("Home")}<div class="main">{status()}
<div class="lbl" style="margin-top:6px">Tue 17 Nov</div><div style="font-size:28px;font-weight:800;letter-spacing:-.6px">Good evening</div>
<div class="card" style="margin-top:14px;overflow:hidden;position:relative;height:150px;border:none;background:linear-gradient(120deg,#ff8a3d,#c2185b)">
 <div style="position:absolute;right:-20px;top:-10px;opacity:.9">{art(6, 160, r=20, extra="transform:rotate(14deg)")}</div>
 <div class="col" style="position:absolute;left:14px;bottom:14px"><span class="lbl" style="color:rgba(255,255,255,.8)">Continue</span><span style="font-size:22px;font-weight:800">Teeth</span><span style="font-size:13px;opacity:.85">Marlowe Finch · 2:04 left</span></div></div>
<div class="row" style="justify-content:space-between;margin-top:18px"><span style="font-weight:700;font-size:16px">Side by side</span><span class="lbl">New this week</span></div>
<div class="row" style="gap:8px;margin-top:8px;align-items:stretch">{colm("sp")}{colm("yt")}</div>
<div class="row" style="justify-content:space-between;margin-top:16px"><span style="font-weight:700;font-size:16px">Radio</span></div>
<div class="row" style="gap:8px;margin-top:8px">{"".join(f'<div class="col" style="gap:6px;width:96px">{art(i + 2, 96, r=12)}<span class="ell" style="font-size:12px;font-weight:600">{t} radio</span></div>' for i, (t, a, s) in enumerate(SONGS[3:6]))}</div>
</div>{mini3()}""", "#101114")


def c3_search():
    def res(key):
        c, n = SRC[key]
        items = [("Teeth", "Marlowe Finch"), ("Teeth (Live)", "Marlowe Finch"), ("Sharp Teeth", "Kestrel"), ("Milk Teeth", "Sundial Kids"), ("Teeth & Tongue", "Neon Orchard")]
        return (f'<div style="flex:1;min-width:0"><div class="row" style="gap:6px;height:30px;border-bottom:2px solid {c};margin-bottom:8px"><i style="width:8px;height:8px;border-radius:4px;background:{c}"></i><span style="font-weight:700;font-size:13px">{n}</span><span class="lbl" style="margin-left:auto">{24 if key == "sp" else 31}</span></div>'
                + "".join(f'<div class="card" style="padding:8px;margin-bottom:6px">{art(j + (0 if key == "yt" else 3), 132, 70, r=8)}<div class="ell" style="font-size:12px;font-weight:600;margin-top:6px">{t}</div><div class="ell" style="font-size:11px;color:#7d8292">{a}</div></div>' for j, (t, a) in enumerate(items[:4])) + "</div>")
    page("3-split", "2-search", F3, C3, f"""{rail("Search")}<div class="main">{status()}
<div class="row" style="height:48px;border-radius:12px;background:#181a1f;border:1px solid #ff8a3d;padding:0 14px;gap:10px;margin-top:6px">{ic("search", 20)}<span style="flex:1;font-weight:600">teeth</span>{ic("x", 18, 2.2, "#6b7080")}</div>
<div class="card row" style="margin-top:12px;padding:10px;gap:10px;background:linear-gradient(90deg,rgba(62,207,122,.15),rgba(255,107,107,.15))">{art(0, 44, r=8)}<div class="col" style="flex:1;min-width:0"><span style="font-weight:700;font-size:14px">Teeth · Marlowe Finch</span><span style="font-size:11px;color:#a3a8b5">On both · Duet plays the best copy</span></div><div style="width:38px;height:38px;border-radius:19px;background:#ff8a3d;color:#101114;display:flex;align-items:center;justify-content:center">{ic("play", 18)}</div></div>
<div class="row" style="gap:10px;margin-top:14px;align-items:flex-start">{res("sp")}{res("yt")}</div>
</div>{mini3()}""", "#101114")


def c3_library():
    def group(key, items):
        c, n = SRC[key]
        return (f'<div class="row" style="gap:8px;margin-top:16px"><i style="width:8px;height:8px;border-radius:4px;background:{c}"></i><span style="font-weight:700">{n}</span><span class="lbl" style="margin-left:auto">{len(items)} playlists {ic("chevd", 14)}</span></div>'
                + "".join(f'<div class="row" style="gap:10px;height:56px;border-bottom:1px solid #1d1f25">{art(i + (2 if key == "sp" else 5), 40, r=8)}<div class="col" style="flex:1;min-width:0"><span class="ell" style="font-weight:600;font-size:14px">{t}</span><span style="font-size:12px;color:#7d8292">{k}</span></div>{ic("more", 18, 2, "#6b7080")}</div>' for i, (t, k) in enumerate(items)))
    page("3-split", "3-library", F3, C3, f"""{rail("Library")}<div class="main">{status()}
<div class="row" style="justify-content:space-between;margin-top:6px"><span style="font-size:28px;font-weight:800;letter-spacing:-.6px">Library</span><span class="row" style="gap:4px;font-size:13px;color:#a3a8b5">{ic("sort", 16)}Recent</span></div>
<div class="card" style="margin-top:12px;padding:12px"><div class="row" style="justify-content:space-between;font-size:13px"><span style="font-weight:600">On this phone</span><span style="color:#7d8292">412 MB of 24 GB</span></div>
<div class="row" style="height:8px;border-radius:4px;overflow:hidden;background:#23262d;margin-top:8px"><div style="width:22%;height:100%;background:#ff8a3d"></div><div style="width:8%;height:100%;background:#5a5f6b"></div></div>
<div class="row" style="gap:14px;margin-top:8px;font-size:12px;color:#a3a8b5"><span>■ Downloaded 48</span><span>■ Cached 112</span></div></div>
{group("sp", [("Night Drive", "50 songs"), ("Focus Flow", "112 songs"), ("Liked songs", "214 songs")])}
{group("yt", [("Paper Moons", "Album · Vela"), ("Saltwater mix", "38 songs"), ("Liked music", "112 songs")])}
</div>{mini3()}""", "#101114")


def c3_now():
    page("3-split", "4-now-playing", F3, C3, f"""<div style="position:absolute;left:0;right:0;top:0;height:470px;background:{ART[6]}"></div><div style="position:absolute;left:0;right:0;top:250px;height:220px;background:linear-gradient(transparent,#101114)"></div>{status()}
<div class="row" style="justify-content:space-between;padding:4px 16px;position:relative"><div style="width:42px;height:42px;border-radius:12px;background:rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center">{ic("chevd", 24)}</div><span style="font-size:12px;font-weight:700;background:rgba(0,0,0,.35);padding:6px 10px;border-radius:10px">● YT MUSIC · 256k</span></div>
<div style="position:absolute;left:16px;right:16px;top:330px"><div style="font-size:34px;font-weight:800;letter-spacing:-1px">Teeth</div><div style="font-size:16px;color:#c9ccd4">Marlowe Finch · Paper Moons</div>
<div style="height:4px;border-radius:2px;background:#2a2d35;margin-top:16px;position:relative"><div style="position:absolute;left:0;top:0;bottom:0;width:38%;background:#ff8a3d;border-radius:2px"></div></div><div class="row" style="justify-content:space-between;font-size:12px;color:#7d8292;margin-top:6px"><span>1:16</span><span>3:20</span></div></div>
<div class="row" style="position:absolute;left:16px;right:16px;top:470px;gap:10px;align-items:stretch;height:300px">
 <div class="card" style="flex:1.4;padding:14px;display:flex;flex-direction:column;gap:10px"><span class="lbl">Lyrics</span><span style="color:#5d6270;font-size:15px;font-weight:600">We kept the lights on past the tide</span><span style="font-size:19px;font-weight:800;line-height:1.2">And every word you swallowed bit me back</span><span style="color:#5d6270;font-size:15px;font-weight:600">Cold coffee, open window</span></div>
 <div class="col" style="flex:1;gap:10px">
  <div class="card" style="flex:1;display:flex;align-items:center;justify-content:center">{ic("prev", 28)}</div>
  <div style="flex:1.4;border-radius:14px;background:#ff8a3d;color:#101114;display:flex;align-items:center;justify-content:center">{ic("pause", 38)}</div>
  <div class="card" style="flex:1;display:flex;align-items:center;justify-content:center">{ic("next", 28)}</div></div></div>
<div class="row" style="position:absolute;left:16px;right:16px;bottom:24px;justify-content:space-between;color:#a3a8b5">{ic("shuffle", 22)}{ic("repeat", 22)}<span class="row" style="gap:6px;font-size:13px">{ic("bt", 16)}WF-1000XM3</span>{ic("queue", 22)}{ic("heartf", 22, 2, "#ff8a3d")}</div>""", "#101114")


# =====================================================================================
# 4. DECK: a hi-fi tape deck. LCD screens, chunky physical keys, cassettes as playlists.
# =====================================================================================
F4 = "family=VT323&family=Rubik:wght@400;500;600;700;800"
C4 = """body,.scr{font-family:Rubik,sans-serif;color:#e9e6df}
.metal{background:linear-gradient(180deg,#36363a,#1c1c1f)}
.lcd{background:linear-gradient(180deg,#c3dd8f,#9fbf6a);color:#1f2a12;font-family:VT323,monospace;border-radius:10px;box-shadow:inset 0 3px 8px rgba(0,0,0,.45),0 1px 0 rgba(255,255,255,.12)}
.key{background:linear-gradient(180deg,#4a4a50,#2a2a2e);border-radius:12px;box-shadow:0 5px 0 #121214,0 7px 12px rgba(0,0,0,.5),inset 0 1px 0 rgba(255,255,255,.18);display:flex;align-items:center;justify-content:center;color:#e9e6df}
.key.or{background:linear-gradient(180deg,#ff8a3d,#e25a10);color:#1b0f05;box-shadow:0 5px 0 #8f3606,0 7px 12px rgba(0,0,0,.5),inset 0 1px 0 rgba(255,255,255,.35)}
.key.down{transform:translateY(3px);box-shadow:0 2px 0 #121214,inset 0 1px 0 rgba(255,255,255,.1)}
.led{width:7px;height:7px;border-radius:50%;background:#3a3a3e;display:inline-block}.led.on{background:#ff8a3d;box-shadow:0 0 8px #ff8a3d}
.screw{position:absolute;width:10px;height:10px;border-radius:50%;background:radial-gradient(#8a8a90,#3a3a3e)}
.navd{position:absolute;left:14px;right:14px;bottom:24px;display:flex;gap:10px;z-index:10}.navd .key{flex:1;height:58px;flex-direction:column;gap:4px;font-weight:700;font-size:12px;letter-spacing:1px}"""


def nav4(on):
    return '<div class="navd">' + "".join(f'<div class="key {"down" if n == on else ""}"><span class="led {"on" if n == on else ""}"></span>{n}</div>' for n in ["HOME", "FIND", "SHELF"]) + "</div>"


def tape(i, title, w=170):
    h = int(w * 0.64)
    reel = lambda x: f'<div style="position:absolute;left:{x}px;top:{h * 0.42:.0f}px;width:{w * 0.2:.0f}px;height:{w * 0.2:.0f}px;border-radius:50%;background:#222;box-shadow:inset 0 0 0 {w * 0.04:.0f}px #eee,inset 0 0 0 {w * 0.06:.0f}px #222"></div>'
    return (f'<div style="position:relative;width:{w}px;height:{h}px;border-radius:10px;background:#2b2b2f;box-shadow:0 6px 14px rgba(0,0,0,.5),inset 0 1px 0 rgba(255,255,255,.1)">'
            f'<div style="position:absolute;left:8px;right:8px;top:8px;height:{h * 0.72:.0f}px;border-radius:6px;background:{ART[i % len(ART)]}"></div>'
            f'<div style="position:absolute;left:14px;right:14px;top:12px;height:20px;border-radius:3px;background:#f3eedc;color:#222;font:600 12px Rubik;display:flex;align-items:center;padding:0 6px" class="ell">{title}</div>'
            f'<div style="position:absolute;left:{w * 0.22:.0f}px;right:{w * 0.22:.0f}px;top:{h * 0.38:.0f}px;height:{w * 0.26:.0f}px;border-radius:{w * 0.13:.0f}px;background:#1a1a1a"></div>'
            f'{reel(w * 0.26)}{reel(w * 0.54)}</div>')


def vu(n=14, lit=9):
    return "".join(f'<i style="display:inline-block;width:6px;height:{8 + k}px;margin-right:3px;background:{"#1f2a12" if k < lit else "rgba(31,42,18,.2)"}"></i>' for k in range(n))


def c4_home():
    tapes = "".join(f'<div class="col" style="gap:6px">{tape(i + 1, t, 166)}</div>' for i, t in enumerate(["NIGHT DRIVE", "DUET MIX", "PAPER MOONS", "FOCUS FLOW"]))
    page("4-deck", "1-home", F4, C4, f"""<div class="metal" style="position:absolute;inset:0"></div>{status()}
<div style="padding:4px 14px 0;position:relative"><div class="row" style="justify-content:space-between;margin-bottom:8px"><span style="font-weight:800;letter-spacing:3px;font-size:13px;color:#a9a6a0">DUET · DUAL SOURCE DECK</span><span class="row" style="gap:6px;font-size:11px;color:#a9a6a0"><span class="led on"></span>SPOTIFY <span class="led on"></span>YTM</span></div>
<div class="lcd" style="padding:12px 14px"><div class="row" style="justify-content:space-between;font-size:20px"><span>▶ NOW PLAYING</span><span>01:16</span></div>
<div style="font-size:34px;line-height:1">TEETH</div><div style="font-size:22px">MARLOWE FINCH · YTM 256K</div><div style="margin-top:6px;height:24px;display:flex;align-items:flex-end">{vu(26, 17)}</div></div></div>
<div class="row" style="justify-content:space-between;padding:20px 14px 10px;position:relative"><span style="font-weight:800;letter-spacing:2px">YOUR TAPES</span><span style="font-size:12px;color:#a9a6a0">12 ▸</span></div>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:16px 14px;padding:0 14px;position:relative">{tapes}</div>
<div class="row" style="gap:10px;padding:20px 14px 0;position:relative"><div class="key or" style="flex:1;height:48px;font-weight:800;letter-spacing:1px">▶ DUET RADIO</div><div class="key" style="width:60px;height:48px">{ic("shuffle", 22)}</div></div>
<div class="lcd" style="margin:20px 14px 0;padding:8px 12px;font-size:22px;line-height:1.3;position:relative">RECENT<br>01 GLASSHOUSE ...... YTM<br>02 NIGHT BUS ....... SPT<br>03 SALTWATER ....... YTM</div>{nav4("HOME")}""", "#1c1c1f")


def c4_search():
    keys = "".join(f'<div class="key" style="height:62px;font-weight:700;font-size:15px;letter-spacing:1px;{"color:#ff8a3d" if i == 0 else ""}">{g.upper()}</div>' for i, g in enumerate(GENRES))
    page("4-deck", "2-search", F4, C4, f"""<div class="metal" style="position:absolute;inset:0"></div>{status()}
<div style="padding:4px 14px 0;position:relative"><span style="font-weight:800;letter-spacing:3px;font-size:13px;color:#a9a6a0">FIND</span>
<div class="lcd" style="padding:14px;margin-top:8px"><div style="font-size:22px">SEARCH BOTH SOURCES:</div><div style="font-size:40px;line-height:1">MARLOWE F<span style="background:#1f2a12;color:#c3dd8f">_</span></div></div>
<div class="row" style="gap:8px;margin-top:12px"><div class="key down" style="flex:1;height:40px;font-size:13px;font-weight:700"><span class="led on" style="margin-right:6px"></span>BOTH</div><div class="key" style="flex:1;height:40px;font-size:13px;font-weight:700"><span class="led" style="margin-right:6px"></span>SPOTIFY</div><div class="key" style="flex:1;height:40px;font-size:13px;font-weight:700"><span class="led" style="margin-right:6px"></span>YTM</div></div>
<div class="lcd" style="padding:8px 12px;margin-top:16px;font-size:22px;line-height:1.25">01 TEETH .......... YTM 3:20<br>02 TEETH LIVE ..... SPT 4:02<br>03 HOLLOW BELL .... SPT 4:15</div>
<div style="font-weight:800;letter-spacing:2px;margin:18px 0 10px">PRESETS</div>
<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px">{keys}</div></div>{nav4("FIND")}""", "#1c1c1f")


def c4_library():
    spines = "".join(f'<div style="width:{w}px;height:{h}px;border-radius:3px;background:{ART[i % len(ART)]};display:flex;align-items:center;justify-content:center;box-shadow:inset -3px 0 0 rgba(0,0,0,.3),0 4px 8px rgba(0,0,0,.4)"><span style="writing-mode:vertical-rl;transform:rotate(180deg);font:700 12px Rubik;letter-spacing:1px;color:#fff;text-shadow:0 1px 2px rgba(0,0,0,.6)">{t}</span></div>'
                     for i, (t, w, h) in enumerate([("NIGHT DRIVE", 34, 190), ("PAPER MOONS", 28, 170), ("FOCUS FLOW", 40, 200), ("SALTWATER", 30, 180), ("LIKED", 44, 205), ("KESTREL", 26, 160), ("DUET MIX", 36, 195), ("VELA", 30, 175), ("SUNDIAL", 34, 185)]))
    rows = "".join(f'<div class="row" style="height:44px;gap:10px;border-bottom:1px solid #2e2e33"><span class="led {"on" if i == 0 else ""}"></span><span style="flex:1;font-weight:600">{t}</span><span style="font-size:12px;color:#a9a6a0">{a}</span></div>' for i, (t, a) in enumerate([("Downloaded", "48 · 412 MB"), ("Cached", "112"), ("Albums", "31"), ("Artists", "56")]))
    page("4-deck", "3-library", F4, C4, f"""<div class="metal" style="position:absolute;inset:0"></div>{status()}
<div style="padding:4px 14px 0;position:relative"><div class="row" style="justify-content:space-between"><span style="font-weight:800;letter-spacing:3px;font-size:13px;color:#a9a6a0">SHELF</span><span class="lcd" style="padding:2px 8px;font-size:20px">27 TAPES</span></div>
<div style="margin-top:14px;padding:14px 10px 0;border-radius:10px;background:linear-gradient(180deg,#151517,#242428);box-shadow:inset 0 4px 10px rgba(0,0,0,.6)"><div class="row" style="align-items:flex-end;gap:5px;height:214px">{spines}</div><div style="height:12px;margin:0 -10px;background:linear-gradient(180deg,#5a4630,#3a2c1c);border-radius:0 0 10px 10px"></div></div>
<div style="margin-top:18px">{rows}</div></div>{nav4("SHELF")}""", "#1c1c1f")


def c4_now():
    knob = ('<div style="width:86px;height:86px;border-radius:50%;background:radial-gradient(circle at 35% 30%,#5a5a60,#1e1e21);box-shadow:0 6px 12px rgba(0,0,0,.6),inset 0 1px 0 rgba(255,255,255,.2);position:relative">'
            '<div style="position:absolute;left:40px;top:8px;width:6px;height:20px;border-radius:3px;background:#ff8a3d;transform-origin:3px 35px;transform:rotate(40deg)"></div></div>')
    sliders = "".join(f'<div style="width:8px;height:86px;border-radius:4px;background:#0f0f11;position:relative;box-shadow:inset 0 1px 3px #000"><div style="position:absolute;left:-7px;top:{t}px;width:22px;height:12px;border-radius:3px;background:linear-gradient(#ddd,#888)"></div></div>' for t in [20, 40, 30, 55, 25])
    page("4-deck", "4-now-playing", F4, C4, f"""<div class="metal" style="position:absolute;inset:0"></div>{status()}
<div class="row" style="justify-content:space-between;padding:4px 14px;position:relative"><div class="key" style="width:44px;height:40px">{ic("chevd", 22)}</div><span style="font-weight:800;letter-spacing:3px;font-size:13px;color:#a9a6a0">DECK A</span><div class="key" style="width:44px;height:40px">{ic("queue", 20)}</div></div>
<div style="margin:14px 14px 0;padding:16px;border-radius:16px;background:#0f0f11;box-shadow:inset 0 4px 14px rgba(0,0,0,.8);position:relative"><div class="screw" style="left:8px;top:8px"></div><div class="screw" style="right:8px;top:8px"></div><div class="screw" style="left:8px;bottom:8px"></div><div class="screw" style="right:8px;bottom:8px"></div>
<div style="display:flex;justify-content:center">{tape(6, "TEETH — MARLOWE FINCH", 300)}</div></div>
<div class="lcd" style="margin:16px 14px 0;padding:10px 14px"><div class="row" style="justify-content:space-between;font-size:24px"><span>TEETH</span><span>01:16 / 03:20</span></div><div style="height:10px;margin-top:6px;background:rgba(31,42,18,.2);position:relative"><div style="position:absolute;left:0;top:0;bottom:0;width:38%;background:#1f2a12"></div></div></div>
<div class="row" style="gap:10px;padding:18px 14px 0;position:relative">
 <div class="key" style="flex:1;height:64px">{ic("prev", 26)}</div><div class="key or" style="flex:1.5;height:64px">{ic("pause", 32)}</div><div class="key" style="flex:1;height:64px">{ic("next", 26)}</div><div class="key" style="flex:.8;height:64px">{ic("heartf", 22, 2, "#ff8a3d")}</div></div>
<div class="row" style="justify-content:space-between;padding:22px 24px 0;position:relative"><div class="col" style="align-items:center;gap:6px">{knob}<span style="font-size:11px;letter-spacing:2px;color:#a9a6a0">VOLUME</span></div>
<div class="col" style="align-items:center;gap:6px"><div class="row" style="gap:18px">{sliders}</div><span style="font-size:11px;letter-spacing:2px;color:#a9a6a0">EQUALIZER</span></div></div>""", "#1c1c1f")


# =====================================================================================
# 5. MONO: black and white, no cards, artwork full-bleed, giant type, text-only navigation.
# =====================================================================================
F5 = "family=Instrument+Serif:ital@0;1&family=Inter+Tight:wght@300;400;500;600;700"
C5 = """body,.scr{font-family:'Inter Tight',sans-serif;color:#fff}
.serif{font-family:'Instrument Serif',serif;font-weight:400}
.nav5{position:absolute;left:0;right:0;bottom:0;height:120px;background:#000;border-top:1px solid #222;z-index:10;padding:0 22px}
.nav5 .links{display:flex;gap:28px;margin-top:16px;font-size:17px;color:#666}.nav5 .links .on{color:#fff;border-bottom:1.5px solid #fff;padding-bottom:4px}
.line{height:1px;background:#222}"""


def nav5(on):
    return (f'<div class="nav5"><div class="row" style="height:44px;gap:10px;font-size:14px">{ic("pause", 14)}<span class="ell" style="flex:1"><b style="font-weight:600">Teeth</b> <span style="color:#777">— Marlowe Finch</span></span><span style="color:#777">1:16</span></div>'
            f'<div style="height:1px;background:#333;position:relative"><div style="position:absolute;left:0;top:-0.5px;height:2px;width:38%;background:#fff"></div></div>'
            f'<div class="links">' + "".join(f'<span class="{"on" if n == on else ""}">{n}</span>' for n in ["Listen", "Find", "Keep"]) + '<span style="margin-left:auto;color:#666">Szymon</span></div></div>')


def c5_home():
    rows = "".join(f'<div class="row" style="height:52px;gap:16px;border-bottom:1px solid #1c1c1c"><span class="serif" style="font-size:22px;color:#555;width:22px">{i + 1}</span><span style="flex:1;font-size:17px">{t}</span><span style="color:#666;font-size:14px">{a}</span></div>' for i, (t, a, s) in enumerate(SONGS[1:5]))
    page("5-mono", "1-home", F5, C5, f"""<div style="position:absolute;left:0;right:0;top:0;height:470px;background:{ART[6]};filter:grayscale(.15)"></div><div style="position:absolute;left:0;right:0;top:200px;height:270px;background:linear-gradient(transparent,#000)"></div>{status()}
<div style="position:absolute;left:22px;right:22px;top:290px"><div class="serif" style="font-size:22px;font-style:italic;color:#ddd">Continue with</div><div class="serif" style="font-size:82px;line-height:.9;letter-spacing:-2px">Teeth</div>
<div class="row" style="justify-content:space-between;margin-top:8px"><span style="color:#aaa">Marlowe Finch · YT Music</span><span class="row" style="gap:8px;font-weight:600">Resume {ic("arrow", 18)}</span></div></div>
<div style="position:absolute;left:22px;right:22px;top:490px"><div class="row" style="justify-content:space-between;margin-bottom:6px"><span style="font-size:13px;letter-spacing:2px;color:#777">UP NEXT FOR YOU</span><span style="font-size:13px;color:#777">Shuffle</span></div>{rows}</div>{nav5("Listen")}""", "#000")


def c5_search():
    words = "".join(f'<div class="row" style="justify-content:space-between;height:50px;border-bottom:1px solid #1a1a1a"><span class="serif" style="font-size:36px;{"font-style:italic" if i % 2 else ""}">{g}</span><span style="color:#555;font-size:13px">{c}</span></div>' for i, (g, c) in enumerate(zip(GENRES[:7], ["2.1k", "1.4k", "860", "1.9k", "1.2k", "940", "410"])))
    page("5-mono", "2-search", F5, C5, f"""{status()}
<div style="padding:24px 22px 0"><div class="serif" style="font-size:56px;font-style:italic;color:#444;line-height:1">Search<span style="color:#fff">|</span></div><div style="height:1.5px;background:#fff;margin-top:10px"></div>
<div class="row" style="gap:18px;margin-top:12px;font-size:14px;color:#666"><span style="color:#fff">Everything</span><span>Spotify</span><span>YT Music</span><span>Saved</span></div></div>
<div style="padding:22px 22px 0"><div style="font-size:13px;letter-spacing:2px;color:#777;margin-bottom:4px">OR WANDER</div>{words}</div>{nav5("Find")}""", "#000")


def c5_library():
    sec = lambda L, items: (f'<div class="serif" style="font-size:44px;line-height:1;margin-top:14px">{L}</div>'
                            + "".join(f'<div class="row" style="height:46px;border-bottom:1px solid #1a1a1a;gap:10px"><span style="flex:1;font-size:17px">{t}</span><span style="color:#666;font-size:13px">{k}</span></div>' for t, k in items))
    az = "".join(f'<span style="{"color:#fff" if c in "KLN" else ""}">{c}</span>' for c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ")
    page("5-mono", "3-library", F5, C5, f"""{status()}
<div style="padding:12px 40px 0 22px"><div class="row" style="justify-content:space-between;align-items:baseline"><span class="serif" style="font-size:48px">Keep</span><span style="color:#777;font-size:14px">326 songs · 27 lists</span></div>
<div class="row" style="gap:18px;margin-top:8px;font-size:14px;color:#666"><span style="color:#fff;border-bottom:1.5px solid #fff;padding-bottom:3px">All</span><span>On this phone (48)</span><span>Playlists</span><span>Artists</span></div>
{sec("K", [("Kestrel", "Artist"), ("Kites", "Vela")])}{sec("L", [("Low Tide Choir", "Artist"), ("Lantern", "Vela"), ("Liked songs", "326")])}{sec("N", [("Neon Orchard", "Artist"), ("Night Bus", "Kestrel"), ("Night Drive", "Playlist")])}</div>
<div class="col" style="position:absolute;right:10px;top:120px;gap:2px;font-size:10px;color:#555;align-items:center">{az}</div>{nav5("Keep")}""", "#000")


def c5_now():
    page("5-mono", "4-now-playing", F5, C5, f"""<div style="position:absolute;inset:0;background:{ART[6]}"></div><div style="position:absolute;left:0;right:0;top:330px;bottom:0;background:linear-gradient(transparent,#000 55%)"></div>{status()}
<div class="row" style="justify-content:space-between;padding:6px 22px;position:relative;font-size:14px"><span>{ic("chevd", 22)}</span><span style="letter-spacing:2px;font-size:12px">YT MUSIC · 256K</span><span>{ic("more", 22)}</span></div>
<div style="position:absolute;left:22px;right:22px;bottom:200px"><div style="font-size:13px;letter-spacing:2px;color:#bbb">FROM DUET MIX</div><div class="serif" style="font-size:96px;line-height:.85;letter-spacing:-3px;margin-top:6px">Teeth</div><div style="font-size:19px;color:#ccc;margin-top:6px">Marlowe Finch</div></div>
<div style="position:absolute;left:22px;right:22px;bottom:150px"><div style="height:1px;background:#444;position:relative"><div style="position:absolute;left:0;top:-1px;height:3px;width:38%;background:#fff"></div></div><div class="row" style="justify-content:space-between;font-size:12px;color:#888;margin-top:8px"><span>1:16</span><span>−2:04</span></div></div>
<div class="row" style="position:absolute;left:22px;right:22px;bottom:70px;justify-content:space-between">{ic("shuffle", 22, 1.6)}{ic("prev", 30, 1.6)}<div style="width:76px;height:76px;border-radius:38px;border:1.5px solid #fff;display:flex;align-items:center;justify-content:center">{ic("pause", 30)}</div>{ic("next", 30, 1.6)}{ic("heart", 22, 1.6)}</div>
<div style="position:absolute;left:0;right:0;bottom:26px;text-align:center;font-size:12px;letter-spacing:2px;color:#777">SWIPE UP FOR LYRICS & QUEUE</div>""", "#000")


for f in [c1_home, c1_search, c1_library, c1_now, c2_home, c2_search, c2_library, c2_now, c3_home, c3_search, c3_library, c3_now,
          c4_home, c4_search, c4_library, c4_now, c5_home, c5_search, c5_library, c5_now]:
    f()
print("ok")
