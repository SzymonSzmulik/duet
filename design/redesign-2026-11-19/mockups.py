"""Duet full-app redesign mockups, 2026-11-19: three plain, non-trendy directions, 13 screens each.

Usage: python3 mockups.py OUT_DIR
  -> OUT_DIR/<direction>.html  (one 390x844 page per screen; print it to PDF with Chrome)
  -> OUT_DIR/overview.html     (all directions side by side, for one screenshot)

The directions take their cues from print and hardware design, not from current app trends:
  A Index  - Swiss/International typographic posters and 1960s-70s Braun manuals: one sans, a grid,
             hairline rules, numbers, black on paper, one signal red.
  B Mono   - text-mode music players and a typewriter: monospace, columns, brackets, amber on black.
  C Sleeve - record sleeve liner notes and newspaper listings: serif headings, small caps labels,
             thin rules, warm off-black.
Song, artist and station names are invented. Covers are flat colour blocks (no real artwork).
"""
import html
import os
import sys
import base64

# Film grain tile (white noise on transparent, 200 px), drawn once with an SVG turbulence filter.
GRAIN_PNG = "data:image/png;base64," + base64.b64encode(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "grain.png"), "rb").read()).decode()

OUT = sys.argv[1]
SET = sys.argv[2] if len(sys.argv) > 2 else "plain"  # plain (A-C) or cinematic (D-F)
os.makedirs(OUT, exist_ok=True)

SONGS = [  # title, artist, album, length, source (S = Spotify, Y = YouTube Music), cover colour
    ("Low Tide Radio", "Marlow Veil", "Coastal Hours", "3:41", "S", "#7A8B6F"),
    ("Paper Planes Over Leeds", "The Quiet Arcade", "North", "4:02", "Y", "#B5583A"),
    ("Second Floor", "Ines Holm", "Rooms", "2:58", "S", "#3E4C6B"),
    ("Neon Is Tired", "Kasper & Lune", "Late Shift", "3:27", "Y", "#C9A227"),
    ("Small Hours", "Marlow Veil", "Coastal Hours", "5:10", "S", "#7A8B6F"),
    ("Cold Brew Summer", "Juno Park", "Juno Park", "3:05", "Y", "#4F8A8B"),
    ("Westbound", "Odile", "Westbound", "3:49", "S", "#8C4A5E"),
    ("Slow Lights", "The Quiet Arcade", "North", "4:21", "Y", "#B5583A"),
]
PLAYLISTS = [  # name, owner/source line, count, colour
    ("Running, mostly", "Duet · 42 songs", "#B5583A"),
    ("Sunday kitchen", "Spotify · 118 songs", "#7A8B6F"),
    ("Liked music", "YouTube Music · 640 songs", "#3E4C6B"),
    ("Drive home", "Duet · 27 songs", "#C9A227"),
    ("Coastal Hours", "Album · Marlow Veil · Spotify", "#7A8B6F"),
    ("Ines Holm", "Artist · Spotify", "#3E4C6B"),
]
STATIONS = [("North Sea Jazz FM", "Netherlands · jazz · 128 kbps"), ("Radio Kestrel", "United Kingdom · indie"),
            ("Onda Lenta", "Spain · ambient · 96 kbps"), ("KXLR Night", "United States · talk")]
SRC = {"S": "Spotify", "Y": "YT Music"}
NOW = SONGS[1]

e = html.escape

ICON = {  # 24px stroked icons
    "play": '<path d="M8 5.5v13l10.5-6.5z" fill="currentColor"/>',
    "pause": '<path d="M7 5h3.2v14H7zM13.8 5H17v14h-3.2z" fill="currentColor" stroke="none"/>',
    "next": '<path d="M6 5.5v13l9-6.5z" fill="currentColor"/><path d="M17.5 5v14"/>',
    "prev": '<path d="M18 5.5v13l-9-6.5z" fill="currentColor"/><path d="M6.5 5v14"/>',
    "shuffle": '<path d="M3 7h3.5a5 5 0 0 1 4 2l3 4a5 5 0 0 0 4 2H21M18 12l3 3-3 3M3 17h3.5a5 5 0 0 0 3-1M14 8a5 5 0 0 1 3-1h4M18 4l3 3-3 3"/>',
    "repeat": '<path d="M17 3l4 4-4 4M3 12v-1a4 4 0 0 1 4-4h14M7 21l-4-4 4-4M21 12v1a4 4 0 0 1-4 4H3"/>',
    "heart": '<path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"/>',
    "lyrics": '<path d="M4 5h16v11H9l-5 4z"/><path d="M8 9h8M8 12h5"/>',
    "queue": '<path d="M3 6h13M3 11h13M3 16h8M18 13v7M18 13l3-1"/>',
    "sleep": '<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>',
    "down": '<path d="m6 9 6 6 6-6"/>',
    "more": '<circle cx="5" cy="12" r="1.5" fill="currentColor"/><circle cx="12" cy="12" r="1.5" fill="currentColor"/><circle cx="19" cy="12" r="1.5" fill="currentColor"/>',
    "back": '<path d="m15 5-7 7 7 7"/>',
    "cast": '<path d="M3 17a4 4 0 0 1 4 4M3 13a8 8 0 0 1 8 8M3 9V5h18v14h-6"/>',
}


def ic(name, size=24):
    return (f'<svg class="ic" width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
            f'stroke-width="2" stroke-linecap="round" stroke-linejoin="round">{ICON[name]}</svg>')



def cover(color, cls="art"):
    return f'<span class="{cls}" style="background:{color}"></span>'


def src(code):
    return f'<span class="src src-{code}">{SRC[code]}</span>'


def track_rows(songs, numbered=True, playing=None):
    out = []
    for i, (t, a, _alb, d, s, c) in enumerate(songs, 1):
        on = " on" if t == playing else ""
        out.append(
            f'<div class="row{on}">{f"<span class=n>{i:02d}</span>" if numbered else ""}{cover(c)}'
            f'<span class="tx"><b>{e(t)}</b><i>{e(a)} · {src(s)}</i></span><span class="d">{d}</span></div>'
        )
    return "".join(out)


