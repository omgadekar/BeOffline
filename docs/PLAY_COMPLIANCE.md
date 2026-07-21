# BeOffline — Google Play Compliance Guide

Everything you need to submit the accountability + App Lock update for review, grounded in what the app actually declares and does. Work the **Action items** first — two of them are hard blockers for approval.

- **Package:** `com.beoffline.app` · **Developer:** OmGLabs
- **minSdk 26 / targetSdk 35**
- Sensitive surfaces in this build: **AccessibilityService**, **VpnService**, **user accounts (Google Sign-In)**, **server-side personal data**, **FCM**.

---

## 0. Status at a glance

| Area | State | What's needed |
|---|---|---|
| Accessibility prominent disclosure (runtime) | ✅ Built | Verify copy (below), mirror in Console |
| `isAccessibilityTool` + minimal config | ✅ Built | Justification text (below) |
| VpnService (local, no data leaves device) | ✅ OK | Declare in Console, keep privacy wording |
| **In-app account deletion + deletion URL** | ✅ Built | In-app button + `DELETE /api/account` + `/account-deletion` page; verify after deploy |
| **Privacy policy (hosted URL)** | ✅ Built | Served at `/privacy`; **review the wording**, then use the deployed URL in Console |
| Data Safety form | ⚠️ To complete | Use answers in §4 |
| Permissions justifications | ⚠️ To complete | Use table in §6 |
| Foreground service review | ⚠️ Likely | Justification in §6.1 |
| Store listing framing | ⚠️ Rewrite | Copy in §8 |
| Exact-alarm permission | ℹ️ Optional | See §6.2 |

> **Both former hard blockers are now built in code** (account deletion + privacy policy served by the API). Once the backend is redeployed they're live; the remaining work is Console forms + copy you must review.

---

## 1. Required action items (in order)

1. ~~Build account deletion~~ ✅ **Done in code.** In-app **Accountability → Delete my account** calls `DELETE /api/account` (purges all server rows for the UID, notifies partners, transfers/deletes owned groups). Public request page at `/account-deletion`. **After deploy:** verify the URL loads and put it in Console's *Data deletion* field.
2. ~~Publish a privacy policy~~ ✅ **Served at `/privacy`.** **Review the wording** (and the contact email in `LegalPages.cs`, currently `gadekarom@gmail.com`), then use `https://beoffline-api.askthepolicy.com/privacy` in Console.
3. **Complete the Data Safety form** using §4.
4. **Fill the Accessibility & Permissions declarations** in Play Console using §2 and §6.
5. **Rewrite the store listing** to wellbeing framing using §8 (avoid the banned words list).
6. **Re-test** a signed build end-to-end, then submit to a **closed testing** track first.

---

## 2. Accessibility API compliance

Play scrutinises AccessibilityService use heavily (enforcement tightened for 2026). BeOffline's use is a legitimate **self-control / digital-wellbeing** case, and the app is already built to satisfy the requirements.

**What the app declares** (`res/xml/accessibility_service_config.xml`):
- `accessibilityEventTypes="typeWindowStateChanged"` — foreground-app changes only.
- `canRetrieveWindowContent="false"` — never reads screen content.
- `isAccessibilityTool="true"` — this is a self-imposed control tool on the user's own device.

**Runtime prominent disclosure** (already implemented as `AccessibilityDisclosureScreen`, shown *before* the grant). Confirm it states, in the user's face, before enabling:

> **Before you enable App Lock**
> BeOffline uses Android's accessibility service for one purpose: to detect when an app *you* chose to block is opened during a focus session, so it can show your block screen instead.
> - It only observes **which app** comes to the foreground.
> - It does **not** read screen content, does **not** collect what you type, and **never** sends this information off your device.
> You can turn this off any time in Settings → Accessibility.
> [ I understand — enable ]

**Play Console → Policy → App content → "Accessibility" / permissions declaration.** Paste this justification:

> BeOffline is a digital-wellbeing app. Its App Lock feature helps a user stop themselves from opening apps they chose to restrict during focus sessions. The accessibility service is used solely to detect which app is in the foreground so the app can display its own block screen. It subscribes only to window-state-changed events, does not retrieve window content, does not capture keystrokes or personal content, and transmits nothing off the device. `isAccessibilityTool` is set to true because the sole purpose is to help the user manage their own app usage on their own device. There is no less-invasive API that provides reliable, immediate foreground-app detection for this purpose.

**Video for review:** record a short screen capture showing the disclosure screen → enabling the service → a blocked app being intercepted. Reviewers usually ask for this; attach it proactively.

---

## 3. VpnService compliance (the existing internet-block feature)

BeOffline's VPN is **device-local**: it routes selected apps into a loopback tunnel and drops their packets to cut internet. **No traffic is inspected, collected, or forwarded off the device.**

Play Console → the VpnService use will be flagged; declare:

> The app uses VpnService only to locally block internet access for user-selected apps (a focus/wellbeing feature). It establishes a local-only tunnel and discards packets for blocked apps. It does not read, collect, forward, or proxy any user traffic, and contacts no remote VPN server.

