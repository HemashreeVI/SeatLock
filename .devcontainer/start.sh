#!/usr/bin/env bash
# Starts the whole SeatLock stack in a Codespace / dev container and waits until the app is healthy.
set -euo pipefail
cd "$(dirname "$0")/.."

# Docker comes with the Codespaces image; give its daemon a moment to start.
if ! command -v docker >/dev/null 2>&1; then
  echo "❌ Docker isn't available in this container."
  echo "   Command Palette (Ctrl/Cmd+Shift+P) → 'Codespaces: Rebuild Container', then run: bash .devcontainer/start.sh"
  exit 1
fi
for _ in $(seq 1 30); do docker info >/dev/null 2>&1 && break; sleep 2; done

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
