<div align="center">

# BeOffline

**Make selected apps truly offline, without putting your whole phone on pause.**

BeOffline is an Android app that gives you stronger focus and cleaner boundaries by blocking internet access for the apps you choose. Instead of only muting notifications, it keeps selected apps offline while the rest of your phone continues to work normally.

[Product Overview](./PRODUCT_OVERVIEW.md)

</div>

## Why BeOffline

Most focus tools reduce noise at the surface level. Messages still arrive, feeds still refresh in the background, and opening the app often brings everything back at once.

BeOffline takes a different approach. It uses Android's local VPN framework to cut internet access for selected apps, which means the boundary is stronger, more intentional, and easier to trust.

## What It Does

BeOffline lets you create offline rules for one app or a group of apps and control them from a single dashboard.

| Capability | What it means |
| --- | --- |
| Permanent rules | Keep selected apps offline until you turn the rule off |
| Scheduled rules | Run recurring offline windows on selected days and times |
| Timer rules | Start focused offline sessions for a chosen duration |
| Custom timers | Use your own timing instead of fixed presets |
| Multi-app groups | Block several distracting apps together in one rule |
| Blocked-app alerts | Get a gentle notification when a blocked app tries to connect |
| Built-in issue reporting | Send bug reports directly from inside the app |

## Why It Feels Different

BeOffline is designed around genuine unreachability, not just quieter notifications.

- Selected apps stop reaching the internet while the rule is active.
- The rest of your phone can stay online and usable.
- You can build rules around routines, time blocks, or immediate boundaries.
- It helps reduce the "open one app, get pulled into everything" problem.

This makes BeOffline useful for deep work, study sessions, family time, bedtime boundaries, and simply taking control over when certain apps are allowed to reach you.

## How It Works

BeOffline uses Android's VPN framework locally on the device.

1. You choose which apps should go offline.
2. BeOffline routes those selected apps through its local control layer.
3. Their internet traffic is blocked while the rule stays active.

This is what makes BeOffline different from ordinary notification muting or basic focus modes. It is not just hiding the interruption. It is stopping selected apps from staying connected in the background.

## Current Highlights

- Kotlin + Jetpack Compose Android app
- App-level internet blocking using `VpnService`
- Rule types: `Permanent`, `Scheduled`, and `Timer`
- Background reliability onboarding for battery optimization and notifications
- Exact scheduled and timer enforcement for recurring and timed rules
- Local persistence with Room
- Dependency injection with Hilt
- Issue reporting with Firebase Crashlytics

## Why It Stands Out

| Typical approach | BeOffline |
| --- | --- |
| Mutes alerts | Blocks internet access for selected apps |
| Reduces noise | Creates a stronger offline boundary |
| Usually broad phone-wide controls | Lets you target specific apps or app groups |
| Often temporary and shallow | Supports permanent, scheduled, and timed rules |
| Easy to bypass mentally | More intentional and harder to "accidentally reopen" |

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Hilt
- Room
- WorkManager
- Android VPN APIs
- Firebase Crashlytics

## Project Structure

| Path | Purpose |
| --- | --- |
| `app/src/main/java/com/beoffline/app/ui` | Compose screens, navigation, and UI state |
| `app/src/main/java/com/beoffline/app/vpn` | VPN service and connection control |
| `app/src/main/java/com/beoffline/app/scheduler` | Timer and schedule enforcement |
| `app/src/main/java/com/beoffline/app/data` | Rule models, Room database, and repository logic |
| `app/src/main/java/com/beoffline/app/support` | Issue reporting and blocked-traffic notifications |

## Getting Started

### Requirements

- Android Studio with Android SDK 34
- JDK 17
- Android device or emulator running Android 8.0+ (`minSdk 26`)

### Run the app

```powershell
.\gradlew.bat :app:assembleDebug
```

Then install the generated debug APK from:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Permissions and Platform Notes

BeOffline depends on a few Android capabilities to do its job well:

- VPN permission so the app can control selected apps' internet access
- Notification permission for status and blocked-app alerts
- Battery optimization exemption guidance for better background reliability
- Boot and alarm handling so timers and schedules can keep working more consistently

Android background behavior varies by manufacturer, so some devices may require extra battery-setting adjustments for best reliability.

## Feedback and Support

BeOffline includes a built-in issue reporting flow inside the app so bugs and unexpected behavior can be submitted directly from the dashboard. That makes it easier to improve real-world reliability and catch device-specific issues earlier.

## Documentation

- [PRODUCT_OVERVIEW.md](./PRODUCT_OVERVIEW.md): complete product narrative, feature breakdown, positioning, and FAQ

## Status

BeOffline is an actively evolving Android focus utility centered on selective offline control. The current product is already capable of permanent, scheduled, and timed app blocking, with ongoing refinement around reliability, UX polish, and smarter feedback flows.
