#!/usr/bin/env bash
# Baseline verification. Needs Docker running (Testcontainers) and Node 24.
set -euo pipefail
cd "$(dirname "$0")/.."
(cd backend && ./gradlew test)
(cd frontend && npm ci && npm run lint && npm run typecheck && npm test -- --runInBand && npm run build)
