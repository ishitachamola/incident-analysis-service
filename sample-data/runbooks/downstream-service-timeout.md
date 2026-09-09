---
documentType: RUNBOOK
title: Downstream Service Timeout
service: payment-service
source: runbooks/downstream-service-timeout.md
---

# Downstream Service Timeout

## Symptoms

- `SocketTimeoutException` or "Read timed out" entries naming an external or internal dependency.
- Retry attempts appearing in the logs, often escalating through attempt 1, 2 and 3.
- A circuit breaker transitioning from CLOSED to OPEN, followed by `CallNotPermittedException`.
- Elevated API latency on endpoints that call the dependency, while unrelated endpoints stay healthy.
- Errors concentrated on one call path rather than spread across the service.

## What is actually happening

The service depends on something that has become slow or unavailable. Each call occupies a thread
until it times out, so a slow dependency consumes far more capacity than a failing one. Retries
amplify this: three attempts against a slow dependency triples the load on it at exactly the moment
it is least able to cope. A circuit breaker exists to stop that amplification — when it opens, calls
fail immediately instead of waiting for a timeout.

An open circuit breaker is the system protecting itself. It is a symptom of the dependency's
condition, not a fault in the calling service.

## Diagnosis

1. Identify precisely which dependency is timing out. The exception message usually names the host
   or endpoint.
2. Determine whether the dependency is slow or entirely down. Slow is more damaging, because calls
   hold resources rather than failing fast.
3. Check that dependency's own health and status page before assuming a fault on the calling side.
4. Confirm whether the failures are isolated to one call path. Errors spread evenly across
   unrelated endpoints suggest a local problem instead.
5. Check whether retry configuration is amplifying the load, particularly retries without backoff.

## Resolution

- If the dependency is a third party, escalate to them; there is usually no local fix. Confirm the
  circuit breaker is doing its job of failing fast.
- If it is an internal service, investigate it as the primary incident. The timeouts here are a
  downstream symptom.
- Ensure retries use exponential backoff with jitter. Immediate retries against a struggling
  dependency prolong the outage.
- Consider serving degraded functionality — a cached response, a queued request for later
  processing — rather than failing the user's request outright.
- Reduce the client timeout if calls are holding threads for too long. Failing fast preserves
  capacity for requests that can still succeed.

## Verification

- Timeout exceptions stop.
- The circuit breaker returns to CLOSED and stays there.
- Latency on affected endpoints returns to baseline.

## Prevention

- Set explicit, deliberately chosen timeouts on every outbound call. A default of "infinite" turns a
  dependency's slowness into your outage.
- Use circuit breakers on all external dependencies.
- Always pair retries with backoff and a cap on total attempts.
- Alert on dependency latency, not only on dependency errors.
