---
documentType: RUNBOOK
title: High API Latency
service: inventory-service
source: runbooks/high-api-latency.md
---

# High API Latency

## Symptoms

- Response times rising while the error rate stays near normal, at least initially.
- Slow query warnings naming a specific statement, often with a growing reported duration.
- Database CPU utilisation climbing.
- Query timeouts (`QueryTimeoutException`) appearing once latency exceeds the configured limit.
- Latency concentrated on endpoints that share a common query or table.

## What is actually happening

Latency problems are usually one of four things: a slow database query, a slow downstream call,
resource saturation (CPU, memory, connection pool), or lock contention. The distinguishing evidence
matters, because the remedies are unrelated.

A slow query is the most common cause and has a characteristic signature: the same statement appears
repeatedly in slow query warnings, its duration grows over time as data volume grows, and database
CPU rises alongside it. Query plans degrade suddenly when a table crosses the size at which the
planner abandons an index scan for a sequential scan, so a query that was fast for months can become
slow without any code change.

## Diagnosis

1. Establish whether latency is uniform or concentrated. Concentrated latency points at one query or
   one dependency; uniform latency points at resource saturation.
2. Look for slow query warnings and identify the repeated statement.
3. Run `EXPLAIN ANALYZE` on that statement. A sequential scan over a large table is the usual
   finding.
4. Check whether the table has grown recently. Plans change when volume crosses a threshold.
5. Rule out the alternatives: check downstream dependency latency, connection pool saturation, and
   database lock waits.
6. Correlate with the most recent deployment. A new query, or a changed one, is a strong candidate.

## Resolution

- Add the index the query needs. A missing index on a filtered or joined column is the most common
  root cause and usually the fastest fix.
- Rewrite queries that cannot use an index, for example a leading-wildcard `LIKE` pattern, which
  cannot use a standard B-tree index.
- Avoid `SELECT *` on wide tables when only a few columns are needed.
- Add pagination to endpoints that return unbounded result sets.
- If a recent deployment introduced the query, consider rolling back while a proper fix is prepared.

## Verification

- The slow query warnings stop.
- p95 and p99 latency return to baseline, not merely the average.
- Database CPU returns to its normal range.

## Prevention

- Alert on p95 or p99 latency rather than the mean, which hides tail latency.
- Review query plans for new queries before release, particularly on large tables.
- Log slow queries with a threshold low enough to give early warning.
- Track table growth so plan changes can be anticipated.
