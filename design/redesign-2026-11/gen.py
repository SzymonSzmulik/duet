"""Generates Duet redesign concept screens (390x844) as HTML, one file per screen."""
import os, sys

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)

I = {  # 24px stroked icons
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
    "check": '<path d="m5 12 5 5 9-10"/>',
    "x": '<path d="M6 6l12 12M18 6 6 18"/>',
    "more": '<circle cx="5" cy="12" r="1.4" fill="currentColor"/><circle cx="12" cy="12" r="1.4" fill="currentColor"/><circle cx="19" cy="12" r="1.4" fill="currentColor"/>',
    "back": '<path d="M15 5l-7 7 7 7"/>',
    "chev": '<path d="m9 5 7 7-7 7"/>',
    "chevd": '<path d="m6 9 6 6 6-6"/>',
    "gear": '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1"/>',
    "person": '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    "heart": '<path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"/>',
    "heartf": '<path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z" fill="currentColor"/>',
    "radio": '<path d="M4 9h16v11H4zM8 4l9 5"/><circle cx="9" cy="14.5" r="2"/><path d="M14 13h3M14 16h3"/>',
    "trash": '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
    "bt": '<path d="m7 7 10 10-5 4V3l5 4L7 17"/>',
    "car": '<path d="M5 16v-5l2-5h10l2 5v5M3 16h18v3H3z"/>',
    "grid": '<path d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z"/>',
    "list": '<path d="M4 6h16M4 12h16M4 18h16"/>',
    "plus": '<path d="M12 5v14M5 12h14"/>',
    "sort": '<path d="M7 4v16M3 16l4 4 4-4M17 20V4M13 8l4-4 4 4"/>',
    "drag": '<path d="M5 9h14M5 15h14"/>',
    "share": '<path d="M12 3v12M7 8l5-5 5 5M5 14v6h14v-6"/>',
    "album": '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="2.5"/>',
    "sleep": '<path d="M20 14A8 8 0 1 1 10 4a6 6 0 0 0 10 10z"/>',
    "wave": '<path d="M3 12h2M7 8v8M11 5v14M15 9v6M19 11v2"/>',
    "clock": '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    "wifi0": '<path d="M2 8.5a15 15 0 0 1 20 0M5 12a10 10 0 0 1 14 0M8.5 15.5a5 5 0 0 1 7 0"/><path d="M3 3l18 18"/>',
    "mic": '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/>',
    "sparkle": '<path d="M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z"/>',
}

def ic(name, size=24, sw=2, color="currentColor", cls=""):
    return (f'<svg class="{cls}" width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="{color}" '
            f'stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round">{I[name]}</svg>')

ART = [  # invented covers: gradients only, no real artwork
    "linear-gradient(135deg,#ff8a5b,#c2185b 60%,#3a0d2e)",
    "linear-gradient(160deg,#c8f26b,#2f8f5b 55%,#0c2a23)",
    "linear-gradient(135deg,#8f7bff,#3b2a8f 60%,#120b33)",
    "linear-gradient(200deg,#ffd36b,#ff6b6b 50%,#4a1030)",
    "linear-gradient(135deg,#5ee0d0,#1d6e8f 55%,#0b1d33)",
    "linear-gradient(120deg,#f1f2f7,#9aa0b4 50%,#2c3040)",
    "radial-gradient(circle at 30% 30%,#ffb84d,#a33b12 55%,#2a0c05)",
    "linear-gradient(145deg,#ff6bd5,#6b3bff 60%,#14093a)",
    "radial-gradient(circle at 70% 20%,#9ef0ff,#3060ff 50%,#0a0f33)",
    "linear-gradient(160deg,#3a3d2a,#5a2a14 50%,#2a0c05)",
]
def art(i, size, r=10, extra=""):
    return f'<div class="art" style="width:{size}px;height:{size}px;border-radius:{r}px;background:{ART[i % len(ART)]};{extra}"></div>'

SP, YT = "#3ecf7a", "#ff6b6b"
def src(color, text, dim=True):
    return f'<span class="src"><i style="background:{color}"></i><span class="{"dim" if dim else ""}">{text}</span></span>'

