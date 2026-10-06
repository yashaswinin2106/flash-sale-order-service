#!/usr/bin/env bash
# Prints the correctness numbers after a load test run.
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose exec -T postgres psql -U flashsale -d flashsale <<'SQL'
SELECT status, count(*) AS orders FROM orders GROUP BY status ORDER BY status;
SELECT total_stock, available_stock FROM products WHERE id = 1;
SELECT count(*) AS duplicate_orders FROM (
  SELECT user_id, idempotency_key FROM orders GROUP BY 1, 2 HAVING count(*) > 1) d;
SELECT count(*) AS orders_with_two_payments FROM (
  SELECT order_id FROM payments GROUP BY 1 HAVING count(*) > 1) p;
SQL
