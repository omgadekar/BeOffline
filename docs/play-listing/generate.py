#!/usr/bin/env python3
"""
Generate the Play Store listing images.

    py docs/play-listing/generate.py

Writes PNGs next to this script, at the exact sizes Google Play requires:

    feature-graphic.png   1024 x 500   (required, one per listing)
    screenshot-1..5.png   1080 x 1920  (9:16; Play wants 2-8, each side 320-3840)

IMPORTANT — these are renders of the designed UI, not captures from a running
device. The app's front end was rebuilt and has not yet been run on hardware.
Play requires screenshots to represent the app accurately, so once the device
pass is done, either re-shoot these from the real app or check each one
against it and regenerate.

Fonts come from Google Fonts at generation time. That is fine here, because
the output is a flat PNG; the APK itself bundles both faces and never fetches
them at runtime.
"""

from pathlib import Path
import subprocess
import shutil
import sys

HERE = Path(__file__).resolve().parent
BUILD = HERE / "_build"

CHROME_CANDIDATES = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
]

# The Paper palette. Keep in step with app/src/main/java/.../ui/theme/Color.kt.
IN, MU, FA = "#1C1915", "#6B635A", "#8C8276"
GROUND, CARD, SUNK, LINE, LINE2 = "#F7F3ED", "#FFFFFF", "#EFE9E0", "#E6DED2", "#D7CBBA"
CLAY, CLAY_DEEP, WASH, WASH_LINE = "#A8452A", "#7E3220", "#F6E7E0", "#E3C3B5"
AMBER, AMBER_WASH, AMBER_LINE = "#8A5A12", "#F6EDDC", "#DCC28C"

FONTS = (
    '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?'
    "family=Instrument+Sans:wght@400;500;600&family=JetBrains+Mono:wght@400;500&display=swap\">"
)

BASE_CSS = f"""
* {{ box-sizing: border-box; }}
body {{ margin: 0; font-family: "Instrument Sans", system-ui, sans-serif; color: {IN}; }}
.mono {{ font-family: "JetBrains Mono", monospace; letter-spacing: 1.6px; font-size: 11px; color: {MU}; }}
.screen {{ width: 390px; height: 844px; background: {GROUND}; display: flex; flex-direction: column;
          overflow: hidden; position: relative; }}
.pad {{ padding: 0 22px; }}
.row {{ display: flex; align-items: center; gap: 13px; border-radius: 15px; padding: 14px 15px; }}
.icon {{ width: 38px; height: 38px; border-radius: 11px; background: {SUNK}; flex-shrink: 0; }}
.name {{ font-size: 15.5px; font-weight: 500; }}
.sub {{ font-size: 13px; margin-top: 3px; }}
.grow {{ flex: 1; min-width: 0; }}
.btn {{ background: {CLAY}; color: #fff; font-weight: 600; font-size: 15.5px; border-radius: 15px;
       padding: 15px; text-align: center; }}
.nav {{ border-top: 1px solid {LINE}; background: {CARD}; display: flex; padding: 12px 0 26px; }}
.nav div {{ flex: 1; text-align: center; font-size: 11.5px; font-weight: 500; color: {MU}; }}
.nav .on {{ color: {CLAY}; }}
.h1 {{ font-size: 27px; line-height: 34px; font-weight: 500; letter-spacing: -0.6px; }}
.lede {{ font-size: 14.5px; line-height: 21px; color: {MU}; margin-top: 8px; }}
"""


def page(body: str, w: int, h: int, css: str = "") -> str:
    return (
        f'<!doctype html><html lang="en"><head><meta charset="utf-8">{FONTS}'
        f"<style>{BASE_CSS}{css}"
        f"html,body{{width:{w}px;height:{h}px;}}</style></head><body>{body}</body></html>"
    )