def header(title, sub="", right=""):
    return (f'<div class="hd"><div class="hdt"><h1>{e(title)}</h1>{f"<p>{e(sub)}</p>" if sub else ""}</div>'
            f'{f"<div class=hdr>{right}</div>" if right else ""}</div>')


def tabs(active):
    names = ["Home", "Search", "Library", "Radio"]
    return '<nav class="tabs">' + "".join(f'<span class="tab{" act" if n == active else ""}">{n}</span>' for n in names) + "</nav>"


def mini():
    t, a, _, _, s, c = NOW
    return (f'<div class="mini">{cover(c, "mart")}<span class="tx"><b>{e(t)}</b><i>{e(a)}</i></span>'
            f'<span class="mctl">{ic("pause", 26)}</span></div><div class="mbar"><span style="width:38%"></span></div>')


def label(t):
    return f'<div class="lbl">{e(t)}</div>'


COLOR = {"Home": "#B5583A", "Now playing": "#B5583A", "Lyrics": "#B5583A", "Queue": "#B5583A", "Playlist": "#B5583A",
         "Search": "#4F8A8B", "Search, empty": "#3E4C6B", "Library": "#3E4C6B", "Song menu": "#C9A227", "Radio": "#7A8B6F"}


def page(name, body, nav=None, chrome=True):
    bottom = (mini() + tabs(nav)) if (chrome and nav) else ""
    return f'<section class="phone{" nav" if bottom else ""}" data-name="{e(name)}" style="--c:{COLOR.get(name, "#3A3733")}"><div class="status"><span>9:41</span><span>5G ▮▮▮</span></div><div class="body">{body}</div>{bottom}</section>'


# ---- screens -------------------------------------------------------------------------------------

def s_home():
    picks = track_rows(SONGS[:5], numbered=True)
    grid = "".join(f'<div class="tile">{cover(c, "tart")}<b>{e(n)}</b><i>{e(o)}</i></div>' for n, o, _, c in [(p[0], p[1], 0, p[2]) for p in PLAYLISTS[:4]])
    body = (header("Duet", "Tuesday evening", '<span class="btn">Settings</span>') +
            '<div class="seg"><span class="act">All</span><span>Spotify</span><span>YT Music</span></div>' +
            label("Continue") + f'<div class="tiles">{grid}</div>' +
            label("Quick picks") + picks)
    return page("Home", body, "Home")


def s_search():
    body = (header("Search") +
            '<div class="field"><span>paper planes</span><span class="clr">Clear</span></div>' +
            '<div class="seg"><span class="act">Songs</span><span>Albums</span><span>Artists</span><span>Playlists</span><span>Videos</span></div>' +
            label("Top result") +
            f'<div class="top">{cover("#B5583A", "bigart")}<div><b>Paper Planes Over Leeds</b><i>Song · The Quiet Arcade</i><span class="btn">Play</span></div></div>' +
            label("Songs · both services") + track_rows([SONGS[1], SONGS[7], SONGS[3], SONGS[5]], numbered=False))
    return page("Search", body, "Search")


def s_library():
    rows = "".join(f'<div class="row">{cover(c)}<span class="tx"><b>{e(n)}</b><i>{e(o)}</i></span><span class="d">›</span></div>' for n, o, c in PLAYLISTS)
    body = (header("Library", "", '<span class="btn">Sync</span>') +
            '<div class="seg"><span class="act">All</span><span>Duet</span><span>Spotify</span><span>YT Music</span></div>' +
            '<div class="stack">'
            '<div class="row plain"><span class="tx"><b>Liked songs</b><i>214 songs</i></span><span class="d">›</span></div>'
            '<div class="row plain"><span class="tx"><b>Downloaded</b><i>38 songs · 412 MB</i></span><span class="d">›</span></div>'
            '<div class="row plain"><span class="tx"><b>History</b><i>Everything you played</i></span><span class="d">›</span></div>'
            '<div class="row plain"><span class="tx"><b>Your stats</b><i>Most played songs and artists</i></span><span class="d">›</span></div></div>' +
            label("Playlists, albums, artists") + '<div class="row plain add"><span class="tx"><b>+ New playlist</b></span></div>' + rows)
    return page("Library", body, "Library")


def s_playlist():
    body = (header("Running, mostly", "Duet playlist · 42 songs · 2 h 51 min") +
            '<div class="actions"><span class="btn pri">Play</span><span class="btn">Shuffle</span><span class="btn">Add songs</span><span class="btn">Edit</span></div>' +
            track_rows(SONGS, numbered=True, playing=NOW[0]))
    return page("Playlist", body, "Library")


def s_now():
    t, a, alb, d, s, c = NOW
    body = ('<div class="npbar"><span>' + ic("down", 26) + '</span><span>Playing from Running, mostly</span><span>' + ic("more", 26) + '</span></div>' +
            f'<div class="npart" style="background:{c}"></div>' +
            f'<div class="npmeta"><div><h2>{e(t)}</h2><p>{e(a)} — {e(alb)}</p></div><span class="like">{ic("heart", 26)}</span></div>' +
            '<div class="scrub"><span style="width:38%"></span></div><div class="times"><span>1:32</span><span>4:02</span></div>' +
            '<div class="ctl"><span>' + ic("shuffle", 22) + '</span><span>' + ic("prev", 30) + '</span><span class="big">' + ic("pause", 32) + '</span><span>' + ic("next", 30) + '</span><span>' + ic("repeat", 22) + '</span></div>' +
            f'<div class="npfoot"><span>{src(s)} Opus · 160 kbps</span><span>Phone speaker</span></div>' +
            '<div class="nplinks"><span>' + ic("lyrics", 22) + '</span><span>' + ic("queue", 22) + '</span><span>' + ic("cast", 22) + '</span><span>' + ic("sleep", 22) + '</span></div>')
    return page("Now playing", body, chrome=False)