Keep the in-app disclosure that a VPN connection will be used. Do **not** add any analytics or traffic inspection to this path — that would break the VPN policy.

---

## 4. Data Safety form — answers

Fill Play Console → App content → **Data safety** exactly as below. BeOffline **does collect** data now (accounts + accountability server). Block rules and App-Lock rules are **not** sent to the server — do not over-declare them.

**Does your app collect or share user data?** → **Yes.**

**Data types collected:**

| Category | Data type | Collected | Shared | Processed ephemerally | Required | Purpose |
|---|---|---|---|---|---|---|
| Personal info | Name | Yes | No | No | Optional | Account, app functionality |
| Personal info | Email address | Yes | No | No | Required | Account management |
| Personal info | User IDs (Firebase UID) | Yes | No | No | Required | Account, app functionality |
| App activity | App interactions (unlock requests incl. the blocked app's package name + label; approvals) | Yes | Yes* | No | Optional | App functionality |
| App activity | Other user-generated content (group chat messages) | Yes | Yes* | No | Optional | App functionality |
| App info & performance | Diagnostics (crash logs via Crashlytics) | Yes | No | No | Optional | Crash reporting |
| Device or other IDs | Device ID / FCM token | Yes | No | No | Required | Push notifications |

\* **"Shared"** here means visible to the accountability partners/group members the user themselves chose — not sold or sent to third parties. In the form, mark these as shared **with other users** for app functionality. Do **not** mark any data as sold or shared with advertisers.

**Security practices:**
- Data is **encrypted in transit** (HTTPS / TLS): **Yes.**
- Users can **request data deletion**: **Yes** (once §7 is built — do not answer Yes before then).
- You follow the Play **Families**/child policy: N/A unless you target children (you don't — see §9).

**Not collected:** location, contacts, photos, SMS, calendar, health, financials, browsing history, keystrokes, screen content.

---

## 5. Privacy policy (publish at a stable URL)

Host this (edit the bracketed bits) and put the URL in Play Console → App content → Privacy policy. Minimum viable content:

```
BeOffline — Privacy Policy
Last updated: [DATE]

Who we are
BeOffline ("we") is developed by OmGLabs. Contact: [YOUR EMAIL].

What BeOffline does
BeOffline is a digital-wellbeing app that helps you block apps' internet
access and stop yourself from opening chosen apps during focus sessions,
optionally with an accountability partner or group.

Data we collect
- Account: when you sign in with Google we receive your name, email, and a
  Firebase user ID. This keeps your accountability pairing alive across reinstalls.
- Accountability activity: when you send an unlock request, the name/package of
  the app you asked to open, and your approver's decision. When you use a group,
  the chat messages you send. This information is visible to the partner(s) or
  group member(s) you chose to add.
- Protection status events: if your app-block protection is turned off, disabled,
  or the app is uninstalled, we notify the partner(s) you added. This is the core
  accountability feature and is disclosed to you in-app.
- Device token: a push-notification token so we can deliver approvals and alerts.
- Diagnostics: anonymous crash reports (Firebase Crashlytics).

What we do NOT collect
- We do not read your screen, keystrokes, messages, or app content.
- Your block rules and app-lock rules stay on your device and are not sent to us.
- The VPN feature runs locally; we do not inspect, collect, or forward your traffic.
- We do not sell your data or share it with advertisers.

Accessibility service
BeOffline uses Android's accessibility service only to detect which app is in
the foreground, so it can show a block screen for apps you chose to lock. It
does not collect or transmit any accessibility data.

Data retention & deletion
Accountability data is kept while your account is active. You can delete your
account and associated server data at any time from Settings → Delete my account
in the app, or by requesting deletion at [DELETION URL]. Operational logs are
retained for up to 30 days.

Security
Data in transit is encrypted with TLS. Server data is stored on our
access-controlled infrastructure.

Children
BeOffline is not directed to children under 13 (or the equivalent minimum age
in your country).

Changes
We will update this page and the "last updated" date when this policy changes.
```

---

## 6. Permissions — declarations & justifications

Everything currently declared, with the Console justification for each. **No `QUERY_ALL_PACKAGES`** is used (good — the picker is limited to launcher apps), so no "high-risk permission" declaration is needed for it.

| Permission | Why | Notes |
|---|---|---|
| `BIND_VPN_SERVICE` | Local internet-block feature | See §3 |
| `SYSTEM_ALERT_WINDOW` | Full-screen block overlay for App Lock | User-granted; app falls back to send-home if absent |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | Keep the VPN alive | See §6.1 |
| `POST_NOTIFICATIONS` | Approval/tamper notifications, VPN status | Runtime-requested |
| `RECEIVE_BOOT_COMPLETED` | Re-arm active rules after reboot | |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep enforcement/heartbeat reliable on aggressive OEMs | Must be user-initiated; keep the rationale screen |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE` | API calls + VPN state | |
| `WAKE_LOCK` | WorkManager reliability | |
| `BIND_ACCESSIBILITY_SERVICE` | App Lock foreground detection | See §2 |

### 6.1 Foreground service type
The VPN service uses `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`. In Console's FGS declaration, justify:

> A VpnService that provides the app's core internet-blocking feature; it must run in the foreground to keep the local tunnel active while the user's focus session is in effect.

The **AccessibilityService needs no FGS** (the system binds it). The app has no other foreground services.

### 6.2 Exact alarms (optional)
The scheduler uses `setExactAndAllowWhileIdle` guarded by `canScheduleExactAlarms()` and **falls back to inexact** when not permitted, so nothing breaks today. Enforcement is per-foreground-event and needs no alarms for correctness. If you later want punctual schedule flips, declare **`SCHEDULE_EXACT_ALARM`** (user-grantable) — **not** `USE_EXACT_ALARM`, which Play restricts to alarm-clock/calendar apps and would be rejected for this app.

### 6.3 `allowBackup`
`android:allowBackup="true"` is set. Since the local DB now holds accountability cache, consider `allowBackup="false"` (or custom backup rules that exclude `beoffline.db`) so a device-transfer can't resurrect stale partner/allowance state. Low risk, but a clean hardening step.

---

## 7. Account deletion (✅ BUILT)

Implemented end to end:

- **In-app:** **Accountability → Delete my account** (red button + confirmation dialog) → `AccountabilityRepository.deleteAccount()` calls the API, then clears local caches and signs out of Firebase. Only touches local state on a successful server delete.
- **Server:** `DELETE /api/account` (`AccountController`) purges, in one transaction, every row for the caller's UID — `Users`, `Devices`, `Pairings`, `GroupMembers`, `UnlockRequests` + `RequestApprovals`, `Allowances`, `TamperEvents`, `ChatMessages`, `InviteCodes`/`GroupInviteCodes` they issued — notifies partners and group co-members first, and **transfers ownership of any group they own** to the longest-standing member (deleting the group only if they were the last one). Idempotent (204 if already gone). Covered by tests.
- **Web:** `/account-deletion` page describes the in-app path and an email fallback.

**Console:** put `https://beoffline-api.askthepolicy.com/account-deletion` in the *Data deletion* field once deployed.

---

## 8. Store listing

**Position as digital wellbeing / focus / screen-time / accountability.** Never as parental control or monitoring.

**Avoid these words** (they trigger parental-control / spyware / device-admin review or outright rejection): *parental control, monitor, track, spy, surveillance, lock someone, prevent uninstall, control another phone, employee monitoring, stealth, hidden.*

**Short description (≤80 chars):**
> Block distracting apps and stay accountable with a partner or group.

**Full description (paste, trim as needed):**
> BeOffline helps you take back your focus. Choose the apps that pull you in, and BeOffline can cut their internet or stop you from opening them during the focus sessions you set — all on your own device, under your control.
>
> **Two ways to block**
> • Internet block — selected apps go offline during your schedule (a local, on-device VPN; your traffic is never inspected or collected).
> • App Lock — selected apps can't be opened at all during a focus window.
>
> **Stay accountable (optional)**
> • Solo challenge — solve a short, escalating challenge to earn a few minutes.
> • Accountability partner — ask someone you trust to approve an unlock.
> • Groups — ask several people at once; the first to respond decides, with a group chat to keep each other on track.
>
> **Honest by design**
> BeOffline doesn't pretend to be a cage. You can always turn it off — but if you back out of a lock while a partner is holding you accountable, they're told, and turning off takes a cooldown. The point is to make giving up visible, not impossible.
>
> **Your privacy**
> Your block rules stay on your phone. BeOffline never reads your screen or what you type. It only detects which app is in the foreground so it can show your block screen. See our privacy policy for details.
>
> BeOffline is a self-control tool for your own device. It is not a parental-control or monitoring app.

**Data deletion URL field:** the web URL from §7.

---

## 9. Content rating & target audience

- **Target audience:** 18+ or 13+ — **not** "children". Do not opt into the Families programme.
- Complete the **content rating questionnaire**: no violence, no user-to-user content risk beyond the group chat (disclose that the app has **user-generated content / chat** so the rating reflects it, and consider a basic report/block path in chat as a later hardening step).
- **Ads:** declare "No ads" (the app has none).

---

## 10. Pre-submission checklist

- [x] Account deletion shipped (in-app + web URL) — §7 *(code done; verify after deploy)*
- [x] Privacy policy served at a stable URL — §5 *(review wording; live after deploy)*
- [ ] **Redeploy the backend** so `/privacy`, `/account-deletion`, and `DELETE /api/account` are live
- [ ] Review privacy-policy/deletion copy + contact email (`LegalPages.cs`)
- [ ] Data Safety form completed — §4
- [ ] Accessibility declaration + demo video — §2
- [ ] VpnService declaration — §3
- [ ] FGS declaration — §6.1
- [ ] Store listing rewritten, banned words removed — §8
- [ ] Release SHA-1/SHA-256 + Play App Signing key added to Firebase (already done for testing)
- [ ] `versionCode` bumped for the release
- [ ] Roll out to **closed testing** first, then production

---

*Not legal advice — this reflects Google Play program policies as understood at authoring time (2026). Verify against the current Play Console policy pages before submitting.*
