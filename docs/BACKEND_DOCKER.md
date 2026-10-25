# Deploying the BeOffline API with Docker

Build the image on your machine, push it to a **private Docker Hub repo**, pull
it on the VM, run it. Replaces the `dotnet publish` + `scp -r publish/*` path in
[`BACKEND_DEPLOYMENT.md`](BACKEND_DEPLOYMENT.md), which stays valid — you can go
back to it at any point, and the rollback step below is exactly that.

**Why.** `scp -r publish/*` overwrites in place over a running service: it never
deletes a file a later build dropped, and rollback means re-publishing from the
previous commit and hoping the leftovers do not matter. With an image, a deploy
is a tag and a rollback is the previous tag. The VM also stops needing
`aspnetcore-runtime-8.0` installed and kept current.

**Runtime stays .NET 8** — `sdk:8.0` to build, `aspnet:8.0` to run. Nothing here
moves the project off it.

---

## What this does not touch

Postgres, nginx, the Cloudflare origin certificate, `appsettings.Production.json`,
`/etc/beoffline/`, the systemd unit file, and the DNS. The container shares the
host's network stack, so `localhost` still means the host and nginx's
`proxy_pass http://127.0.0.1:5080` is unchanged.

The systemd unit is only **stopped**, never deleted. Starting it again is the
rollback of last resort.

---

## Part 1 — Docker Hub, once

### 1.1 Create the private repository

On hub.docker.com: **Repositories → Create repository**, name `beoffline-api`,
visibility **Private**. Its full name is `<your-dockerhub-user>/beoffline-api` —
that is the `IMAGE` value everywhere below.

### 1.2 Two access tokens, not your password

**Account settings → Personal access tokens → Generate new token.**

| Where | Description | Permission |
|---|---|---|
| Your Windows machine | `beoffline-build` | **Read & Write** |
| The VM | `beoffline-vm-pull` | **Read-only** |

The token is shown once. A read-only token on the server means a stolen VM
cannot push a poisoned image back to the repo, which is the whole reason for
two.

### 1.3 Log in at both ends

On Windows:

```cmd
docker login -u <your-dockerhub-user>
```

Paste the **Read & Write** token as the password.

On the VM, after Part 2 installs Docker:

```bash
docker login -u <your-dockerhub-user>
```

Paste the **Read-only** token.

> `docker login` writes the token base64-encoded (not encrypted) into
> `~/.docker/config.json`. On the VM that is a read-only token, which is the
> point. `chmod 600 ~/.docker/config.json`.

---

## Part 2 — The VM, once

### 2.1 Install Docker

```bash
ssh ubuntu@<VM_IP>
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
```

Log out and back in, then confirm:

```bash
docker --version && docker compose version
```

### 2.2 Find the UID that owns the secrets

```bash
id -u        # ubuntu → almost always 1000
id -g
```

Write these down. **This matters.** `appsettings.Production.json` and the
Firebase service-account JSON are mode `600`, owned by `ubuntu`. A container
running as anyone else cannot read them — and that failure is quiet: the app
starts, serves, answers `/health`, and silently falls back to `NullPushService`,
so push stops with nothing on the surface saying why.

### 2.3 Put the deploy files in place

The VM needs `docker-compose.yml` and a `.env`. It does **not** need the source.

```bash
mkdir -p /opt/beoffline-docker
```

From Windows, send the compose file:

```cmd
scp D:\BeOffline\backend\docker-compose.yml ubuntu@<VM_IP>:/opt/beoffline-docker/
```

Then on the VM, `nano /opt/beoffline-docker/.env`:

```ini
IMAGE=<your-dockerhub-user>/beoffline-api
TAG=1.1.4
APP_UID=1000
APP_GID=1000
```

```bash
chmod 600 /opt/beoffline-docker/.env
```

`TAG` is the only line that changes on a normal deploy.

---

## Part 3 — Build and push, every deploy

On Windows, with Docker Desktop running:

```cmd
cd D:\BeOffline\backend
dotnet test
```

Tests first — there is no CI for the backend, so this is the only gate.

```cmd
docker build --platform linux/amd64 -t <your-dockerhub-user>/beoffline-api:1.1.4 .
docker push <your-dockerhub-user>/beoffline-api:1.1.4
```

`--platform linux/amd64` is not optional if your machine is ARM — the image has
to match the VM, and a wrong-architecture image fails at `docker run` with an
`exec format error` after a successful push.

**Tag with a real version, never `latest`.** `latest` cannot be rolled back to,
because it means something different after the next push. Matching the app
version (`1.1.4`) keeps the two readable together; a commit sha works just as
well. To tag an image you already built:

```cmd
docker tag <your-dockerhub-user>/beoffline-api:1.1.4 <your-dockerhub-user>/beoffline-api:1f6d007
docker push <your-dockerhub-user>/beoffline-api:1f6d007
```

---

## Part 4 — The first cutover

Back up the database first. `Database:AutoMigrate` is on and a dump is the one
thing a file copy cannot undo:

```bash
sudo -u postgres pg_dump beoffline | gzip > ~/backups/beoffline-$(date +%F-%H%M).sql.gz
```

