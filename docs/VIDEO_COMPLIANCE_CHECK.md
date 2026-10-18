# Compliance check — the six liabilities from the video

Checked against the tree on **2026-10-18** (branch `dev`, after commit `7103159`).

The source is a 70-second video walking through six ways a shipped app racks up legal
exposure before its first sale, with a running counter that ends at **$261,110**. Each item
below is that claim, then what this codebase actually does, with the file to look at.

**This is an engineering audit, not legal advice.** I am not a lawyer. Two of the six need a
decision only you can make, and one of those (the DMCA agent) is a filing, not a code change.

---

## Summary

| # | Claim in the video | Exposure | Verdict here |
|---|---|---|---|
| 1 | **COPPA** 16 CFR 312 — no age gate at signup | $53,000 per child | ⚠️ **Partial** — no age screen, but not directed to children |
| 2 | **GDPR** — fonts loaded from a CDN leak EU visitor IPs | ~$110 | ✅ **Clear** — and one real leak closed today |
| 3 | **CIPA § 631** — analytics/replay on by default = wiretapping | $5,000 per violation | ⚠️ **Partial** — no replay, but crash reporting is on by default |
| 4 | **CAN-SPAM** — bulk email with no unsubscribe or postal address | ~$53,000 per email | ✅ **Not applicable** — the product sends no email |
| 5 | **CA auto-renewal** B&P § 17603 — renewals become refundable gifts | all revenue | ✅ **Not applicable** — no billing of any kind |
| 6 | **DMCA safe harbor** — user uploads with no designated agent | up to $150,000 per work | ❌ **Gap** — hosts user content, no agent, no reporting path |

The two largest numbers in the video — COPPA and DMCA — are the two that are still open here.
The small ones are closed or do not apply.

---

## 1. COPPA — ⚠️ partial

**The claim:** a signup form that collects a name and email but never asks for age means the
first 12-year-old who joins is $53,000 of exposure under 16 CFR 312.

**What this app does.** Accounts exist only for the accountability layer — pairing, groups and
chat. Sign-in is Google Sign-In via Firebase Auth (`accountability/AuthManager.kt`), and the
backend auto-provisions a user row from the Firebase UID on first authenticated request
(middleware in `backend/src/BeOffline.Api/`). **No age or date of birth is collected or
asked for anywhere**, and there is no neutral age screen.

What is in your favour: COPPA attaches to services *directed to children*, or to operators
with *actual knowledge* that a user is under 13. A digital-wellbeing blocker is not a child-
directed service, and `Legal/LegalPages.cs:80` already states *"BeOffline is not directed to
children under 13 (or the equivalent minimum age in your country)."* Collecting no age at all
also means you cannot acquire actual knowledge from a form.

**What I would do:**
- Keep the not-directed statement, and make sure the Play Console **Target Audience** answer
  matches it (13+, not "children"). A mismatch between the store listing and the privacy page
  is the thing that actually gets noticed.
- Decide whether you want a **neutral age screen** before sign-in. It is a real trade-off: it
  adds friction to the one flow that already has the most, and collecting a birth date is
  itself data you then have to justify. A single "Are you 13 or older?" gate collects nothing
  and is the usual middle ground.
- The Data Safety form must say an account identifier is collected, because it is.

---

## 2. GDPR / fonts from someone else's server — ✅ clear

**The claim:** loading fonts from `fonts.googleapis.com` hands Google the IP address of every
EU visitor without consent. A German court put that at €100 per visitor.

**What this app does.** The app's two faces — Instrument Sans and JetBrains Mono — ship
**inside the APK** as variable fonts in `app/src/main/res/font/`, loaded via
`ui/theme/Type.kt`. Nothing is fetched at runtime. The two public pages the backend serves,
`/privacy` and `/account-deletion` (`Legal/LegalPages.cs`), use a plain system font stack
(`-apple-system, Segoe UI, Roboto, sans-serif`) and reference **no external stylesheet,
script or CDN at all**. I grepped for it; there is nothing.

**One real leak was found and closed today, in the same family of problem.** The VPN tunnel
was advertising **8.8.8.8** as its DNS server (`vpn/BeOfflineVpnService.kt`). That handed
Google the fact of every DNS lookup a blocked app attempted — a genuine third-party data flow
the privacy policy did not describe, and a pointless one, since the tunnel discards the query
anyway. It is now an address inside the tunnel. See commit `7103159`.

**Keep it this way:** if you ever add a web page, a WebView, or a marketing site, bundle the
font. This is the single easiest way to reopen this item.

---

## 3. CIPA § 631 — ⚠️ partial

**The claim:** session-replay analytics that record clicks, scrolls and keystrokes, switched
on by default, count as wiretapping in California at $5,000 a violation.

**What this app does *not* do.** There is no session replay, no analytics SDK, no event
tracking, and no keystroke capture. Firebase Analytics is not a dependency — only
Crashlytics, Auth and Messaging (`app/build.gradle.kts:90-93`). The accessibility service,
which is the most invasive thing in the app, declares `canRetrieveWindowContent="false"` and
reads only the package name of the app that came to the foreground
(`res/values/strings.xml`, `openblock/OpenBlockAccessibilityService.kt`). That is as narrow
as that API gets.