CSS = """
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap');
:root{--g:#0e0f14;--s:#1c1e27;--r:#2c3040;--l:#3a3e50;--t:#f1f2f7;--d:#9aa0b4;--a:#c8f26b;--oa:#0e0f14;--sp:#3ecf7a;--yt:#ff6b6b;--danger:#ff5a5a}
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:390px;height:844px;overflow:hidden;background:var(--g);color:var(--t);font-family:'Plus Jakarta Sans',system-ui,sans-serif;-webkit-font-smoothing:antialiased}
.screen{position:relative;width:390px;height:844px;overflow:hidden}
.status{height:44px;display:flex;align-items:center;justify-content:space-between;padding:0 26px;font-weight:700;font-size:15px;position:relative;z-index:5}
.status .r{display:flex;gap:6px;align-items:center}
.bat{width:24px;height:12px;border:1.5px solid var(--t);border-radius:4px;padding:1.5px}.bat div{width:70%;height:100%;background:var(--t);border-radius:1.5px}
.pad{padding:0 16px}
.row{display:flex;align-items:center;gap:12px}
.col{display:flex;flex-direction:column}
.dim{color:var(--d)}
.h1{font-size:30px;font-weight:800;letter-spacing:-.6px}
.h2{font-size:20px;font-weight:800;letter-spacing:-.3px}
.t{font-size:15px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.sub{font-size:13px;font-weight:500;color:var(--d);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.src{display:inline-flex;align-items:center;gap:6px;font-size:13px;font-weight:600}
.src i{width:7px;height:7px;border-radius:50%;display:inline-block;flex:none}
.chip{height:34px;padding:0 14px;border-radius:17px;background:var(--s);display:inline-flex;align-items:center;gap:7px;font-size:14px;font-weight:700;white-space:nowrap;border:1px solid transparent}
.chip.on{background:var(--a);color:var(--oa)}
.chip.line{background:transparent;border-color:var(--l)}
.chip i{width:7px;height:7px;border-radius:50%}
.chips{display:flex;gap:8px;overflow:hidden}
.iconbtn{width:44px;height:44px;border-radius:22px;display:flex;align-items:center;justify-content:center;flex:none}
.iconbtn.fill{background:var(--s)}
.art{flex:none;box-shadow:inset 0 0 0 1px rgba(255,255,255,.06)}
.card{background:var(--s);border-radius:16px}
.btn{height:48px;border-radius:24px;padding:0 22px;display:inline-flex;align-items:center;justify-content:center;gap:8px;font-weight:800;font-size:15px}
.btn.pri{background:var(--a);color:var(--oa)}
.btn.sec{background:var(--r);color:var(--t)}
.btn.ghost{border:1px solid var(--l)}
.tabbar{position:absolute;left:16px;right:16px;bottom:22px;height:64px;border-radius:32px;background:rgba(28,30,39,.92);backdrop-filter:blur(20px);display:flex;align-items:center;justify-content:space-around;padding:0 8px;box-shadow:0 10px 30px rgba(0,0,0,.5);border:1px solid rgba(255,255,255,.05);z-index:10}
.tab{display:flex;align-items:center;gap:8px;height:48px;padding:0 18px;border-radius:24px;color:var(--d);font-weight:700;font-size:14px}
.tab.on{background:var(--a);color:var(--oa)}
.mini{position:absolute;left:16px;right:16px;bottom:96px;height:62px;border-radius:20px;background:linear-gradient(90deg,#3a2414,#22201c);display:flex;align-items:center;gap:12px;padding:0 8px 0 8px;box-shadow:0 10px 30px rgba(0,0,0,.45);overflow:hidden;z-index:10}
.mini .prog{position:absolute;left:14px;right:14px;bottom:0;height:3px;border-radius:2px;background:rgba(255,255,255,.12)}
.mini .prog div{height:100%;width:38%;background:var(--a);border-radius:2px}
.fade{position:absolute;left:0;right:0;bottom:0;height:200px;background:linear-gradient(transparent,var(--g) 60%);z-index:9}
.bar{height:4px;border-radius:2px;background:rgba(255,255,255,.18);position:relative}
.bar>.f{position:absolute;left:0;top:0;bottom:0;background:var(--a);border-radius:2px}
.knob{position:absolute;top:50%;width:14px;height:14px;border-radius:50%;background:var(--a);transform:translate(-50%,-50%);box-shadow:0 0 0 5px rgba(200,242,107,.2)}
.sheet{position:absolute;left:0;right:0;bottom:0;background:var(--s);border-radius:28px 28px 0 0;z-index:20;padding:10px 0 34px}
.grab{width:40px;height:5px;border-radius:3px;background:var(--l);margin:0 auto 14px}
.scrim{position:absolute;inset:0;background:rgba(0,0,0,.55);z-index:15}
.menuitem{display:flex;align-items:center;gap:16px;height:54px;padding:0 24px;font-size:16px;font-weight:600}
.badge{width:18px;height:18px;border-radius:9px;background:var(--a);color:var(--oa);display:inline-flex;align-items:center;justify-content:center;flex:none}
.label{font-size:12px;font-weight:800;letter-spacing:1.2px;text-transform:uppercase;color:var(--d)}
.sep{height:1px;background:var(--l);opacity:.5}
"""

def status(light=True):
    return ('<div class="status"><span>9:41</span><span class="r">'
            + ic("wave", 16, 2.4) + ic("bt", 15, 2.2) + '<div class="bat"><div></div></div></span></div>')

def tabbar(on):
    tabs = [("home", "Home"), ("search", "Search"), ("lib", "Library")]
    return '<div class="tabbar">' + "".join(
        f'<div class="tab {"on" if n == on else ""}">{ic(i, 22, 2.2)}{n if n == on else ""}</div>' for i, n in tabs) + "</div>"

def mini(title="Teeth", artist="Marlowe Finch", color=YT, playing=True, bottom=96):
    return (f'<div class="mini" style="bottom:{bottom}px">{art(0, 46, 12)}<div class="col" style="flex:1;min-width:0;gap:3px"><div class="t">{title}</div>'
            f'{src(color, artist + " · YT Music" if color == YT else artist + " · Spotify")}</div>'
            f'<div class="iconbtn">{ic("heartf", 22, 2, "var(--a)")}</div>'
            f'<div class="iconbtn" style="background:var(--t);color:var(--g);width:42px;height:42px">{ic("pause" if playing else "play", 20)}</div>'
            f'<div class="prog"><div></div></div></div>')

def page(name, body, bg="var(--g)"):
    html = f'<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head><body><div class="screen" style="background:{bg}">{body}</div></body></html>'
    open(os.path.join(OUT, name + ".html"), "w").write(html)

def trow(i, title, artist, color, right="", size=52, dl=False, playing=False):
    t_color = "var(--a)" if playing else "var(--t)"
    badge = f'<span class="badge">{ic("down", 12, 3)}</span>' if dl else ""
    return (f'<div class="row" style="height:64px">{art(i, size, 8)}<div class="col" style="flex:1;min-width:0;gap:3px">'
            f'<div class="t" style="color:{t_color}">{title}</div><div class="row" style="gap:6px">{badge}{src(color, artist)}</div></div>{right}</div>')

# ---------- 01 Home ----------
shelf = "".join(f'<div class="col" style="gap:8px;width:140px">{art(i+2, 140, 14)}<div class="t" style="font-size:14px">{n}</div><div class="sub">{s}</div></div>'
                for i, (n, s) in enumerate([("Night Drive", "Mix · 50 songs"), ("Low Tide Choir", "Artist radio"), ("Paper Moons", "Album · Vela"), ("Focus Flow", "Playlist")]))
