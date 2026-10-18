# Store listing copy

Character counts verified against Play's field limits by
`docs/play-listing/generate.py`'s sibling check. Re-count if you edit.

## Short description (72/80)

Block the apps that pull you in, with someone you trust holding the key.

## What's new (351/500)

A rebuilt BeOffline.

One list of the apps you are staying away from, each saying in plain words what it is doing right now. Setting up a block is three questions on one screen. New light theme throughout.

Fixed: blocked apps could still load on networks using IPv6. They cannot now.
Fixed: the offline notice could stay up after a block was removed.

## Full description (2335/4000)

BeOffline helps you put an app down and keep it down.

Choose an app that pulls you in, decide how firmly you want it held, and say when. That is the whole setup — three questions on one screen.

HOW FIRMLY

• Nothing loads — the app still opens, but its feed stays empty. Good for something you still need for messages.
• Will not open — the app does not open at all. Your block screen appears instead.

WHEN

Until you turn it off, on a schedule you set, or just for the next while. Overnight windows work too.

ONE LIST, PLAIN WORDS

Home is the list of apps you are staying away from. Every row says what your phone is actually doing — "Will not open until 6:00 PM", "Nothing loads, weekdays until 5:00 PM" — so you never have to decode a status icon or go hunting through settings to find out.

IF YOU NEED IT ANYWAY

• Earn it — solve a short set of problems that gets harder each time you come back.
• Ask someone — a person you choose decides, and they always see that you asked.
• Ask a group — up to ten people; whoever answers first decides. Groups get a chat.

HONEST BY DESIGN

BeOffline is not a cage, and does not pretend to be. You can always turn it off. But if you back out while someone is holding you to it, they are told, and turning it off takes a cooldown. The point is to make giving up visible, not impossible.

HOW BeOffline USES THE ACCESSIBILITY API

The option that stops an app opening uses Android's AccessibilityService API for one purpose: to detect which app comes to the foreground, so BeOffline can show your block screen when you open an app you chose to block. It only receives window-change events. It does not read screen content, does not capture what you type, does not collect passwords, and never sends any accessibility data off your device. The app shows a full disclosure before you enable the service, and you can turn it off at any time in Settings → Accessibility. This option is entirely optional — if you never use it, you are never asked for the permission.

YOUR PRIVACY

Your blocks stay on your phone. The internet option uses a local connection on your own device; your traffic is never inspected, recorded or sent anywhere. An account is only needed if you want to involve another person.

BeOffline is a self-control tool for your own device. It is not a parental-control app.
