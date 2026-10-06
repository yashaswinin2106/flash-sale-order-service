#!/usr/bin/env bash
# Clears orders, payments and reservations and puts the seeded stock back to 100.
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose exec -T postgres psql -q -U flashsale -d flashsale -c \
  "TRUNCATE payments, orders; UPDATE products SET available_stock = total_stock, version = 0;"
docker compose exec -T redis redis-cli FLUSHALL > /dev/null
docker compose exec -T redis redis-cli SET stock:1 100 > /dev/null
