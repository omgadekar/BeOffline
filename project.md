# BeOffline — project reference

> Read this first. It exists so you don't have to scan the whole repo to know what this is.
> Last verified against the tree: 2026-10-18 (branch `dev`, versionCode 9 / v1.0.8).

---

## 1. What the product is

**BeOffline** (`com.beoffline.app`, publisher OmGLabs, live on Google Play) is an Android
digital-wellbeing app. The user picks apps they want to stay away from, and BeOffline
enforces that — with other people as the backstop when willpower isn't enough.

It is **not** a parental-control app and must never be positioned as one (Play policy).
The design mechanic is deliberately *"make bypass visible and socially costly, not impossible."*

### Two restriction engines (this is the single most important thing to understand)

| | **Internet block** (older, v1) | **App Lock / open block** (newer) |
|---|---|---|
| What it stops | app can't reach the **internet** | app can't be **opened at all** |
| Mechanism | `VpnService` allowlist tunnel, packets read and discarded | `AccessibilityService` watches foreground app → overlay + send-home |
| Entity / table | `BlockRule` / `block_rules` | `OpenBlockRule` / `open_block_rules` |
| Controller | `VpnController` + `VpnStateManager` | `OpenBlockController` + `OpenBlockStateManager` |
| Scheduler | `RuleScheduler` → `ScheduleAlarmReceiver` | `OpenBlockScheduler` → `OpenBlockAlarmReceiver` |
| Permission | VPN consent | Accessibility service (`isAccessibilityTool=true`) |
| UI tab | "Rules" | "App Lock" |

They are **separate features that must not conflict**. Shared logic lives in
`ScheduledWindow` + `RuleScheduler.isWithinScheduledWindow()`; rows are never shared.
Both rule types support the same three modes: `PERMANENT` / `SCHEDULED` / `TIMER`.

### The accountability layer

Three ways to get back into a locked app:
1. **Solo challenge** — a brain-teaser in the block overlay (`ChallengeEngine`), difficulty
   climbs with each unlock in a session. Kinds: `Arithmetic`, `Retype`, `Pattern`, `Hold`.
2. **1:1 maker–checker** — one paired partner approves/denies an unlock request.
3. **Group** — up to 10 approvers, **first responder wins**, with a group chat (+ @-mentions).

A successful unlock writes an `Allowance` row (source `TEASER` / `PARTNER` / `GROUP`) with an
expiry. Enforcement checks allowances per foreground event — **no alarms needed for expiry**.

---

## 2. Repo layout

```
D:\BeOffline
├── app/                       Android app (single Gradle module, 100% Kotlin)
│   ├── src/main/java/com/beoffline/app/
│   │   ├── accountability/    Firebase auth, Retrofit ApiService, repository, FCM, SignalR, workers
│   │   ├── background/        BackgroundProtectionManager (OEM autostart/battery deep-links)
│   │   ├── data/local/        Room DAOs + BeOfflineDatabase
│   │   ├── data/model/        BlockRule, OpenBlockRule, Allowance, ScheduledWindow, accountability entities
│   │   ├── data/repository/   BlockRuleRepository, OpenBlockRuleRepository
│   │   ├── di/                AppModule, NetworkModule (Hilt)
│   │   ├── notifications/     BlockedAppNotificationListenerService
│   │   ├── openblock/         a11y service, BlockOverlayManager, ChallengeEngine/Controller, prefs
│   │   ├── receiver/          Boot, schedule alarms, VPN recovery
│   │   ├── scheduler/         RuleScheduler, OpenBlockScheduler
│   │   ├── ui/                ← all Compose; see §3
│   │   └── vpn/               BeOfflineVpnService, VpnController, VpnStateManager, resilience
│   ├── schemas/               Room exported schemas 1..7 — KEEP COMMITTED
│   └── src/test, src/androidTest
├── backend/                   ASP.NET Core 8 API (see §5)
├── docs/PLAY_COMPLIANCE.md    Play Store compliance checklist — read before any store work
├── PRODUCT_OVERVIEW.md, README.md, TECHNICAL_FLOW_DIAGRAM.md
└── project.md                 ← this file
```

