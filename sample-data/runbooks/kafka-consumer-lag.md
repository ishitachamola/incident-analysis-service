---
documentType: RUNBOOK
title: Kafka Consumer Lag
service: order-service
source: runbooks/kafka-consumer-lag.md
---

# Kafka Consumer Lag

## Symptoms

- Consumer group lag growing steadily rather than fluctuating around zero.
- Log entries reporting batch processing times approaching or exceeding `max.poll.interval.ms`.
- Repeated consumer group rebalances, often with `CommitFailedException`.
- Downstream data appearing late; users report stale state rather than outright errors.
- Messages being reprocessed because offsets could not be committed before a rebalance.

## What is actually happening

Consumer lag is the gap between the latest offset produced and the offset the consumer group has
committed. Lag grows whenever consumption is slower than production. The dangerous failure mode is
the rebalance loop: when processing a batch takes longer than `max.poll.interval.ms`, the broker
considers the consumer dead and revokes its partitions. The consumer then fails to commit the work
it just did, the partitions are reassigned, and the same records are processed again — which makes
the lag worse rather than better.

Common causes:

1. A slow downstream dependency (database, external API) inside the message handler.
2. Too few partitions to allow the consumer group to scale out.
3. A poison message that fails repeatedly and blocks its partition.
4. A genuine spike in produced volume.
5. A batch size too large to process within the poll interval.

## Diagnosis

1. Measure the lag per partition, not just per group. Lag concentrated on one partition points at a
   poison message or a hot key, not at insufficient capacity.
2. Check whether rebalances are occurring. A rebalance loop needs to be broken before any capacity
   change will help.
3. Measure the handler's processing time. Compare it against `max.poll.interval.ms`.
4. Check the health of anything the handler calls. Consumer lag is frequently a symptom of a slow
   dependency rather than a Kafka problem.
5. Compare produced message rate against the previous baseline to rule out a genuine volume spike.

## Resolution

- If a rebalance loop is active, reduce `max.poll.records` so a batch completes comfortably inside
  the poll interval, or increase `max.poll.interval.ms` if the work genuinely takes longer.
- If a single partition is stuck on a poison message, route it to the dead-letter topic so the
  partition can drain.
- If the handler is slow because of a downstream dependency, fix that dependency first. Adding
  consumers will only increase pressure on it.
- If the consumer group is genuinely under-provisioned, scale out — but only up to the partition
  count, since partitions cap useful parallelism.

## Verification

- Lag trends downwards and returns to its baseline.
- Rebalances stop.
- No further `CommitFailedException` entries appear.

## Prevention

- Alert on lag trend rather than an absolute threshold; steady growth matters more than any value.
- Keep handlers fast and push slow work to a separate asynchronous stage.
- Provision partitions with future scaling headroom, since increasing them later redistributes keys.
