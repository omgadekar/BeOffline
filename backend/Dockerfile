# BeOffline API — multi-stage build.
#
# Stage 1 restores and publishes with the SDK; stage 2 keeps only the runtime
# and the published output, so the shipped image carries no compiler, no NuGet
# cache and no source. Pinning the digest-free major.minor is deliberate: these
# tags get security patches, which is what you want for an image rebuilt on
# every deploy.

FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

# Restore before the rest of the source is copied, so a change to a .cs file
# does not invalidate the restore layer. Only the API project is needed — the
# test project is not part of the image.
COPY src/BeOffline.Api/BeOffline.Api.csproj src/BeOffline.Api/
RUN dotnet restore src/BeOffline.Api/BeOffline.Api.csproj

COPY src/ src/
RUN dotnet publish src/BeOffline.Api/BeOffline.Api.csproj \
    -c Release \
    -o /app/publish \
    --no-restore \
    /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS runtime
WORKDIR /app

# curl is here for the healthcheck below and nothing else. Without it the
# container has no way to answer "is the app actually serving?", only "is the
# process alive", and those are not the same question.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/publish .

# ASPNETCORE_URLS is deliberately not set: appsettings.json defines a Kestrel
# endpoint explicitly, and a configured endpoint outranks it, so setting it
# here would only produce a warning and be ignored. The override that works is
# the same configuration key, Kestrel__Endpoints__Http__Url, which is what
# compose sets. Left alone, the published appsettings.json binds
# 127.0.0.1:5080 — right for host networking, and reachable from nowhere on a
# bridge network, where that key has to become http://+:5080 instead.
ENV ASPNETCORE_ENVIRONMENT=Production \
    DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=false

EXPOSE 5080

# start-period covers the first run after a schema change, where EF applies
# migrations before the app starts serving. compose overrides this when the
# port is overridden.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:5080/health || exit 1

# Not root. The app writes nothing to disk — logs go to stdout, secrets are
# mounted read-only — so it has no reason to be.
#
# This is the standalone default. compose overrides it with the host UID that
# owns the mounted secrets: those files are mode 600, and a container running
# as anyone else cannot read them. That failure is quiet — the app starts, the
# Firebase service account is unreadable, and push falls back to the no-op
# service with nothing on the surface to say so.
USER $APP_UID

ENTRYPOINT ["dotnet", "BeOffline.Api.dll"]