quick = "".join(f'<div class="row" style="background:var(--s);border-radius:12px;height:56px;gap:10px;overflow:hidden">{art(i, 56, 0)}<div class="t" style="font-size:13px;flex:1">{n}</div></div>'
                for i, n in enumerate(["Teeth", "Liked songs", "Neon Orchard", "Downloaded", "Sundial Kids", "Kestrel"]))
page("01-home", f"""
<div style="position:absolute;inset:0 0 auto 0;height:420px;background:radial-gradient(120% 80% at 20% 0%,rgba(255,138,91,.35),transparent 70%)"></div>
{status()}
<div class="pad row" style="justify-content:space-between;margin-top:6px;position:relative">
  <div class="col"><div class="sub" style="font-size:14px">Good evening, Szymon</div><div class="h1">Listen now</div></div>
  <div class="row" style="gap:6px"><div class="iconbtn fill">{ic("car", 22)}</div>
  <div class="iconbtn" style="background:linear-gradient(135deg,var(--a),#5ee0d0);color:var(--oa);font-weight:800">S</div></div>
</div>
<div class="pad chips" style="margin-top:18px;position:relative"><span class="chip on">All</span><span class="chip">{ '<i style="background:#3ecf7a"></i>' }Spotify</span><span class="chip"><i style="background:#ff6b6b"></i>YT Music</span><span class="chip">Downloaded</span></div>
<div class="pad" style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:18px;position:relative">{quick}</div>
<div class="pad" style="margin-top:22px;position:relative">
 <div style="height:132px;border-radius:22px;background:linear-gradient(115deg,#c8f26b,#5ee0d0 55%,#8f7bff);color:var(--oa);padding:18px 20px;display:flex;justify-content:space-between;align-items:flex-end;overflow:hidden;position:relative">
  <div style="position:absolute;right:-30px;top:-40px;width:170px;height:170px;border-radius:50%;border:26px solid rgba(14,15,20,.08)"></div>
  <div class="col" style="gap:4px"><div class="label" style="color:rgba(14,15,20,.6)">Made for you</div><div style="font-size:26px;font-weight:800;letter-spacing:-.5px">Duet Radio</div><div style="font-size:13px;font-weight:600;opacity:.75">Both services, one endless mix</div></div>
  <div class="iconbtn" style="background:var(--g);color:var(--a);width:54px;height:54px;border-radius:27px">{ic("play", 26)}</div>
 </div>
</div>
<div class="pad row" style="justify-content:space-between;margin-top:24px"><div class="h2">Jump back in</div><span class="sub" style="font-weight:700">See all</span></div>
<div class="pad row" style="gap:12px;margin-top:12px;align-items:flex-start">{shelf}</div>
<div class="fade"></div>{mini()}{tabbar("Home")}
""")

# ---------- 02 Search (idle) ----------
genres = [("Pop", 0), ("Hip-hop", 3), ("Chill", 4), ("Rock", 6), ("Electronic", 7), ("Indie", 1), ("Focus", 8), ("Workout", 2)]
gtiles = "".join(f'<div style="height:96px;border-radius:16px;background:{ART[c]};padding:14px;font-weight:800;font-size:17px;position:relative;overflow:hidden">{n}'
                 f'<div style="position:absolute;right:-14px;bottom:-14px;width:64px;height:64px;border-radius:12px;transform:rotate(22deg);background:rgba(255,255,255,.18)"></div></div>' for n, c in genres)
page("02-search", f"""
{status()}
<div class="pad" style="margin-top:6px"><div class="h1">Search</div></div>
<div class="pad" style="margin-top:14px"><div class="row" style="height:52px;border-radius:26px;background:var(--t);color:var(--g);padding:0 18px;gap:12px">{ic("search", 22, 2.4)}<span style="flex:1;font-weight:600;color:#5c6070">Songs, artists, albums</span>{ic("mic", 20, 2.2)}</div></div>
<div class="pad" style="margin-top:20px"><div class="row" style="justify-content:space-between"><div class="label">Recent</div><span class="sub" style="font-weight:700">Clear</span></div>
<div class="chips" style="flex-wrap:wrap;margin-top:10px">{"".join(f'<span class="chip line">{ic("clock",15,2.2)}{q}</span>' for q in ["marlowe finch", "night drive", "vela", "lofi"])}</div></div>
<div class="pad" style="margin-top:24px"><div class="label">Browse</div></div>
<div class="pad" style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:10px">{gtiles}</div>
<div class="fade"></div>{mini()}{tabbar("Search")}
""")

# ---------- 03 Search results ----------
res = "".join(trow(i, t, a, c, f'<div class="iconbtn dim">{ic("more", 22)}</div>', dl=d) for i, (t, a, c, d) in enumerate([
    ("Teeth", "Marlowe Finch · 3:20", YT, True), ("Teeth (Live)", "Marlowe Finch · 4:02", SP, False),
    ("Sharp Teeth", "Kestrel · 2:58", SP, False), ("Teeth & Tongue", "Neon Orchard · 3:41", YT, False), ("Milk Teeth", "Sundial Kids · 3:05", SP, False)]))
page("03-search-results", f"""
{status()}
<div class="pad row" style="margin-top:6px;gap:8px"><div class="row" style="flex:1;height:48px;border-radius:24px;background:var(--s);padding:0 16px;gap:10px">{ic("search", 20, 2.4)}<span style="flex:1;font-weight:700">teeth</span>{ic("x", 18, 2.4, "var(--d)")}</div></div>
<div class="pad chips" style="margin-top:14px"><span class="chip on">Top</span><span class="chip">Songs</span><span class="chip">Albums</span><span class="chip">Artists</span><span class="chip">Playlists</span></div>
<div class="pad" style="margin-top:16px"><div class="card" style="padding:16px;display:flex;gap:16px;align-items:center;background:linear-gradient(135deg,#3a1d2e,#1c1e27)">
 {art(0, 92, 46)}<div class="col" style="gap:6px;flex:1"><div class="label">Top result · Artist</div><div style="font-size:22px;font-weight:800">Marlowe Finch</div>{src(YT, "On YT Music and Spotify")}</div>
 <div class="iconbtn" style="background:var(--a);color:var(--oa);width:50px;height:50px;border-radius:25px">{ic("play", 24)}</div></div></div>
<div class="pad" style="margin-top:18px"><div class="label">Songs</div><div style="margin-top:4px">{res}</div></div>
<div class="fade"></div>{mini()}{tabbar("Search")}
""")