### Android stack
Jetpack Compose only (one `MainActivity`, Navigation Compose) · Room (DB **v7**) · Hilt ·
MVVM + `StateFlow` UiState · Coroutines/Flow · WorkManager + exact `AlarmManager` ·
Retrofit/Gson · Firebase (Auth + FCM + Crashlytics) · SignalR (`com.microsoft.signalr:8.0.0`).

- **Kotlin 1.9.24 / AGP 8.6.1**, minSdk 26, targetSdk 36. Firebase BOM **pinned 33.1.2**
  (34.x needs Kotlin 2.x metadata — do not bump without the Kotlin upgrade).
- Release build has `isMinifyEnabled = false` — a deliberate R8/Windows workaround.
- The app avoids `QUERY_ALL_PACKAGES`; it uses a launcher-only `<queries>` filter.

---

## 3. UI map (as shipped — the thing being redesigned)

`MainActivity` → `BeOfflineNavGraph` → `MainShell` (bottom nav, 5 tabs) + pushed routes.

**Tabs:** `Your apps` · `People` (`ShellTab` in `MainShell.kt`). Settings is a pushed
route reached from the header, not a tab.

| Route / screen | File | Purpose |
|---|---|---|
| `welcome` | `WelcomeScreen.kt` | First run: pick an app → for how long → the one permission it needs |
| Shell → Your apps | `AppsScreen.kt` | **Home.** One list of apps from BOTH rule tables, each row a sentence |
| Shell → People | `AccountabilityScreen.kt` | Sign-in, pairing, incoming/outgoing requests, groups entry |
| `block_creator?ruleId=&lock=` | `BlockCreatorScreen.kt` | **The one creator.** Which apps → how strict → when |
| `app_picker` | `AppPickerScreen.kt` | Bound to `BlockCreatorViewModel` via the back-stack entry |
| `settings` | `SettingsScreen.kt` | Permissions, how you get back in, how long you get, account |
| `open_block_disclosure` | `AccessibilityDisclosureScreen.kt` | Play prominent disclosure, gates a11y |
| `group_chat/{groupId}` | `GroupChatScreen.kt` | Group chat with @-mentions |
| (in People) | `GroupsScreen.kt` | Create/join group, members, leave/remove |
| overlay (not a route) | `ui/challenge/ChallengeSurface.kt` | The block screen + unlock challenge |

**The sentence engine** — `ui/screens/AppStateCopy.kt` is the heart of the home screen and
the only place a rule is turned into words. Pure Kotlin, no Context, `now` always injected;
every phrasing is pinned in `AppStateCopyTest` (14 tests). `Strictness` (Offline / Locked)
is the user-facing name for the two engines and appears nowhere near the word "engine".

**Design system** — `ui/theme/`:
- `Color.kt` — "Nocturne" dark palette. Ground `Brand900 #161826`, card `Brand800 #232532`,
  hairline `Brand600 #3F424D`, overlay `BrandVoid #0F1019`. Accent = muted violet
  `AccentPrimary #9184D9` used as **outline / glow / 10–25% wash, rarely a fill**.
  No celebratory green; the only saturated colour is the warning `StatusDanger #E08D74`.
- `Type.kt` — never heavier than `FontWeight.Medium`; display sizes track tight,
  `labelSmall` is the wide-tracked uppercase eyebrow.
- `BoComponents.kt` — `BoCard`, `BoEyebrow`, `BoHairline`, `BoFadedDivider`, `BoToggle`,
  `BoPrimaryButton`, `BoSecondaryButton`, `BoHeaderAction`, `BoChip`, `BoScreenHeader`,
  `BoEmptyState`, `BoAppIcon`, `BoAvatar`, `BoStepTrack`, `BoShape`.
