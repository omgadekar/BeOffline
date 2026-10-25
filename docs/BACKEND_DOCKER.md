# Deploying the BeOffline API with Docker

Build the image on your machine, push it to the private Docker Hub repo
`builtbyom/sideprojects`, pull it on the VM, run it. Replaces the
`dotnet publish` + `scp -r publish/*` path in
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

## One repo, several projects

Docker Hub has no nested paths. A repository holds **tags**, not folders, so
`builtbyom/sideprojects/beoffline-api` cannot exist. Projects sharing one
private repo share its tag namespace, which means the project name has to live
in the tag:

```
builtbyom/sideprojects:beoffline-api-1.1.4
                       └── APP ──┘ └VERSION┘
```

| | |
|---|---|
| `IMAGE` | `builtbyom/sideprojects` — the repo |
| `APP` | `beoffline-api` — fixed per project |
| `VERSION` | `1.1.4` — the only line a deploy changes |

Keep the `<app>-<version>` shape for anything else that lands in this repo.
Without a prefix the first project to push `:1.2.0` silently takes a tag another
one wanted, and on a shared repo that is a real collision rather than a
hypothetical one.

**No `latest`, ever.** It cannot be rolled back to, because it means something
different after the next push — and in a shared repo it is ambiguous about
*which project* it even refers to. `VERSION` has no default in
`docker-compose.yml`: leaving it unset fails loudly instead.

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

### 1.1 The repository

`builtbyom/sideprojects`, **Private**. If it does not exist yet:
hub.docker.com → **Repositories → Create repository**, name `sideprojects`,
visibility **Private**.

### 1.2 Two access tokens, not your password

**Account settings → Personal access tokens → Generate new token.**

| Where | Description | Permission |
|---|---|---|
| Your Windows machine | `sideprojects-build` | **Read & Write** |
| The VM | `beoffline-vm-pull` | **Read-only** |

The token is shown once.

> **Worth knowing on a shared repo.** Unless your Docker Hub plan offers
> repository-scoped tokens, a personal access token covers **every** private
> repo on the account. The VM's token is read-only, so the worst case is that a
> compromised VM can pull your other side projects' images — not push to them.
> Scope it to `sideprojects` if your plan lets you.

### 1.3 Log in at both ends

On Windows:

```cmd
docker login -u builtbyom
```

Paste the **Read & Write** token as the password.

On the VM, after Part 2 installs Docker:

```bash
docker login -u builtbyom
chmod 600 ~/.docker/config.json
```

Paste the **Read-only** token.

> `docker login` writes the token base64-encoded, not encrypted, into
> `~/.docker/config.json`. On the VM that is a read-only token, which is the
> point.

---

## Part 2 — The VM, once

### 2.1 Install Docker

```bash
ssh ubuntu@<VM_IP>
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
```

**Log out and back in.** Group membership is applied at login, so the shell
that ran `usermod` does not have it, and neither does any session opened before
it. Then confirm:

```bash
docker ps
```

`docker ps` and not `docker --version`: the version command only prints the
client's own number and never touches the daemon, so it answers happily from a
session with no access at all. `docker ps` talks to the socket, which is the
thing being checked. If it says

```
permission denied while trying to connect to the docker API at unix:///var/run/docker.sock
```