# ---------- 04 Library ----------
plist = "".join(f'<div class="row" style="height:70px">{art(i+3, 58, 10)}<div class="col" style="flex:1;gap:3px;min-width:0"><div class="t">{n}</div>{src(c, s)}</div></div>'
                for i, (n, s, c) in enumerate([("Night Drive", "Playlist · 50 songs", SP), ("Paper Moons", "Album · Vela", YT), ("Low Tide Choir", "Artist", SP), ("Focus Flow", "Playlist · 112 songs", YT)]))
page("04-library", f"""
{status()}
<div class="pad row" style="justify-content:space-between;margin-top:6px"><div class="h1">Library</div><div class="row" style="gap:4px"><div class="iconbtn">{ic("search", 22)}</div><div class="iconbtn fill">{ic("plus", 22)}</div></div></div>
<div class="pad chips" style="margin-top:12px"><span class="chip on">All</span><span class="chip">Playlists</span><span class="chip">Albums</span><span class="chip">Artists</span><span class="chip"><i style="background:#3ecf7a"></i>Spotify</span></div>
<div class="pad" style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:16px">
 <div style="height:120px;border-radius:18px;background:linear-gradient(150deg,#1f6e5a,#0c2a23);padding:14px;display:flex;flex-direction:column;justify-content:space-between">
  <div class="iconbtn" style="width:38px;height:38px;background:rgba(200,242,107,.18);color:var(--a)">{ic("down", 20, 2.4)}</div>
  <div><div class="t">Downloaded</div><div class="sub" style="color:#b7d9c8">48 songs · 412 MB</div></div></div>
 <div style="height:120px;border-radius:18px;background:linear-gradient(150deg,#5a4bd1,#1b1446);padding:14px;display:flex;flex-direction:column;justify-content:space-between">
  <div class="iconbtn" style="width:38px;height:38px;background:rgba(255,255,255,.14)">{ic("heartf", 20, 2)}</div>
  <div><div class="t">Liked songs</div><div class="sub" style="color:#c9c2ff">326 songs · both</div></div></div>
</div>
<div class="pad row" style="justify-content:space-between;margin-top:20px"><div class="row sub" style="gap:6px;font-weight:700;color:var(--t)">{ic("sort", 16, 2.2)}Recently played</div><div class="dim">{ic("grid", 20, 2)}</div></div>
<div class="pad" style="margin-top:6px">{plist}</div>
<div class="fade"></div>{mini()}{tabbar("Library")}
""")

# ---------- 05 Downloaded (with remove) ----------
dl_rows = [("Teeth", "Marlowe Finch", YT, "8.1 MB"), ("Glasshouse", "Vela", YT, "6.4 MB"), ("Night Bus", "Kestrel", SP, "7.0 MB"),
           ("Saltwater", "Low Tide Choir", YT, "9.2 MB"), ("Orchard Lights", "Neon Orchard", SP, "5.8 MB"), ("Sundial", "Sundial Kids", YT, "6.9 MB")]
drows = "".join(trow(i, t, f"{a} · {s}", c, f'<div class="iconbtn dim">{ic("more", 22)}</div>', dl=True, playing=(i == 0)) for i, (t, a, c, s) in enumerate(dl_rows))
page("05-downloaded", f"""
<div style="position:absolute;inset:0 0 auto 0;height:360px;background:linear-gradient(180deg,#1f6e5a,transparent)"></div>
{status()}
<div class="pad row" style="justify-content:space-between;position:relative"><div class="iconbtn">{ic("back", 24, 2.4)}</div><div class="row" style="gap:4px"><div class="iconbtn">{ic("check", 22, 2.2)}</div><div class="iconbtn">{ic("more", 22)}</div></div></div>
<div class="pad" style="position:relative;margin-top:4px">
 <div class="label" style="color:#b7d9c8">On this phone</div><div class="h1" style="font-size:34px">Downloaded</div>
 <div class="card" style="margin-top:14px;padding:14px;background:rgba(14,15,20,.45)">
  <div class="row" style="justify-content:space-between"><span class="t" style="font-size:14px">412 MB used</span><span class="sub">23.6 GB free</span></div>
  <div style="display:flex;height:8px;border-radius:4px;overflow:hidden;margin-top:10px;background:rgba(255,255,255,.1)"><div style="width:18%;background:var(--yt)"></div><div style="width:6%;background:var(--sp)"></div></div>
  <div class="row" style="gap:14px;margin-top:10px">{src(YT, "YT Music 318 MB")}{src(SP, "Spotify matches 94 MB")}</div>
 </div>
 <div class="row" style="margin-top:16px;gap:10px"><div class="btn pri" style="flex:1">{ic("play", 20)}Play all</div><div class="btn sec">{ic("shuffle", 20, 2.2)}</div><div class="btn ghost" style="color:var(--danger)">{ic("trash", 20, 2.2)}</div></div>
</div>
<div class="pad" style="margin-top:10px">{drows}</div>
<div class="fade"></div>{mini(bottom=28)}
""")

# ---------- 06 Downloaded, swipe to remove ----------
swipe = (f'<div style="position:relative;height:64px;margin:0 -16px"><div style="position:absolute;inset:0;background:var(--danger);display:flex;align-items:center;justify-content:flex-end;padding-right:22px;gap:8px;font-weight:800;color:#2a0505">{ic("trash", 22, 2.4)}Remove</div>'
         f'<div style="position:absolute;inset:0;transform:translateX(-118px);background:var(--g);padding:0 16px">{trow(1, "Glasshouse", "Vela · 6.4 MB", YT, "", dl=True)}</div></div>')