### 4.1 Smoke-test on a spare port, with systemd still serving

```bash
cd /opt/beoffline-docker
docker compose pull
API_PORT=5081 docker compose up -d
sleep 20
curl -s http://127.0.0.1:5081/health          # {"status":"ok"}
docker compose logs --tail 50 | grep -i firebase
```

Live traffic is still on 5080 through systemd; this is only proving the image
runs, reads its config and can see Postgres.

**`NullPushService` in those logs means the UID is wrong** — the container
cannot read `/etc/beoffline/firebase-service-account.json`. Fix `APP_UID` in
`.env` and repeat. Do not carry this into the cutover; push is the whole
accountability loop.

> **Only safe because this deploy has no migration.** Two instances against one
> database both run `SweepService` and both apply migrations at start. When a
> release *does* carry a schema change, skip this step and go straight to 4.2.

```bash
docker compose down
```

### 4.2 Switch over

```bash
sudo systemctl stop beoffline-api
docker compose up -d
docker compose ps                 # wait for "healthy", not just "running"
```

### 4.3 Verify, then disable the unit

```bash
curl -s http://127.0.0.1:5080/health
curl -s https://beoffline-api.askthepolicy.com/health
curl -s -o /dev/null -w '%{http_code}\n' https://beoffline-api.askthepolicy.com/api/pairing
```

The last must be **401**. A 200 means the Firebase bearer gate is not working —
stop and fix that before anything else.

Then the test that actually matters, on two devices: raise a group unlock
request on one, background the app, approve on the other, confirm it unlocks.
And for this release specifically: leave a group and confirm it is gone
immediately rather than tomorrow.

Only once all of that passes:

```bash
sudo systemctl disable beoffline-api
```

Stopped and disabled, not deleted. The unit file stays as the way back.

---

## Part 5 — Every deploy after that

```cmd
cd D:\BeOffline\backend && dotnet test
docker build --platform linux/amd64 -t <user>/beoffline-api:<new-tag> .
docker push <user>/beoffline-api:<new-tag>
```

```bash
ssh ubuntu@<VM_IP>
cd /opt/beoffline-docker
sed -i 's/^TAG=.*/TAG=<new-tag>/' .env
docker compose pull && docker compose up -d
docker compose ps && curl -s http://127.0.0.1:5080/health
```

Four lines, and the old image is still on the VM.

### Rolling back

```bash
sed -i 's/^TAG=.*/TAG=<previous-tag>/' .env
docker compose up -d
```

Seconds, with the exact bytes that were running before. If Docker itself is the
problem:

```bash
docker compose down
sudo systemctl start beoffline-api
```

That is the old deployment back, untouched.

### Housekeeping

```bash
docker image ls <user>/beoffline-api          # keep the last few tags
docker image prune -a --filter "until=720h"   # anything older than a month
journalctl -t beoffline-api -f                # logs, as before
```

---

## Checklist

**Once**
- [ ] Private repo `beoffline-api` created on Docker Hub
- [ ] Read & Write token on Windows, Read-only token on the VM
- [ ] `docker login` done at both ends; `chmod 600 ~/.docker/config.json` on the VM
- [ ] Docker installed on the VM, `ubuntu` in the `docker` group
- [ ] `id -u` / `id -g` recorded
- [ ] `docker-compose.yml` copied to `/opt/beoffline-docker/`
- [ ] `.env` written with IMAGE / TAG / APP_UID / APP_GID, `chmod 600`

**Every deploy**
- [ ] `dotnet test` green
- [ ] `docker build --platform linux/amd64` with a real version tag
- [ ] `docker push`
- [ ] `pg_dump` taken
- [ ] `docker compose pull && up -d`, `ps` shows **healthy**
- [ ] `/health` ok locally and through Cloudflare
- [ ] `/api/pairing` returns **401**
- [ ] no `NullPushService` in the logs
- [ ] two-device approval still unlocks

**First cutover only**
- [ ] smoke-tested on `API_PORT=5081` (skip if the release has a migration)
- [ ] `systemctl stop beoffline-api`
- [ ] everything above verified
- [ ] `systemctl disable beoffline-api` — stopped and disabled, never deleted

---

## Notes

- **Secrets never enter the image.** Both mounts are read-only and
  `.dockerignore` excludes `appsettings.Production.json` explicitly rather than
  relying on it being absent from git.
- **Loopback only.** With the host's network stack, binding `0.0.0.0` would
  publish the API straight to the internet — Docker writes its own iptables
  rules ahead of ufw, so a port can be open while the firewall reports it
  closed. `ASPNETCORE_URLS` pins it to `127.0.0.1`.
- **Bridge networking instead** needs three more changes: `Host=localhost`
  becomes `Host=host.docker.internal`, Postgres has to listen on `172.17.0.1`,
  and `pg_hba.conf` has to allow `172.17.0.0/16`. Worth it only if the VM
  starts running more than this one app — and then the published port must be
  written `127.0.0.1:5080:5080`, never `5080:5080`.
- **Still no CI for the backend.** `dotnet test` before the build is the gate.
