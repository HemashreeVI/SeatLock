#!/usr/bin/env bash
# Starts the whole SeatLock stack in a Codespace / dev container and waits until the app is healthy.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "▶ Starting SeatLock (Postgres, Redis, Kafka + app)…"
# use the published image when it can be pulled, otherwise build from this checkout
if docker compose --profile app pull --quiet app; then
  docker compose --profile app up -d --no-build
else
  docker compose --profile app up -d --build
fi

for i in $(seq 1 90); do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "✅ SeatLock is up: open the forwarded port 8080 (Ports tab) → /login.html, /demo.html"
    exit 0
  fi
  sleep 4
done
echo "⚠️  The app is still starting. Check: docker compose logs -f app"
