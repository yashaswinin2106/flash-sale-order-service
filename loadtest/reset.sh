#!/usr/bin/env bash
# Clears orders, payments and reservations and puts the seeded stock back to 100.
set -euo pipefail
cd "$(dirname "$0")/.."

# Skip order.created events left over from the last run. Their orders are about to be deleted,
# so the worker would only fail on them while new orders queue behind. The group must be
# inactive to move its offsets, so the worker is stopped first.
docker compose stop payment-worker > /dev/null 2>&1
MSYS_NO_PATHCONV=1 docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --group payment-worker --topic order.created \
  --reset-offsets --to-latest --execute > /dev/null

docker compose exec -T postgres psql -q -U flashsale -d flashsale -c \
  "TRUNCATE payments, orders; UPDATE products SET available_stock = total_stock, version = 0;"
docker compose exec -T redis redis-cli FLUSHALL > /dev/null
docker compose exec -T redis redis-cli SET stock:1 100 > /dev/null

docker compose start payment-worker > /dev/null 2>&1
for _ in $(seq 1 60); do
  MSYS_NO_PATHCONV=1 docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh     --bootstrap-server localhost:9092 --group payment-worker --describe --state 2>/dev/null | grep -q Stable && exit 0
  sleep 1
done
echo "payment-worker did not rejoin its consumer group" >&2
exit 1
