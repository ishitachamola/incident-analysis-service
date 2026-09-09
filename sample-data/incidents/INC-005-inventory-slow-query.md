---
documentType: HISTORICAL_INCIDENT
title: "INC-005: Inventory lookup timeouts from a missing index"
service: inventory-service
source: incidents/INC-005-inventory-slow-query.md
incidentId: INC-005
rootCause: Slow query caused by a missing index after table growth
---

# INC-005: Inventory lookup timeouts from a missing index

**Service:** inventory-service
**Severity:** MEDIUM
**Duration:** 3 hours 25 minutes

## Symptoms

Inventory lookup latency degraded gradually over several hours, from a baseline of 42ms to over 7
seconds. Slow query warnings named the same statement repeatedly:
`SELECT * FROM stock_levels WHERE warehouse_id = ? AND sku LIKE ?`. Database CPU rose to 94%.
Eventually `QueryTimeoutException` entries began appearing as queries exceeded the 30 second limit.

No deployment had occurred in the preceding two days.

## Investigation

Latency was concentrated on endpoints sharing the stock lookup query, which pointed at a specific
query rather than general resource saturation. `EXPLAIN ANALYZE` showed a sequential scan over
`stock_levels`, a table that had grown past 40 million rows following a warehouse onboarding the
previous week.

The absence of any recent deployment was itself informative: the query had not changed, the data had.
The planner had previously used an index on `warehouse_id` alone, but as the table grew and the
`sku LIKE` predicate matched an increasing number of rows, it switched to a sequential scan.

The `LIKE` pattern had a leading wildcard, so it could not use a standard B-tree index at all.

## Root cause

A query performance regression caused by data growth rather than a code change. The composite access
pattern had no supporting index, and the leading-wildcard `LIKE` predicate prevented normal index
usage.

## Resolution

A composite index on `(warehouse_id, sku)` was added, which returned the common case to an index
scan. Latency fell to 60ms within minutes of the index becoming available. The `SELECT *` was later
narrowed to the six columns actually used.

## Follow-up actions

- Added slow query logging with a 1 second threshold for earlier warning.
- Added a monthly review of table growth against query plans.
- Replaced the leading-wildcard search with a trigram index for the genuine substring search case.

## Lessons

A query that has been fast for months can become slow with no code change at all, because query
plans depend on data volume. The absence of a recent deployment is evidence in itself, redirecting
the investigation from "what did we change" to "what grew".
