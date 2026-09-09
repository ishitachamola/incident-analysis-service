---
documentType: HISTORICAL_INCIDENT
title: "INC-003: Database connection pool exhaustion in payment-service"
service: payment-service
source: incidents/INC-003-payment-db-pool-exhaustion.md
incidentId: INC-003
rootCause: Database connection pool exhaustion following a deployment
---

# INC-003: Database connection pool exhaustion in payment-service

**Service:** payment-service
**Severity:** CRITICAL
**Duration:** 32 minutes

## Symptoms

HTTP 500 responses from payment endpoints, with the error rate climbing from a baseline of 0.3% to
18%. Logs contained 327 `SQLTransientConnectionException` entries reporting "Connection is not
available, request timed out after 5000ms". HikariCP pool statistics showed `total=20, active=20,
idle=0, waiting=14`. Database connection utilisation rose from 62% to 98%, while database CPU
remained moderate at 45%.

Version v2.4.1 of payment-service had been deployed roughly 8 minutes before the first error.

## Investigation

The pool was clearly saturated: every connection was checked out with a substantial queue waiting.
Database CPU being only moderate confirmed the database itself was not the bottleneck — the
constraint was the application's ability to borrow a connection.

Request volume was flat compared with the previous day, ruling out a traffic-driven explanation.

Review of the v2.4.1 diff found that a refactor of the payment reconciliation path had introduced an
additional query per request, executed in a separate transaction from the main one. Each request
therefore held two connections concurrently instead of one, halving the effective capacity of a pool
that had been sized with little headroom.

## Root cause

Deployment v2.4.1 doubled the number of concurrent database connections held per request, exhausting
a connection pool that had been sized for the previous access pattern.

## Resolution

v2.4.1 was rolled back, and the error rate returned to baseline within four minutes. The
reconciliation query was subsequently rewritten to run inside the existing transaction, so each
request holds a single connection. The fixed version was released two days later without incident.

## Follow-up actions

- Added an alert on connection pool saturation, so the condition is visible before it becomes
  user-facing.
- Added a pool utilisation check to the pre-release load test.
- Increased `maximum-pool-size` from 20 to 30 to restore headroom — after the leak in connection
  usage was fixed, not as a substitute for fixing it.

## Lessons

Connection pool exhaustion is usually a symptom rather than a cause. The pool size was not wrong;
the new code path's connection usage was. Raising the pool size first would have masked the problem
and shifted the bottleneck onto the database.
