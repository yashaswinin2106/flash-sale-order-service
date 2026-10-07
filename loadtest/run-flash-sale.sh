#!/usr/bin/env bash
# Runs the sale twice: with the Redis reservation step (flash-sale.js) and without it
# (direct-order.js, orders straight to Postgres). Saves the k6 summaries in results/.
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

# Waits until no order is PENDING_PAYMENT, so the counts below are final.
wait_for_settled() {
  for _ in $(seq 1 60); do
    pending=$(docker compose exec -T postgres psql -tA -U flashsale -d flashsale \
      -c "SELECT count(*) FROM orders WHERE status = 'PENDING_PAYMENT'")
    [ "$pending" = "0" ] && return 0
    sleep 2
  done
  echo "orders still pending" >&2
}

k6() {
  MSYS_NO_PATHCONV=1 docker run --rm --network "$NETWORK" -v "$SCRIPTS_DIR:/scripts" grafana/k6 run \
    -q -e BASE_URL=http://order-service:8080 "$@"
}

run() {
  local name="$1" reservations="$2" script="$3"
  echo "=== $name ==="
  RESERVATIONS_ENABLED="$reservations" docker compose up -d --force-recreate order-service
  wait_for_service

  loadtest/reset.sh
  k6 -e BUYERS=2000 -e VUS="$VUS" "/scripts/$script" > /dev/null
  wait_for_settled

  loadtest/reset.sh
  k6 -e BUYERS="$BUYERS" -e VUS="$VUS" --summary-export "/scripts/results/$name.json" "/scripts/$script"
  wait_for_settled
  loadtest/check.sh
}

run sale-with-reservations true flash-sale.js
run sale-without-reservations false direct-order.js