def shot(caption: str, screen: str) -> str:
    """A 1080x1920 listing frame: caption band, then the screen on a device shell."""
    css = f"""
    .wrap {{ width:1080px; height:1920px; background:
            radial-gradient(120% 40% at 50% 0%, {WASH} 0%, {GROUND} 62%);
            display:flex; flex-direction:column; align-items:center; }}
    .cap {{ font-size:60px; line-height:70px; font-weight:600; letter-spacing:-1.6px;
           text-align:center; margin:104px 86px 0; }}
    .shell {{ margin-top:72px; width:744px; height:1610px; border-radius:62px; background:{CARD};
             box-shadow:0 36px 90px rgba(80,58,44,.22); border:1px solid {LINE};
             padding:14px; overflow:hidden; }}
    .inner {{ width:716px; height:1582px; border-radius:50px; overflow:hidden; position:relative; }}
    .scale {{ transform:scale(1.8359); transform-origin:top left; width:390px; height:844px; }}
    """
    return page(
        f'<div class="wrap"><div class="cap">{caption}</div>'
        f'<div class="shell"><div class="inner"><div class="scale">{screen}</div></div></div></div>',
        1080,
        1920,
        css,
    )


def header(title="BeOffline"):
    return (
        f'<div class="pad" style="padding-top:56px;display:flex;align-items:center;'
        f'justify-content:space-between"><div style="font-size:17px;font-weight:600">{title}</div>'
        f'<div style="width:22px;height:22px;border-radius:50%;border:2px solid {FA}"></div></div>'
    )


def app_row(name, sub, state="now"):
    bg, bd, col = CARD, WASH_LINE, CLAY
    if state == "later":
        bg, bd, col = "#F2ECE2", LINE, MU
    if state == "amber":
        bg, bd, col = AMBER_WASH, AMBER_LINE, AMBER
    dot = f'<div style="width:7px;height:7px;border-radius:50%;background:{col}"></div>' if state == "now" else ""
    tail = f'<div class="mono" style="font-size:13px;letter-spacing:0;color:{col}">4:02</div>' if state == "amber" else dot
    return (
        f'<div class="row" style="background:{bg};border:1px solid {bd}">'
        f'<div class="icon"></div><div class="grow"><div class="name">{name}</div>'
        f'<div class="sub" style="color:{col}">{sub}</div></div>{tail}</div>'
    )


# ── the five screens ────────────────────────────────────────────────────────

HOME = (
    f'<div class="screen">{header()}'
    f'<div class="pad" style="padding-top:26px;padding-bottom:22px">'
    f'<div class="h1">Three apps are<br>blocked right now.</div>'
    f'<div class="lede">Two more start later today. The rest of your phone works normally.</div></div>'
    f'<div class="pad grow" style="display:flex;flex-direction:column;gap:9px">'
    + app_row("Instagram", "Will not open until 6:00 PM")
    + app_row("Reddit", "Will not open &middot; weekdays until 5:00 PM")
    + app_row("X", "Nothing loads &middot; always")
    + app_row("YouTube", "Open for 4 more minutes - Priya said yes", "amber")
    + f'<div class="mono" style="margin-top:8px">LATER TODAY</div>'
    + app_row("TikTok", "Starts at 10:00 PM, until 7:00 AM", "later")
    + "</div>"
    f'<div class="pad" style="padding-bottom:14px"><div class="btn">Block an app</div></div>'
    f'<div class="nav"><div class="on">Your apps</div><div>People</div></div></div>'
)


def q(n, title):
    return (
        f'<div style="display:flex;align-items:baseline;gap:9px;margin-bottom:11px">'
        f'<div class="mono" style="font-size:12px;letter-spacing:0;color:{CLAY}">{n}</div>'
        f'<div style="font-size:20px;font-weight:500;letter-spacing:-0.3px">{title}</div></div>'
    )


def choice(title, body, on):
    bg = WASH if on else CARD
    bd = CLAY if on else LINE
    dot = f"6px solid {CLAY}" if on else f"2px solid {LINE2}"
    return (
        f'<div style="background:{bg};border:1px solid {bd};border-radius:14px;padding:14px 16px;margin-bottom:8px">'
        f'<div style="display:flex;align-items:center;gap:12px">'
        f'<div style="flex:1;font-size:15.5px;font-weight:500">{title}</div>'
        f'<div style="width:19px;height:19px;border-radius:50%;border:{dot}"></div></div>'
        f'<div style="font-size:13px;line-height:19px;color:{MU};margin-top:4px">{body}</div></div>'
    )