page("06-downloaded-swipe", f"""
<div style="position:absolute;inset:0 0 auto 0;height:360px;background:linear-gradient(180deg,#1f6e5a,transparent)"></div>
{status()}
<div class="pad row" style="justify-content:space-between;position:relative"><div class="iconbtn">{ic("back", 24, 2.4)}</div><div class="iconbtn">{ic("more", 22)}</div></div>
<div class="pad" style="position:relative"><div class="h1" style="font-size:34px">Downloaded</div><div class="sub">48 songs · 412 MB · swipe left to remove</div></div>
<div class="pad" style="margin-top:18px">{trow(0, "Teeth", "Marlowe Finch · 8.1 MB", YT, "", dl=True, playing=True)}{swipe}{"".join(trow(i+2, t, f"{a} · {s}", c, "", dl=True) for i, (t, a, c, s) in enumerate(dl_rows[2:]))}</div>
<div style="position:absolute;left:16px;right:16px;bottom:30px;height:56px;border-radius:16px;background:var(--r);display:flex;align-items:center;padding:0 18px;justify-content:space-between;box-shadow:0 10px 30px rgba(0,0,0,.5);z-index:12">
 <span style="font-weight:600;font-size:14px">Removed “Night Bus” · 7.0 MB freed</span><span style="color:var(--a);font-weight:800">Undo</span></div>
""")

# ---------- 07 Downloaded, select mode ----------
def selrow(i, t, a, c, on):
    box = (f'<div style="width:24px;height:24px;border-radius:7px;flex:none;display:flex;align-items:center;justify-content:center;'
           f'{"background:var(--a);color:var(--oa)" if on else "border:2px solid var(--l)"}">{ic("check", 16, 3) if on else ""}</div>')
    return f'<div class="row" style="height:64px">{box}{art(i, 48, 8)}<div class="col" style="flex:1;min-width:0;gap:3px"><div class="t">{t}</div>{src(c, a)}</div></div>'
page("07-downloaded-select", f"""
{status()}
<div class="pad row" style="justify-content:space-between"><div class="row" style="gap:4px"><div class="iconbtn">{ic("x", 24, 2.4)}</div><div class="h2">3 selected</div></div><span style="font-weight:800;color:var(--a)">Select all</span></div>
<div class="pad" style="margin-top:10px">{"".join(selrow(i, t, f"{a} · {s}", c, i in (1, 2, 4)) for i, (t, a, c, s) in enumerate(dl_rows))}</div>
<div style="position:absolute;left:0;right:0;bottom:0;padding:16px 16px 34px;background:var(--s);border-radius:24px 24px 0 0;z-index:12">
 <div class="sub" style="text-align:center;margin-bottom:12px">Frees 19.2 MB · songs stay in your library and can still stream</div>
 <div class="row" style="gap:10px"><div class="btn sec" style="flex:1">{ic("queue", 20, 2.2)}Add to queue</div><div class="btn" style="flex:1;background:var(--danger);color:#2a0505">{ic("trash", 20, 2.4)}Remove 3</div></div>
</div>
""")

# ---------- 08 Song menu sheet ----------
items = [("queue", "Add to queue", ""), ("plus", "Add to playlist", ""), ("radio", "Start radio", ""), ("album", "Go to album", ""),
         ("person", "Go to artist", ""), ("share", "Share", ""), ("trash", "Remove download", "color:var(--danger)")]
page("08-song-menu", f"""
{status()}
<div class="pad" style="margin-top:6px;opacity:.5"><div class="h1">Downloaded</div>{"".join(trow(i, t, a, c, dl=True) for i, (t, a, c, s) in enumerate(dl_rows))}</div>
<div class="scrim"></div>
<div class="sheet"><div class="grab"></div>
 <div class="row" style="padding:0 24px 16px;gap:14px">{art(1, 56, 10)}<div class="col" style="flex:1;gap:4px"><div class="t" style="font-size:17px">Glasshouse</div><div class="row" style="gap:6px"><span class="badge">{ic("down", 12, 3)}</span>{src(YT, "Vela · Saved · 6.4 MB")}</div></div><div class="iconbtn fill">{ic("heart", 22)}</div></div>
 <div class="sep" style="margin:0 24px 6px"></div>
 {"".join(f'<div class="menuitem" style="{st}">{ic(i, 22, 2.2)}{n}</div>' for i, n, st in items)}
</div>
""")

# ---------- 09 Remove dialog ----------
page("09-remove-dialog", f"""
{status()}
<div class="pad" style="margin-top:6px;opacity:.5"><div class="h1">Downloaded</div>{"".join(trow(i, t, a, c, dl=True) for i, (t, a, c, s) in enumerate(dl_rows))}</div>
<div class="scrim"></div>
<div style="position:absolute;left:28px;right:28px;top:250px;background:var(--r);border-radius:28px;padding:26px 24px 20px;z-index:20;text-align:center">
 <div style="width:64px;height:64px;border-radius:32px;background:rgba(255,90,90,.15);color:var(--danger);display:flex;align-items:center;justify-content:center;margin:0 auto">{ic("trash", 30, 2.2)}</div>
 <div class="h2" style="margin-top:16px">Remove download?</div>
 <div class="sub" style="white-space:normal;margin-top:8px;line-height:1.5;font-size:14px">“Glasshouse” will be deleted from this phone and free 6.4 MB. You can still stream it or save it again.</div>
 <div class="col" style="gap:10px;margin-top:22px"><div class="btn" style="background:var(--danger);color:#2a0505">Remove</div><div class="btn ghost">Cancel</div></div>
</div>
""")

