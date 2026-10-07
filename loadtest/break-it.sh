#!/usr/bin/env bash
# Breaks one part of the system in the middle of a sale (flash-sale.js, reservations on),
# waits for every order to settle, then prints the correctness checks.
#
# Usage: loadtest/break-it.sh kill-order-service | flush-redis | stop-kafka | decline-all
set -euo pipefail
cd "$(dirname "$0")/.."

scenario="${1:?usage: loadtest/break-it.sh kill-order-service|flush-redis|stop-kafka|decline-all}"
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

# Longer than in the other scripts: after a Kafka outage the recovery job only republishes
# orders that have been pending for 2 minutes.
wait_for_settled() {
  for _ in $(seq 1 150); do
    pending=$(docker compose exec -T postgres psql -tA -U flashsale -d flashsale \
      -c "SELECT count(*) FROM orders WHERE status = 'PENDING_PAYMENT'")
    [ "$pending" = "0" ] && return 0
    sleep 2
  done
  echo "orders still pending after 5 minutes: $pending" >&2
}

# k6 logs one warning per failed request, thousands while a service is down; the summary counts them.
sale() {
  MSYS_NO_PATHCONV=1 docker run --rm --network "$NETWORK" -v "$SCRIPTS_DIR:/scripts" grafana/k6 run \
    -q --log-output=none -e BASE_URL=http://order-service:8080 -e BUYERS="$BUYERS" -e VUS="$VUS" \
    --summary-export "/scripts/results/break-$scenario.json" /scripts/flash-sale.js
}

if [ "$scenario" = decline-all ]; then
  DECLINE_RATE=1 docker compose up -d --force-recreate payment-worker > /dev/null 2>&1
else
  docker compose up -d payment-worker > /dev/null 2>&1
fi
# After the worker: starting it also starts order-service, with the default settings.
# A short hold for the crash test, so the reservations its buyers abandon are released during the run.
hold=5m
[ "$scenario" = kill-order-service ] && hold=30s
RESERVATIONS_ENABLED=true RESERVATION_HOLD="$hold" docker compose up -d order-service > /dev/null 2>&1
wait_for_service
loadtest/reset.sh

echo "=== $scenario ==="
sale &
sale_pid=$!
sleep 3

case "$scenario" in
  kill-order-service)
    echo ">>> killing order-service"
    docker compose kill order-service > /dev/null 2>&1
    sleep 10
    docker compose start order-service > /dev/null 2>&1
    wait_for_service
    echo ">>> order-service is back"
    ;;
  flush-redis)
    echo ">>> flushing Redis"
    docker compose exec -T redis redis-cli FLUSHALL
    ;;
  stop-kafka)
    echo ">>> stopping Kafka for 60 seconds"
    docker compose stop kafka > /dev/null 2>&1
    sleep 60
    docker compose start kafka > /dev/null 2>&1
    echo ">>> Kafka is back"
    ;;
  decline-all) ;;
  *)
    echo "unknown scenario: $scenario" >&2
    exit 1
    ;;
esac

wait "$sale_pid" || true
started=$(date +%s)
wait_for_settled
echo ">>> all orders settled $(( $(date +%s) - started ))s after the sale ended"
loadtest/check.sh

if [ "$scenario" = kill-order-service ]; then
  echo ">>> waiting for abandoned reservations to expire"
  for _ in $(seq 1 60); do
    [ "$(docker compose exec -T redis redis-cli ZCARD reservations:expiring)" = "0" ] && break
    sleep 2
  done
  echo "redis stock:1            = $(docker compose exec -T redis redis-cli GET stock:1)"
  echo "postgres available_stock = $(docker compose exec -T postgres psql -tA -U flashsale -d flashsale \
    -c 'SELECT available_stock FROM products WHERE id = 1')"
  docker compose up -d order-service > /dev/null 2>&1
fi
if [ "$scenario" = decline-all ]; then
  docker compose up -d --force-recreate payment-worker > /dev/null 2>&1
fi
