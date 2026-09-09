---
documentType: HISTORICAL_INCIDENT
title: "INC-006: Payment errors during an unplanned database failover"
service: payment-service
source: incidents/INC-006-payment-db-failover.md
incidentId: INC-006
rootCause: Unplanned database failover, not connection pool exhaustion
---

# INC-006: Payment errors during an unplanned database failover

**Service:** payment-service
**Severity:** CRITICAL
**Duration:** 11 minutes

## Symptoms

HTTP 500 responses from payment endpoints with the error rate spiking to 31%. Logs contained
`SQLTransientConnectionException` entries and connection timeout messages. Connection pool
utilisation reached 100%.

On the surface this closely resembled INC-003, and the on-call engineer's first hypothesis was
another connection pool exhaustion caused by a deployment.

## Investigation

The superficial similarity did not survive scrutiny. Three pieces of evidence separated this
incident from INC-003:

1. **No deployment had occurred.** The most recent release was nine days earlier, so a new code path
   could not explain a sudden change in connection usage.
2. **The onset was instantaneous rather than gradual.** In INC-003 utilisation climbed over several
   minutes as load built against a halved pool. Here it went from normal to fully saturated within a
   single sampling interval, which is characteristic of connections being severed rather than
   consumed.
3. **The database reported a primary instance change.** Managed database logs showed an automatic
   failover triggered by a hardware fault on the primary.

Every existing pooled connection pointed at the old primary and became invalid at once. The pool
appeared saturated because every connection in it was dead, not because they were all in use.

## Root cause

An unplanned database failover invalidated every pooled connection simultaneously. The application
did not detect the broken connections quickly, so requests continued to borrow dead connections and
time out until the pool recycled them.

## Resolution

The service recovered on its own once the pool evicted the invalid connections and reconnected to the
new primary, about 11 minutes after the failover. A rolling restart of the affected pods, performed
during the incident, shortened recovery for the restarted instances.

## Follow-up actions

- Added a connection validation query and reduced `max-lifetime` so invalid connections are evicted
  promptly.
- Configured the JDBC driver with the failover-aware connection string.
- Added an alert on database primary changes, which would have identified the cause in seconds.

## Lessons

Connection pool saturation is a symptom with more than one cause. Exhaustion (all connections
legitimately busy) and invalidation (all connections dead) look nearly identical from the
application's error logs, but the remedies are entirely different — and raising the pool size, the
instinctive response, would have done nothing here. The distinguishing evidence was the absence of a
deployment and the instantaneous onset.