# ---------- 10 Now playing ----------
np_bg = "radial-gradient(120% 70% at 50% 15%,#a33b12 0%,#5a1a08 45%,#160805 100%)"
def np_controls(play_icon="pause"):
    return f"""
 <div class="row" style="gap:10px;margin-top:20px">
  <div style="flex:1;height:72px;border-radius:36px;background:rgba(255,255,255,.1);display:flex;align-items:center;justify-content:center">{ic("prev", 28)}</div>
  <div style="flex:1.3;height:80px;border-radius:28px;background:var(--a);color:var(--oa);display:flex;align-items:center;justify-content:center">{ic(play_icon, 34)}</div>
  <div style="flex:1;height:72px;border-radius:36px;background:rgba(255,255,255,.1);display:flex;align-items:center;justify-content:center">{ic("next", 28)}</div>
 </div>"""
page("10-now-playing", f"""
{status()}
<div class="pad row" style="justify-content:space-between;padding:0 12px"><div class="iconbtn">{ic("chevd", 26, 2.4)}</div>
 <div class="col" style="align-items:center"><div class="label" style="color:rgba(255,255,255,.6);font-size:11px">Playing from</div><div class="t" style="font-size:14px">Duet Radio</div></div><div class="iconbtn">{ic("more", 24)}</div></div>
<div style="padding:18px 28px 0">{art(6, 334, 24, "box-shadow:0 30px 60px rgba(0,0,0,.5)")}</div>
<div style="padding:0 28px">
 <div class="row" style="margin-top:26px;justify-content:space-between"><div class="col" style="gap:4px;min-width:0"><div style="font-size:26px;font-weight:800;letter-spacing:-.4px">Teeth</div><div style="font-size:17px;font-weight:500;color:rgba(255,255,255,.7)">Marlowe Finch</div></div>
  <div class="row" style="gap:2px"><div class="iconbtn" style="color:var(--a)">{ic("check", 24, 2.6)}</div><div class="iconbtn" style="color:var(--a)">{ic("heartf", 24)}</div></div></div>
 <div class="bar" style="margin-top:22px"><div class="f" style="width:38%"></div><div class="knob" style="left:38%"></div></div>
 <div class="row" style="justify-content:space-between;margin-top:10px;font-size:12px;font-weight:700;color:rgba(255,255,255,.65)"><span>1:16</span>
  <span style="background:rgba(255,255,255,.12);padding:3px 10px;border-radius:10px;display:inline-flex;gap:6px;align-items:center"><i style="width:7px;height:7px;border-radius:50%;background:var(--yt)"></i>YT Music · Opus 160k</span><span>-2:04</span></div>
 {np_controls()}
 <div class="row" style="justify-content:space-between;margin-top:18px;color:rgba(255,255,255,.75)"><div class="iconbtn">{ic("lyrics", 22, 2)}</div>
  <div class="row" style="gap:6px;font-size:13px;font-weight:700;color:#c9b8ff">{ic("bt", 16, 2.2)}WF-1000XM3</div><div class="iconbtn">{ic("queue", 22, 2)}</div></div>
</div>
""", bg=np_bg)

# ---------- 11 Now playing, lyrics ----------
page("11-lyrics", f"""
{status()}
<div class="pad row" style="gap:12px;margin-top:4px">{art(6, 52, 10)}<div class="col" style="flex:1;gap:3px"><div class="t" style="font-size:17px">Teeth</div><div class="sub" style="color:rgba(255,255,255,.7)">Marlowe Finch</div></div><div class="iconbtn">{ic("chevd", 26, 2.4)}</div></div>
<div style="padding:30px 28px 0;font-size:31px;font-weight:800;line-height:1.25;letter-spacing:-.5px">
 <div style="color:rgba(255,255,255,.3)">We kept the lights on<br>past the tide</div>
 <div style="margin-top:22px">And every word you<br>swallowed bit me back</div>
 <div style="margin-top:22px;color:rgba(255,255,255,.3)">Cold coffee, open<br>window, quiet track</div>
 <div style="margin-top:22px;color:rgba(255,255,255,.14)">Show your teeth<br>when you smile</div>
</div>
<div style="position:absolute;left:28px;right:28px;bottom:40px">
 <div class="bar"><div class="f" style="width:38%"></div><div class="knob" style="left:38%"></div></div>
 <div class="row" style="justify-content:space-between;margin-top:8px;font-size:12px;font-weight:700;color:rgba(255,255,255,.65)"><span>1:16</span><span>Lyrics from LRCLIB</span><span>-2:04</span></div>
 {np_controls()}
</div>
""", bg="linear-gradient(170deg,#7a2c10,#3a1208 55%,#140604)")

# ---------- 12 Queue ----------
q = [("Glasshouse", "Vela", YT), ("Night Bus", "Kestrel", SP), ("Saltwater", "Low Tide Choir", YT), ("Orchard Lights", "Neon Orchard", SP), ("Sundial", "Sundial Kids", YT)]
page("12-queue", f"""
<div style="position:absolute;inset:0;background:{np_bg};opacity:.6"></div>
<div class="scrim" style="background:rgba(0,0,0,.35)"></div>
<div class="sheet" style="top:70px;background:#16171e">
 <div class="grab"></div>
 <div class="pad row" style="justify-content:space-between;padding:0 20px"><div class="h2">Up next</div><div class="row" style="gap:6px"><span class="chip on" style="height:32px">{ic("shuffle", 16, 2.4)}Shuffle</span><span class="chip" style="height:32px">{ic("repeat", 16, 2.4)}</span></div></div>
 <div style="padding:14px 20px 0"><div class="label">Now playing</div>
  <div class="row" style="height:68px;gap:12px">{art(6, 52, 10)}<div class="col" style="flex:1;gap:3px"><div class="t" style="color:var(--a)">Teeth</div>{src(YT, "Marlowe Finch")}</div><div style="color:var(--a)">{ic("wave", 22, 2.4)}</div></div>
  <div class="label" style="margin-top:10px">Next from Duet Radio · mixes both services</div>
  {"".join(f'<div class="row" style="height:64px;gap:12px">{art(i+1, 48, 8)}<div class="col" style="flex:1;gap:3px;min-width:0"><div class="t">{t}</div>{src(c, a)}</div><div class="dim">{ic("drag", 22, 2.2)}</div></div>' for i, (t, a, c) in enumerate(q))}
 </div>
</div>
""")