**Where it is not clean.** Two things:

1. **Crashlytics collection is on by default.** There is no
   `firebase_crashlytics_collection_enabled` flag in the manifest and no call to
   `setCrashlyticsCollectionEnabled`, so it starts collecting on first launch, before the
   user has seen anything, with no disclosure and no way to turn it off.
2. **`support/IssueReporter.kt` sends user-typed text to Google.** When someone uses "Report
   an issue" it attaches the title (120 chars) and up to **1,000 characters of free-text
   detail** as Crashlytics custom keys, plus manufacturer, model and Android version. That
   one is user-initiated, so it is consented in context — but the privacy policy should say
   where it goes.

**What I would do:** add a crash-reporting toggle in Settings (default on is defensible once
it is disclosed and reversible; default off is safer), wire it to
`setCrashlyticsCollectionEnabled`, declare "Crash logs" and "Diagnostics" in the Data Safety
form, and add a line to the privacy page naming Google as the processor.

---

## 4. CAN-SPAM — ✅ not applicable

**The claim:** a launch email to a waitlist with no unsubscribe link and no physical postal
address is ~$53,000 per message.

**What this app does.** It sends no email. There is no SMTP client, no SendGrid, no MailKit,
nothing in the backend that composes a message. The only `mailto:` strings are links on the
legal pages that open the reader's own mail client (`Legal/LegalPages.cs:36, 94, 115`), which
is not commercial email in the CAN-SPAM sense. Notifications go out as FCM data messages, not
email.

**If this changes:** the moment you email a waitlist, you need a working unsubscribe link, a
physical postal address in the footer, and honest headers. That is the whole rule.

---

## 5. California automatic renewal law — ✅ not applicable

**The claim:** an auto-renewing subscription without clear disclosure, affirmative consent and
an easy cancel makes every renewal an "unconditional gift" under B&P § 17603 — refundable.

**What this app does.** There is no billing. No Play Billing dependency, no Stripe, no
subscription entity, no price anywhere in the codebase. The app is free.

**If this changes:** Play Billing handles most of the mechanics, but § 17603 is about the
*disclosure*, which is yours: the renewal terms have to be visible before purchase, consent
has to be affirmative, and cancellation has to be as easy as signing up.

---

## 6. DMCA safe harbor — ❌ gap, and the bigger risk is Play, not the statute

**The claim:** letting users upload anything without a registered DMCA designated agent
forfeits safe harbour, at up to $150,000 per work.

**What this app does.** It hosts user-generated content. `ChatMessage`
(`backend/src/BeOffline.Api/Domain/Entities.cs:128`) stores a free-text `Body` on your server,
keyed by conversation, and `ChatController` fans it out to every other group member. It is
text only — there are no file or image uploads anywhere in the backend (no `IFormFile`, no
multipart, no blob storage), and GIF-in-chat was deliberately deferred.

Text-only UGC makes the *copyright* exposure modest in practice. But three things are missing:

1. **No registered DMCA designated agent.** This is a US Copyright Office filing — about $6 —
   plus the agent's contact details published on your site. Without it, safe harbour is not
   available to you at all, regardless of how you behave.
2. **No takedown process.** Nothing published tells a rights-holder where to send a notice,
   and there is no procedure for acting on one.
3. **No in-app way to report a message or block a member.** I grepped for it; there is no
   reporting, blocking, moderation or flagging path anywhere in the app or the API.

**Point 3 is the urgent one, and it is not about copyright.** Google Play's User Generated
Content policy requires apps that host user content to provide an in-app reporting mechanism
and a way to block abusive users. You have group chat with up to 10 members and @-mentions.
A reviewer who finds the chat and no report button can reject the release — and that is a
far more likely outcome than a copyright claim over a text message.

**What I would do, in order:**
- Add **report message** and **block/remove member** to the group chat UI, with the report
  reaching you (an API endpoint writing to the existing `ActivityLog` is enough to start).
- Register the designated agent and publish the contact on the `/privacy` page.
- Add a short acceptable-use paragraph to the legal pages saying what is not allowed and that
  you will remove it.
- If you ever add image or GIF upload, revisit all of this — that is when the copyright
  exposure becomes real rather than theoretical.

---

## What this adds up to

Of the video's $261,110:

- **$110 (fonts)** — closed, and a real DNS leak closed alongside it.
- **$53,000 (CAN-SPAM)** and **the refund exposure (ARL)** — do not apply to this product.
- **$5,000 (CIPA)** — mostly closed; what remains is a disclosure-and-toggle job on
  Crashlytics, not a rearchitecture.
- **$53,000 (COPPA)** — a decision about whether to gate, plus making the Play Console answer
  match the privacy page.
- **$150,000 (DMCA)** — the genuine gap, and the Play UGC requirement inside it is the thing
  most likely to actually bite, at the next release review.

Nothing here blocks a build. Items 3 and 6 should be done before the next Play submission;
`docs/PLAY_COMPLIANCE.md` is the checklist that release goes through.
