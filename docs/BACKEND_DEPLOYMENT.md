# Deploying the BeOffline API

How to get `backend/` onto the OCI VM and live. Written against the tree at commit `7103159`
(2026-10-18).

**Scope.** This guide deploys the **backend only**. Today's work was all Android-side — the
redesigned UI and the VPN IPv6 fix ship through a Play release, not through here. What this
deployment actually puts live is the backend work that has been committed but never deployed:
the data-only FCM fix, single-partner enforcement, the activity/error logging middleware,
`DELETE /api/account`, and the public `/privacy` and `/account-deletion` pages. Until this
runs, approvals still fail in production when the requester's app is backgrounded.

---

## Before you touch anything

### ⚠️ One thing in the working tree must not ship

`backend/src/BeOffline.Api/appsettings.json` currently has:

```json
"ApproverActivationCooldownHours": 0
```

That is a local testing change — it switches off the anti-puppet cooldown, which is the rule
stopping someone pairing with a second account they control and instantly approving their own
unlocks. **Production wants 12.** Either revert it before you build, or make sure
`appsettings.Production.json` on the VM sets it back. Check with:

```bash
git diff -- backend/src/BeOffline.Api/appsettings.json
```

### Secrets never leave the VM

The committed `appsettings.json` must keep `Password=CHANGE_ME`. The real Postgres password
lives only in `appsettings.Production.json` on the VM, which is gitignored. A real password
has reached the working tree once before — check `git diff` before every commit that touches
this file.

### What you need on hand

| Thing | Where it lives |
|---|---|
| SSH access to the OCI VM | your key |
| Postgres role + database | already on the VM |
| Firebase service-account JSON | `/etc/beoffline/firebase-service-account.json` on the VM, mode `600` |
| Cloudflare DNS for the API subdomain | proxied (orange cloud) → VM public IP |

---

## 1. Build and test locally

```bash
cd backend && dotnet test
```

28 tests should pass (WebApplicationFactory + SQLite in-memory, fake auth via `X-Test-Uid`).
Do not deploy on a red suite — the request-flow and group-quorum tests are the ones covering
the atomic first-responder claim, which is the piece most likely to corrupt state if broken.

Then publish a self-contained-enough build:

```bash
cd backend
dotnet publish src/BeOffline.Api/BeOffline.Api.csproj -c Release -o ./publish
```

`backend/publish/` is gitignored. .NET 8 runtime must be installed on the VM — this is a
framework-dependent publish.

---

## 2. Ship it

```bash
# From your machine, with the API stopped on the far side during the swap.
rsync -az --delete backend/publish/ beoffline@<VM_HOST>:/opt/beoffline/api-next/
```

Then on the VM:

```bash
sudo systemctl stop beoffline-api
sudo rm -rf /opt/beoffline/api-prev
sudo mv /opt/beoffline/api /opt/beoffline/api-prev     # keep one version back
sudo mv /opt/beoffline/api-next /opt/beoffline/api
sudo chown -R beoffline:beoffline /opt/beoffline/api
sudo systemctl start beoffline-api
```

Keeping `api-prev` is what makes the rollback at the bottom of this page a thirty-second job.

---

## 3. Configuration on the VM

`/opt/beoffline/api/appsettings.Production.json` — **not in git**, mode `600`, owned by the
service user:

```json
{
  "ConnectionStrings": {
    "Postgres": "Host=127.0.0.1;Database=beoffline;Username=beoffline;Password=<REAL_PASSWORD>"
  },
  "Firebase": {
    "ProjectId": "beoffline-36d68",
    "ServiceAccountJsonPath": "/etc/beoffline/firebase-service-account.json"
  },
  "Accountability": {
    "ApproverActivationCooldownHours": 12,
    "RemovalCooldownHours": 24
  },
  "Database": { "AutoMigrate": true }
}
```

Kestrel binds `http://127.0.0.1:5080` (from the committed `appsettings.json`) — **loopback
only, on purpose**. Nothing reaches it except through nginx.

---

## 4. systemd unit

`/etc/systemd/system/beoffline-api.service`:

```ini
[Unit]
Description=BeOffline accountability API
After=network.target postgresql.service
Wants=postgresql.service

[Service]
WorkingDirectory=/opt/beoffline/api
ExecStart=/usr/bin/dotnet /opt/beoffline/api/BeOffline.Api.dll
Restart=always
RestartSec=5
User=beoffline
Environment=ASPNETCORE_ENVIRONMENT=Production
Environment=DOTNET_PRINT_TELEMETRY_MESSAGE=false
# The service reads a secrets file and writes nothing outside its own directory.
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now beoffline-api
sudo journalctl -u beoffline-api -f
```