# ---------- 13 Album page ----------
alb = [("Glasshouse", "3:41"), ("Paper Moons", "4:12"), ("Kites", "2:55"), ("The Long Way", "5:03"), ("Lantern", "3:28"), ("Undertow", "4:40")]
page("13-album", f"""
<div style="position:absolute;inset:0 0 auto 0;height:470px;background:linear-gradient(180deg,#3b2a8f,var(--g))"></div>
{status()}
<div class="pad row" style="justify-content:space-between;position:relative"><div class="iconbtn">{ic("back", 24, 2.4)}</div><div class="iconbtn">{ic("more", 22)}</div></div>
<div style="display:flex;justify-content:center;position:relative;margin-top:4px">{art(2, 220, 18, "box-shadow:0 24px 50px rgba(0,0,0,.5)")}</div>
<div class="pad" style="position:relative;margin-top:18px"><div style="font-size:28px;font-weight:800;letter-spacing:-.5px">Paper Moons</div>
 <div class="row" style="gap:8px;margin-top:6px"><div style="width:22px;height:22px;border-radius:11px;background:{ART[4]}"></div><span class="t" style="font-size:14px">Vela</span><span class="sub">· Album · 2026 · 11 songs</span></div>
 <div class="row" style="justify-content:space-between;margin-top:14px"><div class="row" style="gap:2px">{src(YT, "YT Music")}<div class="iconbtn" style="margin-left:10px">{ic("heart", 22)}</div><div class="iconbtn">{ic("down", 22, 2.2)}</div></div>
  <div class="row" style="gap:10px"><div class="iconbtn fill">{ic("shuffle", 20, 2.2)}</div><div class="iconbtn" style="background:var(--a);color:var(--oa);width:56px;height:56px;border-radius:28px">{ic("play", 26)}</div></div></div>
</div>
<div class="pad" style="margin-top:6px">{"".join(f'<div class="row" style="height:56px;gap:16px"><span class="sub" style="width:16px;text-align:right">{i+1}</span><div class="col" style="flex:1;gap:2px"><div class="t" style="{"color:var(--a)" if i==0 else ""}">{t}</div><div class="sub">Vela</div></div><span class="sub">{d}</span></div>' for i, (t, d) in enumerate(alb))}</div>
<div class="fade"></div>{mini("Glasshouse", "Vela", bottom=28)}
""")

# ---------- 14 Artist page ----------
page("14-artist", f"""
<div style="position:absolute;inset:0 0 auto 0;height:380px;background:{ART[0]}"></div>
<div style="position:absolute;inset:0 0 auto 0;height:380px;background:linear-gradient(180deg,rgba(0,0,0,.1),var(--g))"></div>
{status()}
<div class="pad row" style="justify-content:space-between;position:relative"><div class="iconbtn" style="background:rgba(0,0,0,.3)">{ic("back", 24, 2.4)}</div><div class="iconbtn" style="background:rgba(0,0,0,.3)">{ic("more", 22)}</div></div>
<div class="pad" style="position:relative;margin-top:150px"><div class="label" style="color:rgba(255,255,255,.75)">Artist · 1.2M monthly listeners</div><div style="font-size:44px;font-weight:800;letter-spacing:-1.2px;line-height:1.05">Marlowe<br>Finch</div>
 <div class="row" style="justify-content:space-between;margin-top:14px"><div class="row" style="gap:8px"><span class="chip line">Follow</span><span class="chip">{ic("radio", 16, 2.2)}Radio</span></div><div class="iconbtn" style="background:var(--a);color:var(--oa);width:56px;height:56px;border-radius:28px">{ic("play", 26)}</div></div></div>
<div class="pad" style="margin-top:14px"><div class="h2">Popular</div>{"".join(trow(i+5, t, s, c, f'<span class="sub">{p}</span>') for i, (t, s, c, p) in enumerate([("Teeth", "38M plays", YT, "3:20"), ("Glass Jaw", "21M plays", SP, "3:02"), ("Hollow Bell", "9.4M plays", SP, "4:15")]))}</div>
<div class="fade"></div>{mini(bottom=28)}
""")

# ---------- 15 Accounts sheet ----------
def acct(color, name, state, signed):
    right = (f'<span class="chip" style="height:30px;color:var(--a)">{ic("check", 14, 3)}Connected</span>' if signed
             else '<span class="chip on" style="height:32px">Sign in</span>')
    return (f'<div class="card row" style="padding:14px;gap:14px;background:var(--r)"><div style="width:46px;height:46px;border-radius:14px;background:{color};display:flex;align-items:center;justify-content:center;color:var(--oa)">{ic("wave", 24, 2.6)}</div>'
            f'<div class="col" style="flex:1;gap:3px"><div class="t">{name}</div><div class="sub">{state}</div></div>{right}</div>')
page("15-accounts", f"""
{status()}
<div class="pad" style="opacity:.4;margin-top:6px"><div class="h1">Listen now</div></div>
<div class="scrim"></div>
<div class="sheet"><div class="grab"></div>
 <div class="pad col" style="align-items:center;gap:8px;padding:6px 20px 18px"><div style="width:72px;height:72px;border-radius:36px;background:linear-gradient(135deg,var(--a),#5ee0d0);color:var(--oa);display:flex;align-items:center;justify-content:center;font-size:30px;font-weight:800">S</div>
  <div class="h2">Szymon</div><div class="sub">Duet Owner · v0.13.3</div></div>
 <div class="col" style="gap:10px;padding:0 20px">{acct(SP, "Spotify", "szymon · Premium", True)}{acct(YT, "YouTube Music", "Not signed in · search works anyway", False)}
  <div class="card row" style="padding:14px 16px;gap:14px;background:transparent;border:1px dashed var(--l)">{ic("plus", 22, 2.2, "var(--d)")}<span class="t dim" style="flex:1">Add a source</span><span class="sub">Soon</span></div></div>
 <div class="sep" style="margin:18px 20px 4px"></div>
 {"".join(f'<div class="menuitem">{ic(i,22,2.2)}<span style="flex:1">{n}</span>{ic("chev",18,2.2,"var(--d)")}</div>' for i, n in [("gear", "Settings"), ("down", "Downloads & storage"), ("car", "Car mode")])}
</div>
""")

