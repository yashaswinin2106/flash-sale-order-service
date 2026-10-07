# Failure tests

Each test breaks one part of the system in the middle of a sale and then checks that the books
still balance. They run the full sale (`loadtest/flash-sale.js`: reserve, order, poll for the
payment result) with 5,000 buyers, 200 at a time, competing for 100 units.

```bash
docker compose up -d --build
loadtest/break-it.sh kill-order-service   # or flush-redis, stop-kafka, decline-all
```

The script resets the data, starts the sale, breaks something 3 seconds in, waits until no order
is `PENDING_PAYMENT`, and then runs `loadtest/check.sh`. The checks that must hold after every test:

| Check | Must be |
|---|---|
| `available_stock` + orders that are pending or confirmed | `total_stock` (100) |
| Orders with the same user and idempotency key | 0 |
| Orders with more than one payment | 0 |
| Settled orders with no payment row | 0 |
| Redis `stock:1` once no reservation is open | Postgres `available_stock` |

All four tests passed every check. Full output is in `loadtest/results/break-*.log`.

## Kill order-service mid-sale

`docker compose kill order-service`, wait 10 seconds, start it again.

**Expected.** Buyers whose requests were in flight get errors. Units held by reservations that
are never turned into orders come back when the reservation hold runs out. Nothing is sold twice.

**Observed.** The kill landed while buyers were still reserving: 51 reservations had been made.
Every other buyer got a connection error while the service was down. k6 does not wait for the
service to come back, so the sale was over before the restart. No order had been committed. The
test runs with a 30 second hold, and after it ran out the sweeper released all 51 reservations:

```
orders                     0
available_stock          100   accounted 100
redis stock:1            100   (open reservations 0)
```

**Known gap.** Placing an order claims the reservation in Redis first and then commits the order
in Postgres. If the process dies between the two, the reservation is marked used but no order
exists. The unit is then missing from the Redis counter for good, and a retry with the same
idempotency key gets "reservation already used". This undersells and never oversells: Postgres
still has the unit. This run did not hit that window. A fix would be a periodic job that sets the
counter to Postgres `available_stock` minus the units held by open reservations.

## Flush Redis mid-sale

`redis-cli FLUSHALL` while the sale is running.

**Expected.** The next reservation finds no stock counter and rebuilds it from Postgres. Open
reservations are lost, so buyers holding one get an error when they order. Postgres stays the
source of truth: it rejects anything past its own stock.

**Observed.** The flush removed the counter and the 100 open reservations. The counter was rebuilt
with 100 units (Postgres had not sold any yet), so 216 reservations were made in total. The 100
buyers whose reservations were wiped got "not found" when ordering. Of the 116 orders placed,
100 were confirmed and 16 were declined by the payment worker, and those 16 units were sold again.

```
CONFIRMED                100
FAILED                    16
available_stock            0   accounted 100
redis stock:1              0
```

## Stop Kafka for 60 seconds

`docker compose stop kafka`, wait 60 seconds, start it again.

**Expected.** Orders are still accepted, because they are saved in Postgres before the event is
published. Publishing fails fast (`max.block.ms=2000`), so the HTTP request does not hang. When
Kafka is back, the recovery job republishes `order.created` for orders that have been pending for
more than 2 minutes. The worker charges each order at most once, so any duplicate events are harmless.

**Observed.** All 100 orders were accepted. None settled within the buyers' 30 second polling
window, so k6 counted all 100 as unsettled. Every order settled 63 seconds after the sale ended,
with no duplicate payments. The 5 declined units went back to stock, but the sale was already
over, so nobody bought them.

```
CONFIRMED                 95
FAILED                     5
available_stock            5   accounted 100
redis stock:1              5
```

## Decline every payment

The payment worker restarted with `DECLINE_RATE=1`.

**Expected.** Every order fails. Each failed order returns its unit to Postgres (in the same
transaction that marks it `FAILED`) and to the Redis counter, so the unit can be sold again, and
it is declined again. At the end all 100 units are back.

**Observed.** 196 orders were placed and all 196 were declined. Some units were sold and returned
several times.

```
FAILED                   196
available_stock          100   accounted 100
redis stock:1            100   (open reservations 0)
```

## Not covered here

- **Payment worker crashing after it records a payment but before it publishes the result.**
  Starting the worker with `HALT_AFTER_RECORD=true` triggers this. On redelivery the worker finds
  the stored payment and publishes that result instead of charging again. The automated test
  `PaymentWorkerTest.redeliveredMessageDoesNotChargeTwice` covers that path; this script does not.
- **Postgres going down.** Every order needs Postgres, so the service returns errors until it is back.
