---
documentType: RUNBOOK
title: Database Connection Pool Exhaustion
service: payment-service
source: runbooks/database-connection-pool-exhaustion.md
---

# Database Connection Pool Exhaustion

## Symptoms

- HTTP 500 responses from endpoints that read or write to the database.
- Log entries containing `SQLTransientConnectionException` or "Connection is not available, request timed out".
- HikariCP pool statistics showing `active` equal to `total` and a growing `waiting` count.
- Database connection utilisation climbing towards 100% while database CPU stays moderate.
- Request latency rising sharply while the database itself reports healthy query times.

## What is actually happening

The application holds a fixed-size pool of database connections. When every connection is checked
out and not yet returned, further requests queue for a free connection and fail once they exceed the
connection timeout. The database is usually not the bottleneck; the application's ability to borrow a
connection is.

Common causes, in rough order of likelihood:

1. A new release increased the number of concurrent database operations per request.
2. A slow query holds connections far longer than expected, reducing effective pool throughput.
3. Connections are leaked because a code path fails to close them (missing try-with-resources).
4. Traffic genuinely increased beyond what the pool was sized for.
5. The pool is simply undersized relative to the service's concurrency.

## Diagnosis

1. Confirm the pool is saturated. Look for HikariCP pool statistics in the logs and check whether
   `active` equals `total` with a non-zero `waiting` count.
2. Correlate the start of the errors with the most recent deployment. If errors began within
   minutes of a release, treat that release as the leading hypothesis.
3. Check for slow queries in the same window. A query that suddenly takes seconds instead of
   milliseconds will exhaust a pool without any change in traffic.
4. Compare request volume against the previous day. Rule out a genuine traffic increase before
   assuming a regression.
5. Look for connection leaks by enabling HikariCP leak detection
   (`spring.datasource.hikari.leak-detection-threshold`) in a non-production environment.

## Resolution

- If a recent deployment is implicated, roll back first and investigate afterwards. Restoring
  service takes priority over root-cause analysis.
- If a slow query is responsible, optimise the query or add the missing index. Connection pool
  pressure is often a symptom of a query performance problem rather than a pool sizing problem.
- If a connection leak is confirmed, fix the code path that fails to release its connection.
- Only increase `maximum-pool-size` once the above have been excluded. Raising the pool size to
  mask a leak or a slow query moves the bottleneck to the database and makes the incident worse.

## Verification

- Pool `active` count falls below `total`, and `waiting` returns to zero.
- `SQLTransientConnectionException` entries stop appearing.
- Error rate returns to its pre-incident baseline.

## Prevention

- Alert on connection pool saturation rather than only on error rate, so the problem is visible
  before it becomes user-facing.
- Load test releases that change database access patterns.
- Set a connection timeout low enough that a saturated pool fails fast instead of stalling threads.