CREATOR = (
    f'<div class="screen">'
    f'<div class="pad" style="padding-top:56px;padding-bottom:20px;font-size:20px;font-weight:600">'
    f"&larr;&nbsp;&nbsp;Block an app</div>"
    f'<div class="pad grow">'
    + q(1, "Which apps?")
    + f'<div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:26px">'
    f'<div style="background:{WASH};border:1px solid {WASH_LINE};border-radius:999px;padding:6px 13px;'
    f'font-size:13.5px;color:{CLAY_DEEP}">Instagram</div>'
    f'<div style="background:{WASH};border:1px solid {WASH_LINE};border-radius:999px;padding:6px 13px;'
    f'font-size:13.5px;color:{CLAY_DEEP}">Reddit</div>'
    f'<div style="border:1px dashed {LINE2};border-radius:999px;padding:6px 13px;font-size:13.5px;'
    f'color:{CLAY}">+ Add</div></div>'
    + q(2, "How strict?")
    + choice("It opens, but nothing loads", "Feeds stay empty. Good for an app you still need for messages.", False)
    + choice("It will not open at all", "Tapping it shows your block screen instead.", True)
    + f'<div style="height:26px"></div>'
    + q(3, "When?")
    + f'<div style="background:{SUNK};border-radius:14px;padding:4px;display:flex;gap:4px;margin-bottom:12px">'
    f'<div style="flex:1;text-align:center;padding:10px;font-size:13.5px;color:{MU}">Always</div>'
    f'<div style="flex:1;text-align:center;padding:10px;font-size:13.5px;font-weight:500;'
    f'background:{CARD};border-radius:12px">Set hours</div>'
    f'<div style="flex:1;text-align:center;padding:10px;font-size:13.5px;color:{MU}">For a while</div></div>'
    f'<div style="display:flex;gap:10px">'
    f'<div style="flex:1;background:{CARD};border:1px solid {LINE};border-radius:14px;padding:12px 15px">'
    f'<div class="mono">FROM</div><div style="font-size:15.5px;font-weight:500;margin-top:4px">9:00 AM</div></div>'
    f'<div style="flex:1;background:{CARD};border:1px solid {LINE};border-radius:14px;padding:12px 15px">'
    f'<div class="mono">UNTIL</div><div style="font-size:15.5px;font-weight:500;margin-top:4px">5:00 PM</div></div>'
    f"</div></div>"
    f'<div style="background:{CARD};border-top:1px solid {LINE};padding:14px 22px 30px">'
    f'<div style="text-align:center;font-size:13px;color:{MU};margin-bottom:10px">'
    f"Instagram and Reddit will not open on weekdays, 9 to 5.</div>"
    f'<div class="btn">Start blocking</div></div></div>'
)