- Dark theme only (`BeOfflineTheme` → `darkColorScheme`). No light scheme exists.

### What the redesign removed (all done, 2026-10-18)
1. ~~Two vocabularies~~ → one object, a **block**, with one question: *how strict?*
2. ~~Two near-identical creators (723 + 482 LOC)~~ → one `BlockCreatorScreen`.
3. ~~Jargon~~ → Accountability → People · allowance → "open for N more minutes" ·
   "Unlock reward" → "How long you get" · "Challenge kinds" → "How you get back in" ·
   "VPN access" → "Holding traffic" · "Reliability" → "Keeping blocks working".
4. ~~Three names for one feature~~ → "People", everywhere.
5. ~~Five tabs~~ → two.
6. ~~Permission wall on first launch~~ → `WelcomeScreen` asks a question first and requests
   the accessibility switch only if the chosen strictness needs it, with the
   no-permission option ("keep it empty instead") on the same screen.
7. ~~Title Case~~ → sentence case throughout.

Deleted in the process: `HomeScreen`, `RulesScreen`, `OpenBlockScreen`, `RuleCreatorScreen`,
`RuleCreatorViewModel`, `OpenBlockRuleCreatorScreen`, `OpenBlockRuleCreatorViewModel`,
`StartupPermissionsDialog` — about 3,100 lines.

Two behaviour changes worth knowing about:
- **Overnight windows now work for both engines.** The old `BlockRule` creator rejected an
  end time earlier than the start, so "offline after 10pm" could not be expressed. The
  scheduler always handled midnight crossing; only the form refused it.
- **Saving a block turns it on.** Someone who has just answered three questions about an app
  expects it blocked, not filed away for them to remember to enable.

---

## 4. Data model & migrations

Room DB `BeOfflineDatabase`, currently **v7**, migration chain 1→7 all hand-written and tested
(`MigrationTest` in `androidTest`).

**LANDMINE:** `AppModule` once used `fallbackToDestructiveMigration()` on a published DB.
It was removed in M0 — **never re-add it.** Any new entity/column needs a real `Migration`
plus a bump, or shipped users lose their rules. `app/schemas/*.json` must stay committed.

Tables: `block_rules`, `open_block_rules`, `allowances`, `solo_teaser_state`, `partners`,
`unlock_request_cache`, `outbox_items`, `group_cache`, `chat_messages`.

Block rules are **never synced to the server** (privacy-minimal, v1 decision).

---

## 5. Backend

`backend/` — ASP.NET Core 8, EF Core 8 + **Npgsql/PostgreSQL**, hosted on an existing **OCI VM**.

**Where it is deployed** (so nobody has to go looking again):

| | |
|---|---|
| Host | `beoffline-api.askthepolicy.com`, Cloudflare-proxied |
| App directory | `/opt/beoffline-api/` |
| Service user | `ubuntu`, systemd unit `beoffline-api` |
| Production config | `/opt/beoffline-api/appsettings.Production.json` (not in git) |
| Secrets | `/etc/beoffline/` — Firebase service account, Cloudflare origin cert + key |
| nginx site | `/etc/nginx/sites-available/beoffline-api` |
| Kestrel | `http://127.0.0.1:5080`, loopback only |

