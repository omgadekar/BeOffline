# Running the BeOffline API in Docker

An alternative to the publish-and-scp update path in
[`BACKEND_DEPLOYMENT.md`](BACKEND_DEPLOYMENT.md). Same VM, same nginx, same
Postgres, same certificate — only how the app process is started changes.

**Why bother.** Today a deploy is `scp -r publish/*` over a running service: it
overwrites in place, never deletes a file a later build dropped, and rollback
means re-publishing from the previous commit and hoping the leftovers do not
matter. With an image, a deploy is a tag and a rollback is the previous tag.
The VM also stops needing the .NET runtime installed and kept current.

**What this is not.** It does not containerise Postgres, nginx or anything else
on the VM. One container, holding the API.

---

## What stays exactly as it is

| | |
|---|---|
| Postgres | on the host, `localhost`, untouched |
| nginx | `proxy_pass http://127.0.0.1:5080`, untouched |
| Cloudflare origin cert | `/etc/beoffline/`, untouched |
| `appsettings.Production.json` | `/opt/beoffline-api/`, untouched and still not in git |
| Firebase service account | `/etc/beoffline/firebase-service-account.json`, untouched |
| Logs | `journalctl -u docker CONTAINER_NAME=beoffline-api`, or `docker logs` |

This works because the container shares the host's network stack
(`network_mode: host`). On a bridge network `localhost` inside the container
would mean the container, and the connection string, Postgres's
`listen_addresses` and `pg_hba.conf` would all need changing. The variant at
the bottom covers that if you ever want it.

---

## One time: install Docker on the VM

```bash
ssh ubuntu@<VM_IP>
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
```

Log out and back in for the group to take effect, then:

```bash
docker --version && docker compose version
```

## One time: get the source onto the VM

The image is built on the VM, so the VM needs the repository. This replaces
needing the .NET SDK locally, not the other way round.

```bash
sudo apt-get install -y git
sudo mkdir -p /opt/beoffline-src && sudo chown ubuntu:ubuntu /opt/beoffline-src
git clone https://github.com/omgadekar/BeOffline.git /opt/beoffline-src
```

Private repository, or you would rather not put a credential on the VM? Build
the image on your own machine and ship it as a file instead — see
**Without git on the VM** below.

---

## Deploying

```bash
ssh ubuntu@<VM_IP>
cd /opt/beoffline-src && git pull
cd backend

# Tag with the commit so a rollback has something to name.
export TAG=$(git rev-parse --short HEAD)
docker compose build
docker compose up -d
```

First time only, once you have confirmed it serves, retire the systemd unit so
the two cannot both hold port 5080:

```bash
sudo systemctl stop beoffline-api
sudo systemctl disable beoffline-api
```

### Verify

```bash
docker compose ps                 # healthy, not just running
curl -s http://127.0.0.1:5080/health
curl -s https://beoffline-api.askthepolicy.com/health
```

Then the checks from `BACKEND_DEPLOYMENT.md` §5 — the 401 on `/api/pairing`
especially, and that `NullPushService` does not appear in the logs:

```bash
docker compose logs --tail 50 | grep -i firebase
```

`NullPushService` means the service-account JSON is not readable at the path in
`appsettings.Production.json`. Check the `/etc/beoffline` mount first.

### Rolling back

This is the part that was previously painful:

```bash
docker images beoffline-api            # pick the tag you want
TAG=<previous-short-sha> docker compose up -d
```

Seconds, and with the exact bytes that were running before. Keep the last few
images; `docker image prune -a --filter "until=720h"` clears anything older
than a month.

### Migrations

`Database:AutoMigrate` is on, so EF applies pending migrations when the
container starts — the same as under systemd. Take the dump first, same as
always:

```bash
sudo -u postgres pg_dump beoffline | gzip > ~/backups/beoffline-$(date +%F-%H%M).sql.gz
```

A container that keeps restarting right after a schema change is almost always
a migration failing. `docker compose logs --tail 100` will say which.

---

## Without git on the VM

Build locally, ship the image as a tarball. Needs Docker Desktop on Windows;
needs nothing on the VM but Docker.

```cmd
cd D:\BeOffline\backend
docker build -t beoffline-api:prod .
docker save beoffline-api:prod | gzip > beoffline-api.tar.gz
scp beoffline-api.tar.gz ubuntu@<VM_IP>:/tmp/
ssh ubuntu@<VM_IP> "gunzip -c /tmp/beoffline-api.tar.gz | docker load && rm /tmp/beoffline-api.tar.gz"
```

Then on the VM, with `docker-compose.yml` copied to `/opt/beoffline-api/`,
comment out the `build:` block and run `TAG=prod docker compose up -d`.

A registry — GHCR, or OCI's own — is the better version of this once it is
worth setting up: `docker push` locally, `docker pull` on the VM, no tarballs.

---

## Bridge network instead of host

More isolation, at the cost of three changes. Worth it if the VM ever runs more
than this one app.

In `docker-compose.yml`, replace `network_mode: host` and the `ASPNETCORE_URLS`
override with:

```yaml
    ports:
      - "127.0.0.1:5080:5080"      # the 127.0.0.1 is load-bearing, see below
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

Then, because `localhost` now means the container:

1. In `appsettings.Production.json`, `Host=localhost` becomes
   `Host=host.docker.internal`.
2. Postgres has to listen on the Docker bridge. In `postgresql.conf`:
   `listen_addresses = 'localhost,172.17.0.1'`.
3. `pg_hba.conf` has to allow it:
   `host beoffline beoffline 172.17.0.0/16 scram-sha-256`.

**Write the port as `127.0.0.1:5080:5080`, never `5080:5080`.** The short form
binds `0.0.0.0`, and Docker inserts its own iptables rules ahead of ufw — so
the short form publishes the API to the internet from behind a firewall that
still reports the port as closed.

---

## Notes

- The image runs as a non-root user and writes nothing to disk. Both mounts are
  read-only; everything secret stays on the VM.
- `.dockerignore` excludes `appsettings.Production.json` explicitly. It is not
  in git, but nothing should depend on that twice.
- The healthcheck gives 40s of start period, which covers a migration running
  before the app begins serving.
- There is still no CI for the backend. `cd backend && dotnet test` before you
  push remains the only gate.