BLOCKED = (
    f'<div class="screen" style="background:radial-gradient(120% 55% at 50% 0%, {WASH} 0%, #F2ECE2 60%);'
    f'align-items:center;padding:0 28px">'
    f'<div style="height:140px"></div>'
    f'<div style="width:54px;height:54px;border-radius:16px;background:{CARD};border:1px solid {WASH_LINE}"></div>'
    f'<div style="font-size:28px;font-weight:500;letter-spacing:-0.5px;margin-top:20px">Instagram is blocked</div>'
    f'<div style="font-size:15px;line-height:22px;color:{MU};text-align:center;margin-top:9px;max-width:280px">'
    f"You set this up yourself. Back at 6:00 PM.</div>"
    f'<div class="grow"></div>'
    f'<div style="width:100%;margin-bottom:46px">'
    f'<div style="background:{IN};color:{GROUND};font-weight:600;font-size:16px;border-radius:15px;'
    f'padding:17px;text-align:center">Go back</div>'
    f'<div style="display:flex;align-items:center;gap:12px;margin:18px 0 12px">'
    f'<div style="flex:1;height:1px;background:{LINE2}"></div>'
    f'<div class="mono" style="font-size:10.5px">OR, IF YOU REALLY NEED IT</div>'
    f'<div style="flex:1;height:1px;background:{LINE2}"></div></div>'
    f'<div style="background:{CARD};border:1px solid {LINE};border-radius:14px;padding:15px 17px;margin-bottom:12px">'
    f'<div style="font-size:15px;font-weight:500">Earn 5 minutes</div>'
    f'<div style="font-size:12.5px;color:{MU};margin-top:3px">Four sums, then a 20-second wait</div></div>'
    f'<div style="background:{CARD};border:1px solid {LINE};border-radius:14px;padding:15px 17px">'
    f'<div style="font-size:15px;font-weight:500">Ask Priya</div>'
    f'<div style="font-size:12.5px;color:{MU};margin-top:3px">She decides, and she sees that you asked</div>'
    f"</div></div></div>"
)

PEOPLE = (
    f'<div class="screen">'
    f'<div class="pad" style="padding-top:56px"><div class="h1">People</div>'
    f'<div class="lede">They can let you back into a blocked app. They always see that you asked.</div></div>'
    f'<div class="pad grow" style="padding-top:22px">'
    f'<div style="background:{WASH};border:1px solid {WASH_LINE};border-radius:16px;padding:16px 17px">'
    f'<div style="display:flex;justify-content:space-between;align-items:center">'
    f'<div style="font-size:15.5px;font-weight:600">Waiting on Priya</div>'
    f'<div class="mono" style="font-size:13px;letter-spacing:0;color:{CLAY_DEEP}">12:04</div></div>'
    f'<div style="font-size:13.5px;line-height:20px;color:{CLAY_DEEP};margin-top:11px">'
    f"You asked to open Instagram. If she does not answer in 12 minutes the request just expires.</div></div>"
    f'<div class="mono" style="margin:20px 0 10px">YOUR PERSON</div>'
    f'<div class="row" style="background:{CARD};border:1px solid {LINE}">'
    f'<div class="icon" style="background:{WASH};color:{CLAY};font-weight:600;display:flex;'
    f'align-items:center;justify-content:center;font-size:16px">P</div>'
    f'<div class="grow"><div class="name">Priya</div>'
    f'<div class="sub" style="color:{MU}">Active a minute ago</div></div></div>'
    f'<div style="font-size:12.5px;line-height:19px;color:{MU};margin-top:10px">'
    f"One person at a time. Dropping them takes 24 hours and tells them it happened.</div>"
    f'<div class="mono" style="margin:22px 0 10px">GROUPS</div>'
    f'<div class="row" style="background:{CARD};border:1px solid {LINE}">'
    f'<div class="icon" style="background:{WASH}"></div>'
    f'<div class="grow"><div class="name">Morning crew</div>'
    f'<div class="sub" style="color:{MU}">4 people &middot; whoever answers first decides</div></div></div></div>'
    f'<div class="nav"><div>Your apps</div><div class="on">People</div></div></div>'
)

WELCOME = (
    f'<div class="screen" style="background:radial-gradient(110% 48% at 22% 4%, {WASH} 0%, {GROUND} 58%);'
    f'padding:0 26px">'
    f'<div style="height:92px"></div>'
    f'<div style="font-size:34px;line-height:41px;font-weight:500;letter-spacing:-0.9px">'
    f"What do you want<br>to put down?</div>"
    f'<div style="font-size:15px;line-height:23px;color:{MU};margin-top:12px">'
    f"Pick one to start with. You can change it, or add more, at any time.</div>"
    f'<div style="height:34px"></div>'
    + "".join(
        f'<div class="row" style="background:{CARD};border:1px solid {LINE};margin-bottom:10px;padding:15px 17px">'
        f'<div class="icon" style="width:40px;height:40px;border-radius:12px"></div>'
        f'<div class="grow" style="font-size:16px;font-weight:500">{n}</div>'
        f'<div style="color:{FA};font-size:18px">&rsaquo;</div></div>'
        for n in ("Instagram", "TikTok", "Reddit", "YouTube")
    )
    + f'<div class="grow"></div>'
    f'<div style="border-top:1px solid {LINE};padding:18px 0 40px;display:flex;align-items:center;gap:10px">'
    f'<div style="width:7px;height:7px;border-radius:50%;background:{CLAY}"></div>'
    f'<div style="font-size:14.5px">3 apps are already blocked</div></div></div>'
)

