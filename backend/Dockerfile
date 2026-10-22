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

# The published appsettings.json binds Kestrel to 127.0.0.1, which inside a
# bridge-networked container means nothing outside it can connect. Binding all
# interfaces is safe here precisely because the container is the boundary:
# compose publishes the port on 127.0.0.1 of the host, so nginx still reaches
# it and the internet still does not.
ENV ASPNETCORE_ENVIRONMENT=Production \
    ASPNETCORE_URLS=http://+:5080 \
    DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=false

EXPOSE 5080

# start-period covers the first run after a schema change, where EF applies
# migrations before the app starts serving.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:5080/health || exit 1

# Not root. The app writes nothing to disk — logs go to stdout, secrets are
# mounted read-only — so it has no reason to be.
USER $APP_UID

ENTRYPOINT ["dotnet", "BeOffline.Api.dll"]
