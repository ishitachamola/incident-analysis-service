---
documentType: HISTORICAL_INCIDENT
title: "INC-002: Order processing delays from Kafka consumer rebalance loop"
service: order-service
source: incidents/INC-002-order-consumer-lag.md
incidentId: INC-002
rootCause: Kafka consumer rebalance loop caused by slow batch processing
---

# INC-002: Order processing delays from Kafka consumer rebalance loop

**Service:** order-service
**Severity:** HIGH
**Duration:** 2 hours 10 minutes

## Symptoms

Consumer group lag for `order-processing-group` grew from near zero to over 18,000 messages. Logs
showed batch processing times exceeding 8 seconds, repeated consumer group rebalances, and
`CommitFailedException` entries. Orders were being processed, but minutes late. Customers reported
stale order status rather than outright failures.

## Investigation

Lag was distributed evenly across partitions, which ruled out a poison message or a hot key. Produced
message volume was consistent with the previous week, so this was not a traffic spike.

Processing time per batch had roughly quadrupled. The handler enriches each order with data from an
internal pricing service, and that service's p99 latency had risen sharply after its own release
earlier that day.

The rebalance loop was the compounding factor: batches exceeded `max.poll.interval.ms`, the broker
declared the consumer dead, partitions were revoked before offsets could be committed, and the same
records were reprocessed after reassignment. The consumer was doing the same work repeatedly and
falling further behind.

## Root cause

A latency regression in the downstream pricing service slowed the message handler enough to exceed
the poll interval, which triggered a rebalance loop that prevented the consumer group from making
forward progress.

## Resolution

`max.poll.records` was reduced from 500 to 100 so a batch could complete within the poll interval,
which broke the rebalance loop and let the consumer group start draining. The pricing service team
rolled back their release, restoring handler latency. Lag returned to baseline about 35 minutes
later.

## Follow-up actions

- Added alerting on consumer lag trend rather than an absolute threshold.
- Moved the pricing enrichment call behind a timeout so a slow dependency cannot stall a batch.
- Documented the relationship between `max.poll.records` and `max.poll.interval.ms`.

## Lessons

Consumer lag is often a symptom of a slow dependency rather than a Kafka capacity problem. Adding
consumers would have increased pressure on the failing pricing service and made things worse. Break
the rebalance loop before attempting any capacity change.
