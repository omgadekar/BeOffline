# Deploying the BeOffline API

How to ship `backend/` to the OCI VM it already runs on.

**This is the update path.** For first-time setup — provisioning the VM, creating the database,
Cloudflare DNS, the origin certificate, nginx, and the two OCI firewall layers — the record is
the **[BeOffline API Deployment Runbook](https://claude.ai/artifact/SzDtGGZG7MjpzpyBZvka1E)**
artifact (2026-07-20), which is what the live deployment was actually built from. Everything
below matches it.

**Scope.** This deploys the backend only. The Android work ships through Play — see
`docs/RELEASE_BUILD.md`.

---

## Where everything lives

| | |
|---|---|
| Host | `beoffline-api.askthepolicy.com` — Cloudflare-proxied → OCI VM |
| App directory | `/opt/beoffline-api/` |
| Service user | `ubuntu` |
| systemd unit | `/etc/systemd/system/beoffline-api.service` (`beoffline-api`) |
| Production config | `/opt/beoffline-api/appsettings.Production.json` — **not in git** |
| Secrets directory | `/etc/beoffline/` — Firebase service account, Cloudflare origin cert + key |
| nginx site | `/etc/nginx/sites-available/beoffline-api` |
| Kestrel | `http://127.0.0.1:5080` — loopback only; nothing reaches it except nginx |
| TLS to origin | Cloudflare **Origin Certificate**, 15-year, at `/etc/beoffline/origin-cert.pem` — no renewal cron |
| Logs | `journalctl -u beoffline-api` (`-f` to follow) |

---

## Before you build

### ⚠️ One thing in the working tree must not ship

`backend/src/BeOffline.Api/appsettings.json` currently has:

```json
"ApproverActivationCooldownHours": 0
```

That is a local testing change — it switches off the anti-puppet cooldown, the rule stopping
someone pairing with a second account they control and instantly approving their own unlocks.
**Production wants 12.**

It also turns the backend test suite red: two `GroupFlowTests` cases assert that a new member
*cannot* approve immediately, and they fail with the cooldown at 0. Two expected failures are
a good place for a real one to hide. Moving the override into
`appsettings.Development.json` fixes both problems at once.

```bash
git diff -- backend/src/BeOffline.Api/appsettings.json
```

### Secrets never leave the VM

The committed `appsettings.json` keeps `Password=CHANGE_ME`. The real password lives only in
`appsettings.Production.json` on the VM. A real password has reached the working tree once
before — check `git diff` before every commit that touches this file.

---

## 1. Test

```bash
cd backend && dotnet test
```

41 tests, all passing — and they only do with the cooldown back at 12.

## 2. Publish

```bash
cd backend
dotnet publish src/BeOffline.Api/BeOffline.Api.csproj -c Release -o publish
```

`backend/publish/` is gitignored. This is a framework-dependent publish; the VM has
`aspnetcore-runtime-8.0` installed.

## 3. Back up the database

No schema change is pending, but `Database:AutoMigrate` is on and a dump is the one thing a
file copy cannot undo:

```bash
ssh ubuntu@<VM_IP> "sudo -u postgres pg_dump beoffline | gzip > ~/backups/beoffline-\$(date +%F-%H%M).sql.gz"
```

There is already a nightly 3 AM cron doing this into `/home/ubuntu/backups`.

## 4. Upload and restart

```bash
scp -r publish/* ubuntu@<VM_IP>:/opt/beoffline-api/
ssh ubuntu@<VM_IP> "sudo systemctl restart beoffline-api"
```

Migrations apply themselves on start (`Program.cs:84-87`).

> **A note on this step.** `scp -r publish/*` overwrites in place: it adds and replaces files
> but never deletes one that a later build dropped, and it writes while the service is still
> running. It has worked so far and it is what the live deployment uses. If you want it
> safer, stop the service first and use `rsync -az --delete` instead, which also makes
> rollback possible — keep the previous directory as `/opt/beoffline-api-prev` and a bad
> deploy becomes a thirty-second directory swap. Today, rollback means re-publishing from
> the previous commit.

## 5. Verify

```bash
curl -s https://beoffline-api.askthepolicy.com/health
# → {"status":"ok"}

curl -s -o /dev/null -w '%{http_code}\n' https://beoffline-api.askthepolicy.com/api/pairing
# → 401, the auth gate working

curl -s https://beoffline-api.askthepolicy.com/privacy | head -5
curl -s https://beoffline-api.askthepolicy.com/account-deletion | head -5
```

A `200` on `/api/pairing` means the Firebase bearer gate is not working. Stop and fix that
before anything else.

Confirm push is live rather than silently falling back to the no-op service:

```bash
ssh ubuntu@<VM_IP> "journalctl -u beoffline-api -n 50 | grep -i firebase"
```

No mention of `NullPushService` after a restart. That fallback only engages when the
service-account JSON is missing or unreadable.

### The test that actually matters

On two devices:

1. Pair device A with device B.
2. On A, block an app and ask B to unlock it.
3. **Background the app on A.** This is the whole point — the old bug was that FCM sent
   notification+data, so `onMessageReceived` never fired when backgrounded, the allowance was
   never inserted, and A stayed locked no matter what B pressed.
4. Approve on B. A should unlock.

---

## What this deploy ships

Everything committed since the last deploy, which includes work that has never been live:

- **Data-only FCM messages** — the fix for the approval flow above.
- **Single-1:1-partner enforcement.**
- **`DELETE /api/account`** plus the public `/privacy` and `/account-deletion` pages. Until
  these are live, the deletion URL in Play Console does not resolve, which is a policy
  violation rather than a bug.
- **Chat report and block endpoints** (`ChatController`), added for Play's user-generated
  content policy. **No migration**: moderation records are written into the existing
  `ActivityLogs` table with `Method = "REPORT"`, and are exempt from the 30-day log prune in
  `SweepService`.
- **Revised privacy page copy** naming Google as the processor for crash logs. Read
  `/privacy` once it is live and make sure you are happy with it — it is the URL Play uses.

**Deploy this before rolling out app 1.1.0.** The release build points at the live host, so a
stale API means sign-in and approvals fail for everyone who updates.

---

## When it breaks

The runbook artifact has the full table. The ones worth knowing by heart:

| Symptom | Cause |
|---|---|
| Cloudflare 521 | nginx down, or a firewall layer closed |
| Cloudflare 522 | OCI Security List missing 443 |
| Cloudflare 526 | Strict mode rejected the origin certificate — cert path or hostname |
| Too many redirects | Zone is on Flexible and the API's Strict configuration rule is missing |
| 500s / will not start | `journalctl -u beoffline-api -e` — usually the connection string or the service-account path |
| Chat only updates on refresh | WebSocket upgrade not passing through nginx; FCM still delivers |
| Push never arrives | Service-account JSON missing at the path in `appsettings.Production.json` |

---

## Notes

- **There is no CI for the backend.** `.github/workflows/` has no .NET job, so nothing builds
  or tests `backend/` on push. `dotnet test` before publish is the only gate.
- **Redis is not deployed and is not needed** — single instance, no SignalR backplane
  required. That changes if you ever run more than one API instance.
- **Secret hygiene:** `appsettings.Production.json`, the Firebase JSON and
  `origin-key.pem` all mode `600`, and none of them ever in git.
