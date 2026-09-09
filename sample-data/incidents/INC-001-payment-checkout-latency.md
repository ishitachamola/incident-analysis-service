---
documentType: HISTORICAL_INCIDENT
title: "INC-001: Checkout latency caused by a slow payment gateway"
service: payment-service
source: incidents/INC-001-payment-checkout-latency.md
incidentId: INC-001
rootCause: Downstream payment gateway degradation
---

# INC-001: Checkout latency caused by a slow payment gateway

**Service:** payment-service
**Severity:** HIGH
**Duration:** 47 minutes

## Symptoms

Checkout endpoint latency rose from around 300ms to over 8 seconds. Logs filled with
`SocketTimeoutException` naming the external payment gateway, followed by retry attempts and
eventually a circuit breaker opening. The error rate reached 12%. No deployment had occurred in the
preceding six hours.

## Investigation

Latency was concentrated entirely on the checkout path; unrelated endpoints stayed healthy, which
ruled out resource saturation within the service. The database was healthy, with normal connection
pool utilisation and no slow queries. The exception messages named the gateway host directly.

The gateway's own status page confirmed a degradation on their side. The circuit breaker opening was
the platform protecting itself, not a fault of its own.

## Root cause

The external payment gateway experienced a partial outage, responding slowly rather than failing
outright. Because calls were slow rather than failed, each one occupied a request thread until it
timed out, and the retry policy tripled the load on an already-struggling dependency.

## Resolution

The circuit breaker was allowed to remain open, which restored latency for non-payment traffic
immediately. Retry attempts were reduced from three to one while the gateway recovered. Full service
returned once the gateway recovered 40 minutes later.

## Follow-up actions

- Added exponential backoff with jitter to the gateway retry policy.
- Reduced the outbound call timeout so a slow gateway fails fast instead of holding threads.
- Added an alert on gateway latency, not only on gateway errors.

## Lessons

A slow dependency is more damaging than a dead one, because slow calls hold resources while failed
calls release them. Retries without backoff amplify a dependency's problems at exactly the wrong
moment.
