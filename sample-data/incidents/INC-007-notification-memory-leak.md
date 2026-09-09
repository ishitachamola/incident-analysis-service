---
documentType: HISTORICAL_INCIDENT
title: "INC-007: Notification service instability from a memory leak"
service: notification-service
source: incidents/INC-007-notification-memory-leak.md
incidentId: INC-007
rootCause: Memory leak from an unbounded in-memory cache
---

# INC-007: Notification service instability from a memory leak

**Service:** notification-service
**Severity:** MEDIUM
**Duration:** 4 days (intermittent)

## Symptoms

Notification delivery failed intermittently, roughly every 14 hours. Each episode was preceded by
rising garbage collection pause times and heap utilisation approaching the configured maximum,
ending in an `OutOfMemoryError` and a container restart. Restarts temporarily restored service, so
the problem appeared to resolve itself each time.

## Investigation

The regular interval was the most informative signal: a leak fills the heap at a rate proportional to
traffic, producing a repeating sawtooth pattern rather than a random one.

A heap dump captured shortly before an `OutOfMemoryError` showed a `ConcurrentHashMap` holding
approximately 2.1 million entries, used to deduplicate notifications by recipient. Entries were added
but never removed, so the map grew for the lifetime of the process.

The deduplication cache had been introduced eleven days earlier. It went unnoticed initially because
the heap took most of a day to fill.

## Root cause

An unbounded in-memory cache used for notification deduplication retained every entry for the
lifetime of the process, exhausting the heap.

## Resolution

The map was replaced with a size- and time-bounded cache holding entries for 24 hours, which is the
window the deduplication logic actually requires. Heap utilisation stabilised and the restarts
stopped.

## Follow-up actions

- Added alerting on heap utilisation trend and garbage collection pause time.
- Established a review rule that any in-memory cache must have an explicit eviction policy.
- Enabled heap dump on `OutOfMemoryError` so future occurrences are diagnosable immediately.

## Lessons

Automatic restarts masked the problem for days by making each episode look self-resolving. The
regular interval between failures was the clue that separated a leak from random instability. Any
cache without an eviction policy is a memory leak with a delay.