the session still predates the group change — reconnect, or `newgrp docker` to
get it in the shell you are already in.

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
sudo mkdir -p /opt/beoffline-docker && sudo chown ubuntu:ubuntu /opt/beoffline-docker
```

Both halves matter. `/opt` is root-owned, so the `mkdir` needs `sudo` — and a
directory made that way belongs to root, which `scp` cannot write into because
it runs as `ubuntu` with no way to elevate. The symptom is
`dest open "/opt/beoffline-docker/docker-compose.yml": Permission denied`, from
the copy rather than from the `mkdir` that caused it.

From Windows, send the compose file:

```cmd
scp D:\BeOffline\backend\docker-compose.yml ubuntu@<VM_IP>:/opt/beoffline-docker/
```

Check it landed, because the next command that needs it reports its absence as
`no configuration file provided: not found`, which says nothing about the copy:

```bash
ls -la /opt/beoffline-docker
```

Then on the VM, `nano /opt/beoffline-docker/.env`:

```ini
IMAGE=builtbyom/sideprojects
APP=beoffline-api
VERSION=1.1.4
APP_UID=1000
APP_GID=1000
```

```bash
chmod 600 /opt/beoffline-docker/.env
```

`VERSION` is the only line that changes on a normal deploy.

Now check what those five lines actually resolve to, rather than trusting that
they do:

```bash
docker compose config | grep image:
# image: builtbyom/sideprojects:beoffline-api-1.1.4
```

`APP` has to match the tag prefix you pushed, character for character —
`beoffline.api` instead of `beoffline-api` is not a typo the registry can
forgive, and it surfaces as a `not found` on the pull that reads like a missing
image rather than a wrong name.

And confirm the UID can read what it is about to be handed. This is the one
that goes wrong quietly:

```bash
stat -c '%u %g %a %n' /opt/beoffline-api/appsettings.Production.json                       /etc/beoffline/firebase-service-account.json
```

The owner column of each must be `APP_UID`, or the file must be readable by
`APP_GID`. Owned by root, or by a different user, and the container will start,
serve, answer `/health` and fall back to `NullPushService` — push dead, nothing
on the surface saying so.

---

## Part 3 — Build and push, every deploy

On Windows, with Docker Desktop running:

```cmd
cd D:\BeOffline\backend
dotnet test
```

Tests first — there is no CI for the backend, so this is the only gate.

```cmd
docker build --platform linux/amd64 -t builtbyom/sideprojects:beoffline-api-1.1.4 .
docker push builtbyom/sideprojects:beoffline-api-1.1.4
```

`--platform linux/amd64` is not optional if your machine is ARM — the image has
to match the VM, and a wrong-architecture image pushes fine and then fails at
`docker run` with `exec format error`.

A second tag costs nothing and makes a rollback target readable:

```cmd
docker tag builtbyom/sideprojects:beoffline-api-1.1.4 builtbyom/sideprojects:beoffline-api-dd0728b
docker push builtbyom/sideprojects:beoffline-api-dd0728b
```

---

## Part 4 — The first cutover

Back up the database first. `Database:AutoMigrate` is on and a dump is the one
thing a file copy cannot undo:

```bash
mkdir -p ~/backups
sudo -u postgres pg_dump beoffline | gzip > ~/backups/beoffline-$(date +%F-%H%M).sql.gz
```

The `mkdir` is not redundant on a rebuilt VM. The shell sets up the redirect
before `pg_dump` runs, so a missing directory fails the line before the dump
has started — and the error names the `.sql.gz` path rather than the directory,
which reads like the dump failed when nothing was ever attempted.

`BACKEND_DEPLOYMENT.md` says a nightly cron already writes here. Confirm that
came across with the machine rather than assuming it:

```bash
crontab -l 2>/dev/null; sudo crontab -l 2>/dev/null; ls -la ~/backups
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

Live traffic is still on 5080 through systemd; this only proves the image runs,
reads its config and can see Postgres.

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
docker build --platform linux/amd64 -t builtbyom/sideprojects:beoffline-api-<new> .
docker push builtbyom/sideprojects:beoffline-api-<new>
```

```bash
ssh ubuntu@<VM_IP>
cd /opt/beoffline-docker
sed -i 's/^VERSION=.*/VERSION=<new>/' .env
docker compose pull && docker compose up -d
docker compose ps && curl -s http://127.0.0.1:5080/health
```

Four lines, and the old image is still on the VM.

### Rolling back

```bash
sed -i 's/^VERSION=.*/VERSION=<previous>/' .env
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
# This project's tags only — the repo holds other projects too.
docker image ls builtbyom/sideprojects --filter 'reference=*:beoffline-api-*'
docker image rm builtbyom/sideprojects:beoffline-api-<old>
journalctl -t beoffline-api -f
```

Prefer removing old tags by name over `docker image prune -a`, which does not
know one project in this repo from another.

---

## Checklist

**Once**
- [ ] `builtbyom/sideprojects` exists and is **Private**
- [ ] Read & Write token on Windows, Read-only token on the VM
- [ ] `docker login -u builtbyom` done at both ends; `chmod 600 ~/.docker/config.json` on the VM
- [ ] Docker installed on the VM, `ubuntu` in the `docker` group
- [ ] `id -u` / `id -g` recorded
- [ ] `/opt/beoffline-docker` created **and owned by `ubuntu`**
- [ ] `docker-compose.yml` copied to `/opt/beoffline-docker/`
- [ ] `.env` written with IMAGE / APP / VERSION / APP_UID / APP_GID, `chmod 600`

**Every deploy**
- [ ] `dotnet test` green
- [ ] built `--platform linux/amd64`, tagged `beoffline-api-<version>`
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