FEATURE = page(
    f'<div style="width:1024px;height:500px;background:'
    f"radial-gradient(90% 150% at 86% 10%, {WASH} 0%, {GROUND} 58%);"
    f'display:flex;align-items:center;padding:0 72px;gap:56px">'
    f'<div style="flex:1">'
    f'<div class="mono" style="font-size:15px">DIGITAL WELLBEING</div>'
    f'<div style="font-size:62px;line-height:66px;font-weight:600;letter-spacing:-2px;margin-top:18px">'
    f"Put the app<br>down.</div>"
    f'<div style="font-size:21px;line-height:31px;color:{MU};margin-top:20px;max-width:430px">'
    f"Block what pulls you in &mdash; and let someone you trust decide when you get back.</div></div>"
    # The phone runs off the bottom edge of the canvas on purpose. Sizing it to
    # fit meant the screen content clipped mid-row INSIDE the frame, which reads
    # as a rendering fault rather than a crop.
    f'<div style="width:300px;height:560px;margin-top:150px;border-radius:40px;background:{CARD};'
    f'border:1px solid {LINE};box-shadow:0 26px 60px rgba(80,58,44,.18);padding:10px;'
    f'overflow:hidden;flex-shrink:0;align-self:flex-start">'
    f'<div style="width:280px;height:540px;border-radius:32px;overflow:hidden;position:relative">'
    f'<div style="transform:scale(.7179);transform-origin:top left;width:390px;height:844px">{HOME}</div>'
    f"</div></div></div>",
    1024,
    500,
)

SHOTS = [
    ("Know what your phone is doing, at a glance", HOME),
    ("Three questions. That is the whole setup.", CREATOR),
    ("A moment to reconsider, not a wall", BLOCKED),
    ("Let someone you trust hold the key", PEOPLE),
    ("It starts by asking, not demanding", WELCOME),
]


def find_chrome() -> str:
    for c in CHROME_CANDIDATES:
        if Path(c).exists():
            return c
    found = shutil.which("chrome") or shutil.which("chromium") or shutil.which("google-chrome")
    if found:
        return found
    sys.exit("No Chrome or Edge found. Edit CHROME_CANDIDATES at the top of this file.")


def render(chrome: str, html: str, out: Path, w: int, h: int) -> None:
    src = BUILD / (out.stem + ".html")
    src.write_text(html, encoding="utf-8")
    subprocess.run(
        [
            chrome,
            "--headless=new",
            "--disable-gpu",
            "--no-sandbox",
            "--hide-scrollbars",
            "--force-device-scale-factor=1",
            f"--window-size={w},{h}",
            # Give the webfonts a moment; without this the first run can render
            # in a fallback face and the type looks wrong.
            "--virtual-time-budget=4000",
            f"--screenshot={out}",
            src.as_uri(),
        ],
        check=True,
        capture_output=True,
    )
    print(f"  {out.name}  {w}x{h}")


def main() -> None:
    BUILD.mkdir(parents=True, exist_ok=True)
    chrome = find_chrome()
    print(f"Rendering with {chrome}\n")

    render(chrome, FEATURE, HERE / "feature-graphic.png", 1024, 500)
    for i, (caption, screen) in enumerate(SHOTS, start=1):
        render(chrome, shot(caption, screen), HERE / f"screenshot-{i}.png", 1080, 1920)

    print("\nDone. Upload order in the listing is the file order above.")


if __name__ == "__main__":
    main()