def s_lyrics():
    lines = ["We folded the timetable into wings", "and threw it off the station roof", "it went further than the trains did",
             "further than we said we would", "", "Paper planes over Leeds", "nobody looking up", "paper planes over Leeds",
             "and the rain can't keep up"]
    body = ('<div class="npbar"><span>Back</span><span>Lyrics · synced</span><span>Size</span></div><div class="lyr">' +
            "".join(f'<p class="{"cur" if i == 5 else ("past" if i < 5 else "")}">{e(l) or "&nbsp;"}</p>' for i, l in enumerate(lines)) +
            '</div><div class="lyrfoot">Paper Planes Over Leeds · The Quiet Arcade<br>Lyrics from LRCLIB</div>')
    return page("Lyrics", body, chrome=False)


def s_queue():
    body = (header("Queue", "Up next · 6 songs", '<span class="btn">Clear</span>') +
            label("Now playing") + track_rows([NOW], numbered=False, playing=NOW[0]) +
            label("Next") + track_rows(SONGS[2:8], numbered=True) )
    return page("Queue", body, "Library")


def s_menu():
    t, a, _, _, s, c = SONGS[3]
    items = ["Play next", "Add to queue", "Add to playlist", "Like", "Go to artist", "Start mix", "Download", "Share"]
    body = (track_rows(SONGS[:6], numbered=True) + '<div class="scrim"></div><div class="sheet">' +
            f'<div class="row plain">{cover(c)}<span class="tx"><b>{e(t)}</b><i>{e(a)} · {src(s)}</i></span></div>' +
            "".join(f'<div class="mi">{x}</div>' for x in items) + '</div>')
    return page("Song menu", body)


def s_radio():
    rows = "".join(f'<div class="row plain"><span class="tx"><b>{e(n)}</b><i>{e(d)}</i></span><span class="d">Play</span></div>' for n, d in STATIONS)
    body = (header("Radio", "Live stations · United Kingdom", '<span class="btn">Region</span>') +
            '<div class="field"><span class="ph">Find a station</span></div>' +
            label("Favourites") + '<div class="row plain on"><span class="tx"><b>Radio Kestrel</b><i>Playing · indie</i></span><span class="d">Stop</span></div>' +
            label("Popular here") + rows +
            label("Genres") + '<div class="chips"><span>Jazz</span><span>News</span><span>Indie</span><span>Classical</span><span>Ambient</span><span>Talk</span></div>')
    return page("Radio", body, "Radio")


def s_settings():
    groups = [("Interface", ["Appearance", "Home page"]), ("Player and audio", ["Playback", "Sound", "Car mode"]),
              ("Accounts", ["Music services"]), ("Storage", ["Downloads and storage"]), ("Duet", ["Updates", "About"])]
    body = header("Settings")
    for g, items in groups:
        body += label(g) + "".join(f'<div class="row plain"><span class="tx"><b>{x}</b></span><span class="d">›</span></div>' for x in items)
    return page("Settings", body)


def s_sound():
    def sw(t, d, on):
        return f'<div class="row plain"><span class="tx"><b>{t}</b><i>{d}</i></span><span class="sw{" on" if on else ""}">{"On" if on else "Off"}</span></div>'

    def opt(t, opts, sel):
        return f'<div class="opt"><b>{t}</b><div class="seg">' + "".join(f'<span class="{"act" if o == sel else ""}">{o}</span>' for o in opts) + '</div></div>'
    body = (header("Sound", "", '<span class="btn">Back</span>') +
            label("Quality · YouTube Music") + opt("Wi-Fi", ["High", "Normal", "Data saver"], "High") + opt("Mobile data", ["High", "Normal", "Data saver"], "Data saver") +
            label("Spotify songs") + sw("Play from YouTube Music", "Quality and effects then work on them", False) +
            label("Effects · YouTube Music and radio") + sw("Equalizer", "Flat", False) +
            '<div class="eq">' + "".join(f'<span><i style="height:{h}%"></i><em>{l}</em></span>' for h, l in [(50, "60"), (62, "230"), (48, "910"), (55, "3.6k"), (58, "14k")]) + '</div>' +
            opt("Speed", ["0.75", "1", "1.25", "1.5", "2"], "1") +
            sw("Crossfade", "Between YouTube Music songs", False))
    return page("Settings / Sound", body)


def s_accounts():
    body = (header("Music services", "", '<span class="btn">Back</span>') +
            '<div class="acct"><b>Spotify</b><i>Signed in as szy · Premium</i><span class="btn">Sign out</span></div>' +
            '<div class="acct"><b>YouTube Music</b><i>Not signed in · optional. Search and playback work without it.</i><span class="btn pri">Sign in</span></div>' +
            '<div class="acct"><b>Radio</b><i>No account needed</i></div>' +
            label("What each one does") +
            '<p class="note">Spotify plays in Spotify\'s own player. YouTube Music plays in Duet, so quality, equalizer and downloads work on it.</p>')
    return page("Settings / Accounts", body)


def s_empty():
    body = (header("Search") + '<div class="field"><span class="ph">Songs, albums, artists</span></div>' +
            label("Recent") + "".join(f'<div class="row plain"><span class="tx"><b>{q}</b></span><span class="d">×</span></div>' for q in ["marlow veil", "late shift", "kasper & lune", "running"]) +
            label("Browse") + '<div class="chips"><span>New releases</span><span>Charts</span><span>Moods</span><span>Videos</span></div>')
    return page("Search, empty", body, "Search")


SCREENS = [s_home, s_now, s_search, s_empty, s_library, s_playlist, s_queue, s_lyrics, s_menu, s_radio, s_settings, s_sound, s_accounts]

# ---- styles --------------------------------------------------------------------------------------

