# Cutting a production build for Play review

For **versionCode 10 / versionName 1.1.0** — the release that carries the rebuilt front end
and the IPv6 fix.

This covers the *build*. The Console forms, declarations and policy wording live in
`docs/PLAY_COMPLIANCE.md`, and the backend side is `docs/BACKEND_DEPLOYMENT.md`. Do not work
this page on its own.

---

## Order matters

**Deploy the backend before you roll out the app.** The release build points at
`https://beoffline-api.askthepolicy.com/` (`app/build.gradle.kts`, release `buildConfigField`).
If that host is not serving the current API when the update reaches users, sign-in, pairing,
approvals and the privacy/deletion URLs all fail — and the deletion URL failing is a policy
violation, not just a bug.

1. Deploy the backend (`docs/BACKEND_DEPLOYMENT.md`), verify `/health`, `/privacy`,
   `/account-deletion`.
2. Build and upload the app.
3. Closed testing track first, then production.

---

## 1. What changed in the build config

Already done, in `app/build.gradle.kts`:

```kotlin
versionCode = 10        // was 9
versionName = "1.1.0"   // was 1.0.8
```

A minor bump rather than a patch: two tabs instead of five, one block object instead of two
rule types, a light theme. Play only requires `versionCode` to increase; `versionName` is what
the user sees next to "What's new".

Nothing else in the build config needed changing. `targetSdk` is already 36, which satisfies
the current Play target-API requirement, and the release API URL is already the production
host.

---

## 2. Before you build

- [ ] **`backend/.../appsettings.json` is not carrying the test cooldown.** It currently has
      `ApproverActivationCooldownHours: 0` uncommitted, which switches off the anti-puppet
      rule. That belongs in `appsettings.Development.json`, never in a deployed build.
- [ ] **The app has been run on a device.** As of this writing it has not. The front end was
      rebuilt and verified only by `:app:assembleDebug` plus 49 JVM tests. Do not ship a UI
      nobody has seen running.
- [ ] `./gradlew.bat :app:testDebugUnitTest` — 49 tests green.
- [ ] `./gradlew.bat :app:connectedDebugAndroidTest` — includes `MigrationTest`, which walks
      the Room chain 1→7 on a real device. **This release adds no migration** (both rule
      tables are unchanged), but run it anyway: it is the test that catches an accidental
      schema edit.
- [ ] `app/schemas/*.json` still committed. They are the record of every shipped schema.
- [ ] Check `git status` for secrets before committing anything.

---

## 3. Build the bundle

Play wants an **AAB**, not an APK.

```bash
./gradlew.bat :app:bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`.

### Signing

There is **no `signingConfigs` block in `app/build.gradle.kts`** — release signing is done
through Android Studio's *Build → Generate Signed App Bundle*, using the upload keystore.
That is why `app/release/` is gitignored.

Keep it that way unless you want CI, in which case add:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(providers.gradleProperty("BO_KEYSTORE").get())
        storePassword = providers.gradleProperty("BO_KEYSTORE_PASSWORD").get()
        keyAlias = providers.gradleProperty("BO_KEY_ALIAS").get()
        keyPassword = providers.gradleProperty("BO_KEY_PASSWORD").get()
    }
}
```

…with the four values in `~/.gradle/gradle.properties`, **never** in the repo.

### One thing to know about this build

`isMinifyEnabled = false` and `isShrinkResources = false` in the release type. That is a
deliberate workaround for an R8 failure on Windows that was blocking signed builds. The
consequences are real and worth re-testing one day: the bundle is larger than it needs to be,
and the Kotlin is not obfuscated, so anyone can read your class and method names from the
APK. Nothing in this app is a secret worth protecting that way — no API keys are compiled in,
the Firebase config is public by design — so it is a size and tidiness problem rather than a
security one. Revisit when you next touch the toolchain.

---

## 4. Verify the artifact before upload

```bash
# Version and target actually baked in
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging \
  app/build/outputs/bundle/release/app-release.aab | head -5
```

Then check, on a device, from the **release** build specifically:

- [ ] Sign-in works. If it does not, the release SHA-1/SHA-256 is missing from Firebase —
      you need the debug key, the upload key (`CN=Om Gadekar`) **and** the Play App Signing
      key registered, or sign-in breaks for Play-delivered builds only.
- [ ] The app talks to production, not `10.0.2.2`. Pair two devices and approve an unlock.
- [ ] Cleartext is blocked in release — the API URL must be HTTPS or every call fails.
- [ ] A locked app actually shows the block screen.
- [ ] `/privacy` and `/account-deletion` load from the deployed host.

---

## 5. Listing assets

Generated, correctly sized, in `docs/play-listing/`:

| File | Size | Play slot |
|---|---|---|
| `feature-graphic.png` | 1024 × 500 | Feature graphic (required) |
| `screenshot-1..5.png` | 1080 × 1920 | Phone screenshots (2–8 required, 9:16) |

Regenerate with `py docs/play-listing/generate.py` after any UI change.

**Read this before uploading them.** They are renders of the designed UI, not captures from a
running device, because the app has not been run yet. Play requires screenshots to represent
the app accurately. Once the device pass is done, compare each one against the real screen
and either regenerate or re-shoot. If the real UI differs, the screenshots are wrong and that
is a listing violation, not a cosmetic issue.

Still needed and **not** generated here:

- **App icon, 512 × 512 PNG.** The existing launcher icon is unchanged by this release, so
  whatever is on the listing today is still accurate. Worth revisiting: it was drawn for the
  dark violet theme and now sits next to a light, warm app.
- **Accessibility demo video.** Required for the accessibility declaration (§2 of
  `PLAY_COMPLIANCE.md`). Any existing video shows the old five-tab UI and is now misleading —
  re-record it against 1.1.0.

---

## 6. Store listing text

`PLAY_COMPLIANCE.md` §8 has the approved framing and the banned-words list. **Its wording is
now out of date** in one respect: it describes the features as "Internet block" and "App
Lock", and those names no longer exist in the app. Update the listing to match what a user
will actually see:

> **Two ways to block**
> • **Nothing loads** — the app still opens, but its feed stays empty. Good for something you
>   need for messages.
> • **Will not open** — the app does not open at all; your block screen appears instead.

Everything else in §8 still holds, including the mandatory paragraph documenting the
AccessibilityService use. **Do not drop that paragraph** — omitting it is what got the app
rejected on 2026-07-22.

### Suggested "What's new"

> A rebuilt BeOffline. One list of the apps you are staying away from, each saying in plain
> words what it is doing right now. Setting up a block is three questions on one screen. New
> light theme throughout.
>
> Fixed: blocked apps could still load on networks using IPv6. They cannot now.

---

## 7. Submit

Work `PLAY_COMPLIANCE.md` §10 — Data Safety, accessibility and VPN declarations, foreground
service justification, content rating (declare the group chat as user-generated content).

Go to **closed testing first**. The front end is new, and the one thing you cannot learn from
a build is whether it feels right in a hand.
