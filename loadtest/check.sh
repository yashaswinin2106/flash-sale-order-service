#!/usr/bin/env bash
# Prints the correctness numbers after a load test run.
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose exec -T postgres psql -U flashsale -d flashsale <<'SQL'
SELECT status, count(*) AS orders FROM orders GROUP BY status ORDER BY status;
-- Every unit is either still available, held by a pending order, or sold: this must equal total_stock.
SELECT p.total_stock,
       p.available_stock,
       count(o.*) FILTER (WHERE o.status IN ('PENDING_PAYMENT', 'CONFIRMED')) AS sold_or_pending,
       p.available_stock + count(o.*) FILTER (WHERE o.status IN ('PENDING_PAYMENT', 'CONFIRMED')) AS accounted
FROM products p LEFT JOIN orders o ON o.product_id = p.id
WHERE p.id = 1
GROUP BY p.id;
SELECT count(*) AS duplicate_orders FROM (
  SELECT user_id, idempotency_key FROM orders GROUP BY 1, 2 HAVING count(*) > 1) d;
SELECT count(*) AS orders_with_two_payments FROM (
  SELECT order_id FROM payments GROUP BY 1 HAVING count(*) > 1) p;
SELECT count(*) AS orders_without_payment FROM orders o
  WHERE o.status <> 'PENDING_PAYMENT' AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id);
SQL

echo "redis stock:1            = $(docker compose exec -T redis redis-cli GET stock:1)"
echo "redis open reservations  = $(docker compose exec -T redis redis-cli ZCARD reservations:expiring)"