BASE = """
*{box-sizing:border-box;margin:0;padding:0}
@page{size:390px 844px;margin:0}
body{background:#888}
.phone{width:390px;height:844px;position:relative;overflow:hidden;page-break-after:always;background:var(--bg);color:var(--fg);font-family:var(--sans)}
.status{height:44px;display:flex;justify-content:space-between;align-items:flex-end;padding:0 24px 6px;font-size:13px;font-weight:600}
.body{padding:0 var(--gut) 160px;height:800px;overflow:hidden}
.phone.nav .body{height:656px;padding-bottom:0}
.hd{display:flex;align-items:flex-end;justify-content:space-between;padding:18px 0 14px}
h1{font:var(--h1)}
.hd p{color:var(--dim);font-size:13px;margin-top:4px}
.btn{font-size:13px;padding:7px 12px;border:1px solid var(--line);display:inline-block}
.btn.pri{background:var(--fg);color:var(--bg);border-color:var(--fg)}
.lbl{font:var(--lbl);color:var(--dim);padding:18px 0 8px;letter-spacing:var(--lblsp);text-transform:var(--lbltt)}
.row{display:flex;align-items:center;gap:12px;padding:9px 0;border-bottom:1px solid var(--rule)}
.row .n{width:22px;color:var(--dim);font:var(--num)}
.art{width:40px;height:40px;flex:none;border-radius:var(--r)}
.row.plain{min-height:52px}
.tx{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}
.tx b{font:var(--title);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.tx i{font-style:normal;font-size:12.5px;color:var(--dim);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.d{color:var(--dim);font:var(--num);font-size:15px}
.row.on .tx b,.row.on .n{color:var(--acc)}
.src{font-size:11px}
.seg{display:flex;gap:0;border:1px solid var(--line);margin:6px 0 4px}
.seg span{flex:1;text-align:center;font-size:12.5px;padding:7px 0;border-right:1px solid var(--line)}
.seg span:last-child{border-right:0}
.seg .act{background:var(--fg);color:var(--bg)}
.tiles{display:grid;grid-template-columns:1fr 1fr;gap:12px}
.tile b{display:block;font:var(--title);margin-top:6px}
.tile i{font-style:normal;font-size:12px;color:var(--dim)}
.tart{display:block;width:100%;aspect-ratio:1/1;border-radius:var(--r)}
.tiles .tile:nth-child(n+3) .tart{aspect-ratio:2/1}
.tabs{position:absolute;left:0;right:0;bottom:0;height:64px;display:flex;border-top:1px solid var(--line);background:var(--bg)}
.tab{flex:1;text-align:center;padding-top:14px;font-size:13px;color:var(--dim)}
.tab.act{color:var(--fg);font-weight:700}
.mini{position:absolute;left:0;right:0;bottom:64px;height:58px;display:flex;align-items:center;gap:12px;padding:0 var(--gut);background:var(--minibg);color:var(--minifg)}
.mini .tx i{color:var(--minidim)}
.mart{width:38px;height:38px;border-radius:var(--r)}
.mctl{font-size:13px;font-weight:700;display:flex}
.ic{display:block}
.npbar span{display:flex;align-items:center}
.like{display:flex}
.ctl span{display:flex;align-items:center;justify-content:center}
.nplinks span{display:flex;justify-content:center}
.mbar{position:absolute;left:0;right:0;bottom:122px;height:2px;background:var(--rule)}
.mbar span,.scrub span{display:block;height:100%;background:var(--acc)}
.field{display:flex;justify-content:space-between;border-bottom:2px solid var(--fg);padding:10px 0;font-size:17px;margin-bottom:8px}
.field .ph{color:var(--dim)} .clr{font-size:13px;color:var(--dim)}
.top{display:flex;gap:14px;align-items:center;padding:6px 0 10px}
.bigart{width:96px;height:96px;border-radius:var(--r)}
.top b{display:block;font:var(--title);font-size:17px}.top i{display:block;font-style:normal;color:var(--dim);font-size:13px;margin:3px 0 10px}
.stack{border-top:1px solid var(--rule);margin-top:6px}
.actions{display:flex;gap:8px;padding:2px 0 12px;flex-wrap:wrap}
.npbar{display:flex;justify-content:space-between;font-size:13px;color:var(--dim);padding:14px 0 18px}
.npbar span:nth-child(2){color:var(--fg)}
.npart{width:100%;aspect-ratio:1/1;border-radius:var(--r)}
.npmeta{display:flex;justify-content:space-between;align-items:flex-end;padding:22px 0 18px}
.npmeta h2{font:var(--h2)} .npmeta p{color:var(--dim);font-size:14px;margin-top:4px}
.like{font-size:13px;border:1px solid var(--line);padding:6px 10px}
.scrub{height:3px;background:var(--rule)}
.times{display:flex;justify-content:space-between;font:var(--num);color:var(--dim);padding:8px 0 22px}
.ctl{display:flex;justify-content:space-between;align-items:center;font-size:13px}
.ctl .big{font-size:15px;font-weight:700;padding:16px 22px;background:var(--fg);color:var(--bg)}
.npfoot{display:flex;justify-content:space-between;font-size:12px;color:var(--dim);padding:28px 0 0}
.nplinks{display:flex;gap:0;margin-top:18px;border-top:1px solid var(--rule)}
.nplinks span{flex:1;text-align:center;padding:14px 0;font-size:13px;border-right:1px solid var(--rule)}
.nplinks span:last-child{border-right:0}
.lyr{padding-top:20px;max-height:560px;overflow:hidden}
.lyr p{font:var(--lyr);color:var(--dim);margin-bottom:14px;opacity:.55}
.lyr p.past{opacity:.35}.lyr p.cur{color:var(--fg);opacity:1}
.lyrfoot{position:absolute;bottom:34px;left:var(--gut);font-size:12px;color:var(--dim);line-height:1.6}
.scrim{position:absolute;inset:0;background:rgba(0,0,0,.45)}
.sheet{position:absolute;left:0;right:0;bottom:0;background:var(--bg);padding:8px var(--gut) 30px;border-top:1px solid var(--line)}
.mi{padding:14px 0;font-size:15px;border-bottom:1px solid var(--rule)}
.chips{display:flex;flex-wrap:wrap;gap:8px}
.chips span{font-size:13px;padding:7px 12px;border:1px solid var(--line)}
.sw{font-size:12px;padding:5px 10px;border:1px solid var(--line);color:var(--dim)}
.sw.on{background:var(--fg);color:var(--bg)}
.opt{padding:8px 0}.opt b{font:var(--title);display:block;margin-bottom:4px}
.eq{display:flex;justify-content:space-between;height:110px;padding:10px 8px 0;border-bottom:1px solid var(--rule)}
.eq span{position:relative;width:34px;display:flex;flex-direction:column;justify-content:flex-end;align-items:center}
.eq i{display:block;width:2px;background:var(--fg)}
.eq em{font-style:normal;font-size:11px;color:var(--dim);margin-top:6px}
.acct{padding:16px 0;border-bottom:1px solid var(--rule)}
.acct b{font:var(--title);font-size:17px;display:block}.acct i{display:block;font-style:normal;color:var(--dim);font-size:13px;margin:4px 0 10px}
.note{font-size:13.5px;line-height:1.55;color:var(--dim)}
"""