---

## 5. nginx

The one thing that is easy to get wrong here is **SignalR**. `/hubs/accountability` is a
WebSocket, and without the upgrade headers it silently falls back to long-polling or fails
outright — the app keeps working because FCM is the safety net, so you will not notice until
you wonder why realtime is never realtime.

`/etc/nginx/sites-available/beoffline-api`:

```nginx
server {
    listen 443 ssl http2;
    server_name <API_SUBDOMAIN>;

    ssl_certificate     /etc/letsencrypt/live/<API_SUBDOMAIN>/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/<API_SUBDOMAIN>/privkey.pem;

    location / {
        proxy_pass         http://127.0.0.1:5080;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }

    # SignalR: WebSocket upgrade, and a read timeout long enough that an idle
    # hub connection is not cut every minute.
    location /hubs/ {
        proxy_pass         http://127.0.0.1:5080;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade    $http_upgrade;
        proxy_set_header   Connection "upgrade";
        proxy_set_header   Host       $host;
        proxy_read_timeout 100s;
        proxy_send_timeout 100s;
    }
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

**Cloudflare:** the subdomain is proxied. Make sure **WebSockets** is enabled in the
Cloudflare dashboard (Network → WebSockets) or `/hubs/` will not connect regardless of what
nginx says. SSL mode should be **Full (strict)** given there is a real certificate on origin.

---

## 6. Database migrations

`Database:AutoMigrate` is `true`, so `Database.Migrate()` runs on startup
(`Program.cs:84-87`) and applies anything pending. For a normal deploy you do nothing.

**Back the database up first anyway** — a migration that half-applies is the one failure
on this page you cannot undo by swapping a directory back:

```bash
pg_dump -U beoffline -Fc beoffline > ~/beoffline-$(date +%F-%H%M).dump
```

To see what would run before it runs, from your machine against a copy:

```bash
cd backend && dotnet ef migrations list --project src/BeOffline.Api
```

The two SQL scripts in `backend/scripts/` (`wipe-test-data.sql`,
`wipe-activity-keep-setup.sql`) are **testing tools**. They empty application tables while
leaving `__EFMigrationsHistory` alone. Never run them against production.

---

## 7. Verify

```bash
curl -s https://<API_SUBDOMAIN>/health                 # {"status":"ok"}
curl -s -o /dev/null -w '%{http_code}\n' \
     https://<API_SUBDOMAIN>/api/pairings               # 401 — the auth gate is on
curl -s https://<API_SUBDOMAIN>/privacy | head -5       # the legal page renders
curl -s https://<API_SUBDOMAIN>/account-deletion | head -5
```

A `200` on `/api/pairings` means the Firebase bearer gate is not working. Stop and fix that
before anything else.

Then the one that actually matters, on two devices:

1. Pair device A with device B.
2. On A, block an app and ask B to unlock it.
3. **Background the app on A.** This is the whole point — the old bug was that FCM sent
   notification+data, so `onMessageReceived` never fired when backgrounded, the allowance was
   never inserted, and A stayed locked no matter what B pressed.
4. Approve on B. A should unlock.

If that works, the deployment did what it was for.

Also confirm in Firebase Console that all three SHA-1s are registered (debug, upload, and the
Play App Signing key) — a missing one breaks Google Sign-In for that build only, which looks
like a backend fault and is not.

---

## 8. Rolling back

```bash
sudo systemctl stop beoffline-api
sudo rm -rf /opt/beoffline/api
sudo mv /opt/beoffline/api-prev /opt/beoffline/api
sudo systemctl start beoffline-api
```

This rolls back **code only**. Migrations already applied stay applied, which is fine as long
as each one is additive — and so far they all are. If you ever write a destructive migration,
this rollback stops being safe and you restore the `pg_dump` instead.

---

## Notes

- **There is no CI for the backend yet.** `.github/workflows/` has no .NET job, so nothing
  builds or tests `backend/` on push. Path-filtered CI was the plan; it has not been written.
  Until it is, `dotnet test` before publish is the only gate.
- **Redis is not deployed and is not needed.** It was cut from M3 deliberately — single
  instance, no SignalR backplane required. If you ever run more than one API instance, that
  changes.
- **Secret hygiene on every deploy:** `appsettings.Production.json` mode `600`, the Firebase
  service-account JSON mode `600`, and neither one ever in git.