Update path: `docs/BACKEND_DEPLOYMENT.md`. First-time setup (DNS, origin certificate, the two
OCI firewall layers) is the
[Deployment Runbook artifact](https://claude.ai/artifact/SzDtGGZG7MjpzpyBZvka1E).

- Auth: **Firebase JWT bearer validation** (securetoken authority, no Admin SDK needed for
  validation); users auto-provisioned by middleware; **Firebase UID is the PK**.
- Push: FCM via FirebaseAdmin — **DATA-ONLY messages** (notification+data broke background
  delivery; the client builds its own routed notification). Do not change this.
- Realtime: SignalR `AccountabilityHub` at `/hubs/accountability`.
- Controllers: pairing/invites, requests (idempotent, 15-min expiry, atomic first-responder
  claim via `ExecuteUpdateAsync` guarded on `Status==Pending`), groups, chat, devices
  heartbeat, tamper, account deletion (`DELETE /api/account`, transactional purge).
- Public HTML pages served by the API: `/privacy`, `/account-deletion` (`LegalPages.cs`).
- Tests: `backend/tests/BeOffline.Api.Tests` (WebApplicationFactory + SQLite in-memory,
  fake auth via `X-Test-Uid`). 28/28 at last run. Android: 21 JVM tests.

**Secret hygiene:** base `appsettings.json` must keep `Password=CHANGE_ME`. The real Postgres
password lives only in `appsettings.Production.json` on the VM (gitignored). A real password
has leaked into the working tree once before — check before every commit.

---

## 6. Build & test

```bash
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # 21 JVM tests
./gradlew :app:connectedDebugAndroidTest   # incl. MigrationTest (needs a device)
cd backend && dotnet test             # 28 backend tests
```
Git: work on `dev`, main branch is `master`. Build output dirs `.gradle-build*` are scratch.

---

## 7. Play Store status

Full checklist: `docs/PLAY_COMPLIANCE.md`. Code-side work is done; what remains is Console
and config only — backend redeploy, privacy copy review, Data Safety form, accessibility +
VPN declarations with demo video, store listing rewritten in wellbeing language (avoid
"parental control", "monitor", "track"), versionCode bump, closed testing first.

Signing SHA-1s that must all be in Firebase: debug `d614dac2…`, release/upload `d2dfa118…`
(CN=Om Gadekar), plus the Play App Signing key.

---

## 8. The redesign (chosen and built, 2026-10-18)

Prototypes live on a private Artifact canvas:
**https://claude.ai/artifact/H3UwKtW9AkvyDQMeQAKKyD** (14 artboards, 390×844).

**Chosen: direction B ("The List") as the app, direction C's flow as the first run.**
A was dropped — its simplicity came from hiding standing rules, which is what most people
actually want, and its Focus/Blocks tab split recreated the Rules/App Lock confusion.

The structural fix, now implemented:

- The two engines stop being two features. One object, a **block**, with one question —
  *how strict?* → "opens, but nothing loads" (VPN) vs "won't open at all" (accessibility).
- One list, one creator, one vocabulary. `RuleCreatorScreen` + `OpenBlockRuleCreatorScreen`
  collapse into a single screen.
- Vocabulary: Rule/Lock → **block** · Block Type/Lock Type → **when** · Accountability/Social
  → **People** · allowance → **extra time** · unlock reward → **how long you get** · VPN access
  → "let BeOffline hold traffic" · accessibility service → "let BeOffline see what's open" ·
  "Protection is off" → "Nothing is blocked".
- Permissions asked **in context** with a no-permission fallback offered, not as a launch wall.
- Block-screen default action is **Go back**; unlocking is the effortful secondary path.

**Not yet verified:** none of this has been run on a device or emulator. It builds
(`:app:assembleDebug`) and the 49 JVM tests pass, but no screen has been seen running.
A physical-device pass is the next thing that should happen — in particular the first-run
flow, the time picker, and a locked app actually showing its block screen.

### Palette — "Paper" (chosen 2026-10-18, replaces Nocturne)

The dark violet theme is retired. Nocturne read as cold and generic, and its dimmest text
failed accessibility. Paper is **light-first, one accent, no second accent.**

| Token | Hex | Use | Contrast |
|---|---|---|---|
| Ground | `#F7F3ED` | screen background | — |
| Card | `#FFFFFF` | raised surface, 1px `#E6DED2` border | — |
| Sunken | `#EFE9E0` | segmented tracks, inert tiles | — |
| Hairline | `#E6DED2` | borders (`#D7CBBA` for dashed) | — |
| Ink | `#1C1915` | primary text, dark button fills | 15.2:1 |
| Ink muted | `#6B635A` | secondary text — **the floor for any text** | 5.3:1 |
| Ink faint | `#8C8276` | icon strokes only, never text | 3.4:1 |
| Clay | `#A8452A` | accent: fills (white text 5.9:1), accent text | 5.4:1 |
| Clay deep | `#7E3220` | pressed, text sitting on the wash | 7.4:1 on wash |
| Clay wash | `#F6E7E0` | selected rows, chips; border `#E3C3B5` | — |
| Amber | `#8A5A12` | "running on borrowed time"; wash `#F6EDDC` | 5.4:1 |

- **No destructive red.** Ending a block early or dropping a partner is written in *ink muted*
  — possible, never inviting. That is a product decision, not an oversight.
- Clay and amber differ in lightness as well as hue, so the two live states stay distinguishable
  without colour.
- Block overlay uses a slightly deeper ground (`#F2ECE2` with a `#F6E7E0` radial) — a different
  surface, still light. A black overlay at the moment of frustration was rejected deliberately.
- Radius 14 on cards and buttons, 11 on app icons, 999 on chips. Tap targets ≥ 44×44.

Type: **Instrument Sans** (UI, 400/500/600) + **JetBrains Mono** (timers, counts, eyebrows).
Both are Google Fonts and must be **bundled in the APK**, never fetched at runtime — a runtime
fetch is the GDPR item in §9.

**Why Nocturne was dropped:** `TextTertiary #75798C` on `Brand900 #161826` measured **4.08:1**,
below the WCAG AA floor of 4.5:1 for body text. That was a real legibility defect, not taste.
Every colour above clears AA against the surface it actually sits on.

## 9. Compliance checklist to run (source: user-supplied video, 70s, 2026-10-18)

Not yet run against this codebase. Six liabilities the video walks through, in order:

| # | Topic | Exposure | What to check here |
|---|---|---|---|
| 1 | **COPPA** 16 CFR 312 | $53,000 per child | Google Sign-In creates accounts with no age gate / DOB |
| 2 | **GDPR** — fonts from Google's servers | ~$110 per the German ruling | `/privacy`, `/account-deletion` HTML; any runtime font fetch |
| 3 | **CIPA § 631** (California wiretapping) | $5,000 per violation | Session-replay-like capture on by default; Crashlytics custom keys in `IssueReporter` |
| 4 | **CAN-SPAM** | ~$53,000 per email | Any bulk email needs an unsubscribe link + physical postal address |
| 5 | **CA auto-renewal law** B&P § 17603 | every renewal refundable as an "unconditional gift" | Only if a subscription ships |
| 6 | **DMCA safe harbor** | up to $150,000 per work | User uploads / chat content need a registered designated agent ($6 filing) |

Running total in the video: **$261,110 before a single sale.** Items 1, 3 and 6 are the ones
most likely to bite BeOffline as built (accounts, Crashlytics payloads, group chat).

## 10. Conventions and traps

- **Never** reintroduce `fallbackToDestructiveMigration()`.
- **Never** make FCM messages notification+data; data-only or background delivery breaks.
- Don't bump Firebase BOM past 33.x while on Kotlin 1.9.24.
- Dates cross the wire as ISO strings → `java.time.Instant`; .NET's 7-digit fractional
  seconds break `SimpleDateFormat`.
- One partner at a time (1:1); groups are the many-approver path.
- Anti-puppet rule: a relationship created while either party already has one gets
  `CanApproveAfterUtc = +24h`; the first-ever relationship on both sides is immediate.
- Removing a partner / leaving a group runs a **24h cooldown**; disabling App Lock runs a
  cooldown too (`disableEffectiveAt`), reboot-safe via WorkManager, and tells the partner.
- Compose only — there are no XML layouts to edit.