STYLES = {
    "A-Index": ("Index", "Swiss typographic: one sans, a grid, numbers, hairlines. Black on paper, red only for what's playing.", """
:root{--bg:#F2F1EC;--fg:#111;--dim:#6B6A66;--line:#111;--rule:rgba(17,17,17,.14);--acc:#E0301E;--gut:20px;--r:0;
--sans:'Helvetica Neue',Helvetica,Arial,sans-serif;--h1:700 38px/1 var(--sans);--h2:700 26px/1.1 var(--sans);
--title:500 15px/1.25 var(--sans);--num:400 12px/1 var(--sans);--lbl:700 11px/1 var(--sans);--lblsp:.08em;--lbltt:uppercase;
--lyr:700 26px/1.2 var(--sans);--minibg:#111;--minifg:#F2F1EC;--minidim:#9A9994}
h1{letter-spacing:-.03em}.npmeta h2{letter-spacing:-.02em}
.tab.act{color:var(--acc)}.ctl .big{background:var(--acc);color:#fff}
.src-S::before,.src-Y::before{content:'';display:inline-block;width:6px;height:6px;margin-right:4px;vertical-align:1px}
.src-S::before{background:#111}.src-Y::before{background:var(--acc)}
"""),
    "B-Mono": ("Mono", "Text-mode player: monospace, columns, brackets. Amber on black, no decoration at all.", """
:root{--bg:#0B0B0A;--fg:#E9E4D8;--dim:#7C786E;--line:#3A3833;--rule:#1F1E1B;--acc:#FFB000;--gut:18px;--r:0;
--sans:Menlo,'SF Mono',monospace;--h1:700 22px/1 var(--sans);--h2:700 20px/1.2 var(--sans);
--title:400 13.5px/1.3 var(--sans);--num:400 12px/1 var(--sans);--lbl:400 12px/1 var(--sans);--lblsp:0;--lbltt:lowercase;
--lyr:400 18px/1.35 var(--sans);--minibg:#16150F;--minifg:#E9E4D8;--minidim:#7C786E}
.phone{font-size:13px}
h1::before{content:'~/';color:var(--dim)} h1{text-transform:lowercase}
.lbl::before{content:'── ';}.lbl::after{content:' ──────────────';color:var(--line)}
.art,.mart,.tart,.bigart{display:none}.npart{aspect-ratio:3/1;background:repeating-linear-gradient(90deg,var(--rule) 0 6px,transparent 6px 8px)!important;border:1px solid var(--line)}
.tile{border:1px solid var(--line);padding:10px}.tiles .tile b{margin-top:0}
.btn::before{content:'['}.btn::after{content:']'}.btn{border:0;padding:6px 2px;color:var(--acc)}.btn.pri{background:none;color:var(--acc);font-weight:700}
.seg{border:0;gap:12px}.seg span{flex:none;border:0;padding:4px 0;color:var(--dim)}.seg .act{background:none;color:var(--acc)}.seg .act::before{content:'['}.seg .act::after{content:']'}
.tab.act{color:var(--acc);font-weight:400}.tab.act::before{content:'['}.tab.act::after{content:']'}
.row.on{background:#17150D}.row.on .tx b::before{content:'▶ ';}
.src{color:var(--dim)}.src-S::before{content:'sp:'}.src-Y::before{content:'yt:'}.src{font-size:0}.src::before{font-size:12px}
.ctl .big{background:none;color:var(--acc);border:1px solid var(--acc)}
.sw{border:0;color:var(--dim)}.sw::before{content:'['}.sw::after{content:']'}.sw.on{background:none;color:var(--acc)}
.chips span{border:0;padding:4px 0;color:var(--dim)}.chips span::before{content:'#'}
.field{border-bottom:1px solid var(--acc);font-size:15px}.field::before{content:'> ';color:var(--acc)}
.like,.mi{font-size:13.5px}.like{border:0;color:var(--acc)}
.eq i{background:var(--acc)}
.lyr p.cur{color:var(--acc)}
"""),
    "C-Sleeve": ("Sleeve", "Record-sleeve liner notes: serif headings, small-caps labels, thin rules, warm off-black.", """
:root{--bg:#161412;--fg:#EDE6DA;--dim:#8F877B;--line:#5A534A;--rule:#2B2723;--acc:#D9B26F;--gut:22px;--r:2px;
--sans:'Helvetica Neue',Helvetica,sans-serif;--serif:'New York','Iowan Old Style',Georgia,serif;
--h1:400 36px/1.05 var(--serif);--h2:400 28px/1.1 var(--serif);--title:400 15.5px/1.3 var(--serif);
--num:400 12px/1 var(--sans);--lbl:600 10.5px/1 var(--sans);--lblsp:.18em;--lbltt:uppercase;
--lyr:400 25px/1.3 var(--serif);--minibg:#211E1A;--minifg:#EDE6DA;--minidim:#8F877B}
h1{font-style:normal}.hd{border-bottom:1px solid var(--line);margin-bottom:4px}
.row .n{font-family:var(--serif);font-style:italic}
.btn{border-radius:20px;border-color:var(--line)}.btn.pri{background:var(--acc);border-color:var(--acc);color:#161412}
.seg{border:0;gap:18px;border-bottom:1px solid var(--rule)}.seg span{flex:none;border:0;padding:8px 0;color:var(--dim)}.seg .act{background:none;color:var(--fg);box-shadow:inset 0 -2px 0 var(--acc)}
.tab{font-family:var(--serif);font-size:15px}.tab.act{font-weight:400;color:var(--acc)}
.ctl .big{border-radius:50%;width:72px;height:72px;padding:0;display:flex;align-items:center;justify-content:center;background:var(--acc);color:#161412}
.src{font-family:var(--sans);font-size:10px;letter-spacing:.12em;text-transform:uppercase}
.chips span,.sw,.like{border-radius:20px;border-color:var(--line)}.sw.on{background:var(--acc);border-color:var(--acc);color:#161412}
.lyr p.cur{color:var(--acc)}
.field{border-bottom:1px solid var(--line);font-family:var(--serif);font-size:19px}
.mini{border-top:1px solid var(--line)}
.tile b{font-size:15px}
"""),
    "D-Marquee": ("Marquee", "Film poster: heavy condensed capitals, each screen washed in its cover's colour fading to black, a little film grain.", """
@font-face{font-family:Cond;src:local("Futura-CondensedExtraBold"),local("Futura Condensed ExtraBold")}
:root{--bg:#0A0908;--fg:#F4EFE6;--dim:rgba(244,239,230,.58);--line:rgba(244,239,230,.35);--rule:rgba(244,239,230,.10);--acc:#F4EFE6;--gut:20px;--r:3px;
--sans:'Helvetica Neue',Helvetica,sans-serif;--h1:400 52px/.9 Cond,'Helvetica Neue';--h2:400 40px/.95 Cond,'Helvetica Neue';
--title:600 15px/1.25 var(--sans);--num:500 12px/1 var(--sans);--lbl:400 15px/1 Cond,'Helvetica Neue';--lblsp:.06em;--lbltt:uppercase;
--lyr:400 30px/1.05 Cond,'Helvetica Neue';--minibg:rgba(10,9,8,.92);--minifg:#F4EFE6;--minidim:rgba(244,239,230,.55)}
.phone{background:linear-gradient(180deg,color-mix(in srgb,var(--c) 92%,#000) 0%,color-mix(in srgb,var(--c) 45%,#0A0908) 32%,#0A0908 68%)}
h1,.npmeta h2{text-transform:uppercase}
.seg{border:0;gap:6px}.seg span{flex:none;border:0;padding:7px 14px;border-radius:30px;background:rgba(255,255,255,.08)}.seg .act{background:var(--fg);color:#0A0908}
.btn{border-radius:30px;border-color:var(--line)}.btn.pri{background:var(--fg);color:#0A0908}
.tabs{background:#0A0908;border-top:0}.tab{font:400 16px Cond,'Helvetica Neue';text-transform:uppercase;letter-spacing:.06em}.tab.act{font-weight:400;color:var(--fg)}
.ctl .big{border-radius:50%;width:76px;height:76px;padding:0;display:flex;align-items:center;justify-content:center;background:var(--fg);color:#0A0908}
.chips span,.sw,.like{border-radius:30px}.sw.on{background:var(--fg);color:#0A0908}
.row.on .tx b{color:var(--fg)}.row.on{box-shadow:inset 3px 0 0 var(--fg);padding-left:10px}
.lyr p.cur{color:var(--fg)}.lyr p{text-transform:uppercase}
.field{border-bottom:2px solid var(--fg)}
.sheet{background:#141210;border-top:0;border-radius:14px 14px 0 0}
.nplinks{border-top:0}.nplinks span{border-right:0;background:rgba(255,255,255,.06);margin:0 4px;border-radius:30px;padding:11px 0}
.src{opacity:.8}

.phone::after{content:'';position:absolute;inset:0;pointer-events:none;background-image:url(GRAIN_PNG);opacity:.035;z-index:5}
.tabs,.mini,.sheet{z-index:4}.scrim{z-index:3}
.npart{box-shadow:0 30px 60px rgba(0,0,0,.55)}
.mbar{z-index:4}
"""),
    "E-Dusk": ("Dusk", "Warm, wide gradients like a late-evening sky: the cover's colour rises from the bottom, big bold sans titles, round soft controls.", """
:root{--bg:#0C0B0D;--fg:#FFFFFF;--dim:rgba(255,255,255,.6);--line:rgba(255,255,255,.28);--rule:rgba(255,255,255,.08);--acc:#FFFFFF;--gut:22px;--r:12px;
--sans:'Helvetica Neue',Helvetica,sans-serif;--h1:800 40px/1 var(--sans);--h2:800 30px/1.05 var(--sans);
--title:600 15.5px/1.25 var(--sans);--num:500 12px/1 var(--sans);--lbl:700 17px/1 var(--sans);--lblsp:-.01em;--lbltt:none;
--lyr:800 28px/1.18 var(--sans);--minibg:color-mix(in srgb,var(--c) 30%,#1B1A1C);--minifg:#fff;--minidim:rgba(255,255,255,.6)}
.phone{background:radial-gradient(120% 70% at 30% 100%,color-mix(in srgb,var(--c) 85%,#000) 0%,color-mix(in srgb,var(--c) 35%,#0C0B0D) 45%,#0C0B0D 80%),#0C0B0D}
h1{letter-spacing:-.035em}.npmeta h2{letter-spacing:-.025em}
.lbl{padding:24px 0 10px}
.row{border-bottom:0;padding:8px 0}
.seg{border:0;gap:8px}.seg span{flex:none;border:0;padding:8px 15px;border-radius:30px;background:rgba(255,255,255,.08)}.seg .act{background:#fff;color:#0C0B0D}
.btn{border:0;border-radius:30px;background:rgba(255,255,255,.12)}.btn.pri{background:#fff;color:#0C0B0D}
.tabs{background:#0C0B0D;border-top:0}.tab.act{color:#fff}
.mini{left:12px;right:12px;bottom:72px;border-radius:16px;height:60px}.mbar{display:none}
.ctl .big{border-radius:50%;width:78px;height:78px;padding:0;display:flex;align-items:center;justify-content:center;background:#fff;color:#0C0B0D}
.scrub{border-radius:3px;height:4px}.scrub span{border-radius:3px}
.chips span,.sw,.like{border:0;border-radius:30px;background:rgba(255,255,255,.1)}.sw.on{background:#fff;color:#0C0B0D}
.row.on .tx b{color:#fff}.row.on .n{color:#fff}
.lyr p.cur{color:#fff}
.field{border:0;background:rgba(255,255,255,.1);border-radius:14px;padding:13px 16px}
.sheet{border:0;border-radius:22px 22px 0 0;background:#1A1719}
.mi{border-bottom:0}
.stack{border-top:0}.nplinks{border-top:0}.nplinks span{border-right:0}
.npart{border-radius:16px}

.phone::after{content:'';position:absolute;inset:0;pointer-events:none;background-image:url(GRAIN_PNG);opacity:.035;z-index:5}
.tabs,.mini,.sheet{z-index:4}.scrim{z-index:3}
.npart{box-shadow:0 30px 60px rgba(0,0,0,.55)}
.mbar{z-index:4}
"""),
    "F-Title": ("Title card", "Opening credits: a heavy serif title over a soft glow of the cover's colour, black frame, wide spacing, almost nothing else.", """
:root{--bg:#050505;--fg:#F2F0EB;--dim:rgba(242,240,235,.5);--line:rgba(242,240,235,.3);--rule:rgba(242,240,235,.09);--acc:#F2F0EB;--gut:24px;--r:0;
--sans:'Helvetica Neue',Helvetica,sans-serif;--serif:'New York','Iowan Old Style',Georgia,serif;
--h1:800 42px/1 var(--serif);--h2:800 30px/1.08 var(--serif);--title:500 15px/1.3 var(--sans);--num:400 11.5px/1 var(--sans);
--lbl:500 10.5px/1 var(--sans);--lblsp:.32em;--lbltt:uppercase;--lyr:700 27px/1.25 var(--serif);
--minibg:#050505;--minifg:#F2F0EB;--minidim:rgba(242,240,235,.5)}
.phone{background:radial-gradient(90% 45% at 50% 18%,color-mix(in srgb,var(--c) 70%,#000) 0%,color-mix(in srgb,var(--c) 22%,#050505) 55%,#050505 100%)}
.hd{padding-top:30px}
.lbl{padding:26px 0 10px}
.row{border-bottom:1px solid var(--rule)}
.seg{border:0;gap:20px;justify-content:flex-start}.seg span{flex:none;border:0;padding:6px 0;color:var(--dim);font-size:11px;letter-spacing:.2em;text-transform:uppercase}.seg .act{background:none;color:var(--fg)}
.btn{font-size:10.5px;letter-spacing:.22em;text-transform:uppercase;border-color:var(--line)}.btn.pri{background:var(--fg);color:#050505}
.tabs{border-top:1px solid var(--rule)}.tab{font-size:10.5px;letter-spacing:.24em;text-transform:uppercase;padding-top:22px}.tab.act{font-weight:500;color:var(--fg)}
.mini{border-top:1px solid var(--rule)}.mbar{background:transparent}
.npart{width:calc(100% + 48px);margin-left:-24px;aspect-ratio:16/11;box-shadow:none}
.npmeta{flex-direction:column;align-items:flex-start;gap:12px;text-align:left}
.ctl .big{background:none;color:var(--fg);border:1px solid var(--fg);font-size:11px;letter-spacing:.24em;text-transform:uppercase;padding:18px 26px}
.ctl span{font-size:10.5px;letter-spacing:.16em;text-transform:uppercase}
.chips span,.sw,.like{font-size:10.5px;letter-spacing:.18em;text-transform:uppercase}.sw.on{background:var(--fg);color:#050505}
.row.on .tx b{color:var(--fg)}.row.on .tx b::before{content:'— '}
.lyr{text-align:center}.lyr p.cur{color:var(--fg)}
.field{border-bottom:1px solid var(--line);font-family:var(--serif);font-size:20px}
.npbar span{font-size:10.5px;letter-spacing:.2em;text-transform:uppercase}
.nplinks span{font-size:10.5px;letter-spacing:.2em;text-transform:uppercase}

.phone::after{content:'';position:absolute;inset:0;pointer-events:none;background-image:url(GRAIN_PNG);opacity:.035;z-index:5}
.tabs,.mini,.sheet{z-index:4}.scrim{z-index:3}
.npart{box-shadow:0 30px 60px rgba(0,0,0,.55)}
.mbar{z-index:4}
"""),
}