# ---------- 16 Settings ----------
def grp(title, rows):
    body = "".join(f'<div class="row" style="height:58px;padding:0 16px;gap:14px;{"border-top:1px solid rgba(58,62,80,.6)" if k else ""}">'
                   f'<div style="width:34px;height:34px;border-radius:10px;background:{bg};display:flex;align-items:center;justify-content:center;color:var(--oa)">{ic(i, 18, 2.4)}</div>'
                   f'<div class="col" style="flex:1;gap:2px"><div class="t" style="font-size:15px">{n}</div>{f"<div class=sub>{s}</div>" if s else ""}</div>{r}</div>'
                   for k, (i, bg, n, s, r) in enumerate(rows))
    return f'<div class="label" style="margin:20px 4px 8px">{title}</div><div class="card" style="overflow:hidden">{body}</div>'
tog = lambda on: f'<div style="width:46px;height:28px;border-radius:14px;background:{"var(--a)" if on else "var(--l)"};position:relative"><div style="position:absolute;top:3px;{"right" if on else "left"}:3px;width:22px;height:22px;border-radius:11px;background:{"var(--g)" if on else "var(--t)"}"></div></div>'
chev = ic("chev", 18, 2.2, "var(--d)")
page("16-settings", f"""
{status()}
<div class="pad row" style="gap:4px"><div class="iconbtn">{ic("back", 24, 2.4)}</div><div class="h1" style="font-size:26px">Settings</div></div>
<div class="pad">
 {grp("Playback", [("wave", "#c8f26b", "Sound", "Equalizer · Bass boost", chev), ("sleep", "#8f7bff", "Sleep timer", "Off", chev), ("shuffle", "#5ee0d0", "Gapless playback", "", tog(True))])}
 {grp("Downloads", [("down", "#3ecf7a", "Saved songs", "48 songs · 412 MB", chev), ("wifi0", "#ffb84d", "Download on Wi-Fi only", "", tog(True)), ("trash", "#ff5a5a", "Remove all downloads", "Frees 412 MB", chev)])}
 {grp("Look", [("lyrics", "#ff8a5b", "Lyrics button", "", tog(True)), ("sparkle", "#ff6bd5", "Colour from cover art", "", tog(False))])}
</div>
""")

# ---------- 17 Car mode (portrait) ----------
page("17-car-mode", f"""
{status()}
<div class="pad row" style="justify-content:space-between"><div class="row" style="gap:8px;font-weight:800">{ic("car", 22, 2.2)}Car mode</div><span class="chip">Exit</span></div>
<div class="pad" style="margin-top:14px"><div class="card row" style="padding:16px;gap:16px;background:linear-gradient(135deg,#5a1a08,#1c1e27)">{art(6, 96, 16)}<div class="col" style="flex:1;gap:4px;min-width:0"><div style="font-size:24px;font-weight:800">Teeth</div><div style="font-size:17px;color:rgba(255,255,255,.7)">Marlowe Finch</div></div></div>
 <div class="bar" style="margin-top:16px;height:6px"><div class="f" style="width:38%"></div></div>
 {np_controls()}
</div>
<div class="pad" style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:22px">
 {"".join(f'<div style="height:118px;border-radius:20px;background:{bg};padding:16px;display:flex;flex-direction:column;justify-content:space-between;font-size:20px;font-weight:800">{ic(i, 30, 2.4)}{n}</div>' for i, n, bg in [("sparkle", "Quick picks", "#2c3040"), ("radio", "Radio", "#3b2a8f"), ("clock", "Recent", "#1d6e8f"), ("down", "Downloaded", "#1f6e5a")])}
</div>
""")

# ---------- 18 Welcome ----------
page("18-welcome", f"""
<div style="position:absolute;inset:0;background:radial-gradient(80% 50% at 30% 20%,rgba(62,207,122,.35),transparent),radial-gradient(80% 50% at 80% 40%,rgba(255,107,107,.35),transparent)"></div>
{status()}
<div style="position:relative;height:420px;display:flex;align-items:center;justify-content:center">
 <div style="position:absolute;width:190px;height:190px;border-radius:44px;background:{ART[1]};transform:rotate(-12deg) translate(-56px,10px);box-shadow:0 30px 60px rgba(0,0,0,.5)"></div>
 <div style="position:absolute;width:190px;height:190px;border-radius:44px;background:{ART[0]};transform:rotate(10deg) translate(56px,-6px);box-shadow:0 30px 60px rgba(0,0,0,.5)"></div>
 <div style="position:absolute;width:78px;height:78px;border-radius:39px;background:var(--a);color:var(--oa);display:flex;align-items:center;justify-content:center;box-shadow:0 0 0 10px var(--g)">{ic("play", 36)}</div>
</div>
<div class="pad" style="padding:0 28px;position:relative"><div style="font-size:40px;font-weight:800;letter-spacing:-1px;line-height:1.08">Two services.<br>One player.</div>
 <div class="sub" style="white-space:normal;font-size:16px;margin-top:12px;line-height:1.5">Search, queue and play Spotify and YouTube Music together. Sign in now or later.</div>
 <div class="col" style="gap:10px;margin-top:28px"><div class="btn pri">Get started</div><div class="btn ghost">I'll sign in later</div></div></div>
""")

print("ok")
