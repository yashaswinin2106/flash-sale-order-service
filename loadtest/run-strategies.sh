#!/usr/bin/env bash
# Runs direct-order.js (reservations off) once per stock strategy and saves the k6 summaries in results/.
set -euo pipefail
cd "$(dirname "$0")/.."

BUYERS="${BUYERS:-5000}"
VUS="${VUS:-200}"
NETWORK="$(basename "$PWD" | tr '[:upper:]' '[:lower:]')_default"
SCRIPTS_DIR="$(pwd -W 2>/dev/null || pwd)/loadtest"

wait_for_service() {
  for _ in $(seq 1 60); do
    curl -sf localhost:8080/api/v1/products/1 > /dev/null && return 0
    sleep 2
  done
  echo "order-service did not start" >&2
  return 1
}

for strategy in ${STRATEGIES:-NAIVE PESSIMISTIC ATOMIC OPTIMISTIC}; do
  echo "=== $strategy ==="
  RESERVATIONS_ENABLED=false STOCK_STRATEGY="$strategy" docker compose up -d --force-recreate order-service
  wait_for_service

  # Warm-up pass so the JIT and connection pool are ready before the measured run.
  loadtest/reset.sh
  MSYS_NO_PATHCONV=1 docker run --rm --network "$NETWORK" -v "$SCRIPTS_DIR:/scripts" grafana/k6 run     -q -e BASE_URL=http://order-service:8080 -e BUYERS=2000 -e VUS="$VUS"     /scripts/direct-order.js > /dev/null
  loadtest/reset.sh

  MSYS_NO_PATHCONV=1 docker run --rm --network "$NETWORK" -v "$SCRIPTS_DIR:/scripts" grafana/k6 run \
    -q -e BASE_URL=http://order-service:8080 -e BUYERS="$BUYERS" -e VUS="$VUS" \
    --summary-export "/scripts/results/direct-$(echo "$strategy" | tr '[:upper:]' '[:lower:]').json" \
    /scripts/direct-order.js

  loadtest/check.sh
done