STYLES = {k: (n, b, c.replace("GRAIN_PNG", GRAIN_PNG)) for k, (n, b, c) in STYLES.items()}

# Owner's notes on D-F (2026-11-19): rounder everything, and the playing song takes over the whole screen.
ROUND_FULL = """
.art{border-radius:12px}.mart{border-radius:12px}.tart,.bigart{border-radius:22px}
.btn,.chips span,.sw,.like{border-radius:999px}
.seg{border:0;gap:8px}.seg span{flex:none;border:0!important;border-radius:999px;padding:8px 15px;background:rgba(255,255,255,.08)}
.field{border:0!important;border-radius:18px;background:rgba(255,255,255,.09);padding:13px 16px}
.row.on{border-radius:14px;background:rgba(255,255,255,.07);box-shadow:none;padding-left:10px;padding-right:10px}
.mini{left:12px;right:12px;bottom:74px;height:62px;border-radius:22px;border:0;padding:0 14px}.mini .mart{border-radius:14px}.mbar{display:none}
.tabs{border-top:0}
.sheet{border-radius:28px 28px 0 0;border-top:0}
.acct{background:rgba(255,255,255,.05);border:0;border-radius:20px;padding:16px;margin-bottom:10px}
.ctl .big{border-radius:18px!important;width:128px;height:60px;padding:0!important;display:flex;align-items:center;justify-content:center;background:#fff!important;color:#000!important;border:0!important;letter-spacing:0!important}
/* Now playing and Lyrics: the cover is the whole background, the text sits on a dark fade at the bottom. */
.phone[data-name="Now playing"] .npart{position:absolute;inset:0;width:100%!important;height:100%;margin:0!important;aspect-ratio:auto;border-radius:0;box-shadow:none;z-index:0;
  background-image:radial-gradient(70% 45% at 30% 28%,rgba(255,255,255,.22),transparent 70%),radial-gradient(60% 50% at 80% 70%,rgba(0,0,0,.35),transparent 70%)!important}
.phone[data-name="Now playing"]::before,.phone[data-name="Lyrics"]::before{content:'';position:absolute;inset:0;z-index:1;
  background:linear-gradient(180deg,rgba(0,0,0,.35) 0%,rgba(0,0,0,0) 22%,rgba(0,0,0,.15) 45%,rgba(0,0,0,.82) 72%,rgba(0,0,0,.92) 100%)}
.phone[data-name="Now playing"] .status,.phone[data-name="Lyrics"] .status,.phone[data-name="Lyrics"] .body,.phone[data-name="Now playing"] .body>*:not(.npart){position:relative;z-index:2}
.phone[data-name="Now playing"] .body{position:static;display:flex;flex-direction:column;padding-bottom:30px}
.phone[data-name="Now playing"] .npmeta{margin-top:auto;flex-direction:row;align-items:flex-end}
.phone[data-name="Now playing"] .npbar span,.phone[data-name="Now playing"] .npmeta p,.phone[data-name="Now playing"] .times,.phone[data-name="Now playing"] .npfoot{color:rgba(255,255,255,.75)}
.phone[data-name="Now playing"] .npbar span:nth-child(2){color:#fff}
.phone[data-name="Now playing"] .like{border:0;background:none;color:#fff;padding:0}
.phone[data-name="Now playing"] .scrub{background:rgba(255,255,255,.25);border-radius:4px;height:5px}.phone[data-name="Now playing"] .scrub span{background:#fff;border-radius:4px}
.phone[data-name="Now playing"] .ctl span{color:#fff}
.phone[data-name="Now playing"] .nplinks{border:0;gap:0;justify-content:space-between}.phone[data-name="Now playing"] .nplinks span{border:0;margin:0;background:none;color:rgba(255,255,255,.85);padding:12px 0}
.phone[data-name="Now playing"] .npfoot{padding-top:22px}
.phone[data-name="Lyrics"]{background:radial-gradient(70% 45% at 30% 20%,color-mix(in srgb,var(--c) 80%,#fff 10%),transparent 70%),var(--c)!important}
.phone[data-name="Lyrics"] .lyr p{color:#fff;opacity:.45}.phone[data-name="Lyrics"] .lyr p.cur{opacity:1;color:#fff}
.phone[data-name="Lyrics"] .npbar span{color:rgba(255,255,255,.75)}
.phone[data-name="Lyrics"] .lyrfoot{color:rgba(255,255,255,.75);z-index:2}
"""
STYLES = {k: (n, b, c + ROUND_FULL if k[0] in "DEF" else c) for k, (n, b, c) in STYLES.items()}


