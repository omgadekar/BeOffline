# BeOffline Technical Flow Diagram

This diagram is designed to be shareable and easy to explain in a post, while still staying faithful to how the app actually works.

```mermaid
flowchart TD
    A["User Opens BeOffline"] --> B["Dashboard<br/>Jetpack Compose UI"]

    B --> C["Quick Setup<br/>Battery Optimization<br/>Notifications<br/>VPN Consent<br/>Optional Notification Access"]
    B --> D["Create / Edit Rule"]

    D --> E["Rule Builder<br/>Name + Apps + Type"]
    E --> F["App Picker<br/>Launcher Apps Only<br/>No QUERY_ALL_PACKAGES"]
    E --> G["Rule Type"]

    G --> G1["Permanent"]
    G --> G2["Scheduled"]
    G --> G3["Timer"]

    G1 --> H["Save Rule"]
    G2 --> H
    G3 --> H

    H --> I["Room Database<br/>BlockRule Entity + DAO + Repository"]

    I --> J["Dashboard Observes Rules<br/>via Flow"]
    J --> K["User Activates Rule"]

    K --> L{"VPN Permission Already Granted?"}
    L -->|No| M["MainActivity launches<br/>Android VPN Consent Dialog"]
    M --> N["User Approves VPN"]
    L -->|Yes| O["Collect Active Packages<br/>Across All Active Rules"]
    N --> O

    O --> P["VpnController starts<br/>BeOfflineVpnService"]
    P --> Q["Foreground VPN Service"]
    Q --> R["Builder.addAllowedApplication(pkg)<br/>for blocked apps only"]
    R --> S["Android routes only selected apps<br/>into local VPN tunnel"]
    S --> T["Packet Drop Loop reads tun fd<br/>and discards packets"]
    T --> U["Selected Apps appear offline<br/>Rest of phone stays online"]

    K --> V{"Rule Type?"}
    V -->|Scheduled| W["RuleScheduler registers<br/>start/stop alarms + WorkManager backup"]
    V -->|Timer| X["RuleScheduler sets<br/>exact timer stop + WorkManager backup"]
    V -->|Permanent| Y["No future stop needed"]

    W --> Z["ScheduleAlarmReceiver"]
    X --> Z
    Z --> O

    Q --> AA["VpnResilienceScheduler<br/>health checks + recovery"]
    AA --> AB["VpnHealthWorker checks<br/>VPN is still running"]
    AA --> AC["VpnRecoveryReceiver restarts VPN<br/>after unexpected stop"]
    AD["BootReceiver"] --> AE["Restore schedules + active packages<br/>after reboot or app update"]
    AE --> O

    Q --> AF["BlockedTrafficAlertManager"]
    AF --> AG["Optional BeOffline alert when<br/>blocked app attempts internet access"]

    C --> AH["Optional: Notification Access"]
    AH --> AI["BlockedAppNotificationListenerService"]
    AI --> AJ["Dismiss notifications from<br/>currently blocked apps when possible"]

    B --> AK["Support / Report Issue"]
    AK --> AL["IssueReporter -> Firebase Crashlytics"]

    style B fill:#151a2d,stroke:#7c83ff,color:#ffffff
    style Q fill:#1d2340,stroke:#7c83ff,color:#ffffff
    style U fill:#123126,stroke:#34d399,color:#ffffff
    style I fill:#1b1f35,stroke:#9aa4ff,color:#ffffff
    style W fill:#2a2145,stroke:#a78bfa,color:#ffffff
    style X fill:#2a2145,stroke:#a78bfa,color:#ffffff
    style AI fill:#2b233d,stroke:#a78bfa,color:#ffffff
    style AL fill:#2a2234,stroke:#f472b6,color:#ffffff
```

## Suggested framing for LinkedIn

- Problem: most focus tools mute noise, but the app still stays connected.
- Core idea: BeOffline uses Android's local VPN framework to make selected apps truly offline.
- Technical angle: Compose UI + Room + Hilt + WorkManager + AlarmManager + VpnService + optional NotificationListenerService.
- Reliability angle: scheduled recovery, boot restore, health monitoring, and background protection guidance.