def doc(css, inner):
    return f'<!doctype html><html><head><meta charset="utf-8"><style>{BASE}{css}</style></head><body>{inner}</body></html>'


cards = []
for key, (name, blurb, css) in STYLES.items():
    if (key[0] in "DEF") != (SET == "cinematic"):
        continue
    pages = "".join(s() for s in SCREENS)
    cover_page = (f'<section class="phone" style="--c:#B5583A"><div class="body" style="padding-top:120px"><div class="lbl">Duet redesign · direction {key[0]}</div>'
                  f'<h1 style="margin:10px 0 18px">{name}</h1><p class="note" style="font-size:15px">{e(blurb)}</p>'
                  f'<p class="note" style="margin-top:28px">{len(SCREENS)} screens: ' + ", ".join(e(s().split("data-name=\"")[1].split("\"")[0]) for s in SCREENS) +
                  '.</p><p class="note" style="margin-top:18px">Song names are made up. Covers are plain colour blocks; in the app they are the real artwork.</p></div></section>')
    with open(os.path.join(OUT, f"{key}.html"), "w") as f:
        f.write(doc(css, cover_page + pages))
    cards.append((key, name, css))

# Overview per direction: every screen, four per row, for one screenshot (1760 wide).
KEY = SCREENS
for key, name, css in cards:
    ov = (f'<!doctype html><html><head><meta charset="utf-8"><style>{BASE}{css}'
          'body{background:#D9D7D1;padding:40px;width:1760px}.ovlbl{font:700 24px Helvetica Neue,Arial;margin-bottom:16px;color:#111}'
          '.ovscreens{display:flex;flex-wrap:wrap;gap:28px}.phone{page-break-after:auto}</style></head><body>'
          f'<div class="ovlbl">{key[0]} · {name}</div><div class="ovscreens">' + "".join(s() for s in KEY) + '</div></body></html>')
    with open(os.path.join(OUT, f"{key}-overview.html"), "w") as f:
        f.write(ov)
print("ok", len(SCREENS), "screens x", len(STYLES))
