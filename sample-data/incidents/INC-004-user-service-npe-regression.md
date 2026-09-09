---
documentType: HISTORICAL_INCIDENT
title: "INC-004: NullPointerException regression in user-service v3.0.4"
service: user-service
source: incidents/INC-004-user-service-npe-regression.md
incidentId: INC-004
rootCause: Deployment regression introducing an unguarded null field
---

# INC-004: NullPointerException regression in user-service v3.0.4

**Service:** user-service
**Severity:** HIGH
**Duration:** 18 minutes

## Symptoms

The error rate for user-service rose from under 1% to 22% within three minutes of deploying v3.0.4.
Logs showed a `NullPointerException` with the message
`Cannot invoke "String.trim()" because "preferredName" is null`. This exception signature did not
appear anywhere in the previous version's logs. Errors were confined to the profile endpoints.

## Investigation

The temporal correlation was unusually clean: the first occurrence was 90 seconds after the
deployment completed, and the exception type was entirely new rather than an increase in an existing
error.

Traffic volume was checked and found flat, and no dependency had degraded in the same window, which
ruled out the two common coincidences that can make an unrelated failure look like a regression.

v3.0.4 added a `preferredName` field to the user profile response. The implementation called
`trim()` on the value without a null check. Accounts created before the field existed have it null,
so every request for an older profile failed.

## Root cause

A regression in v3.0.4: a newly added field was dereferenced without a null check, and pre-existing
records legitimately hold null for that field.

## Resolution

v3.0.4 was rolled back and the error rate returned to baseline within two minutes, confirming the
release was responsible. A null check was added along with a regression test covering profiles
created before the field was introduced. v3.0.5 was deployed the next morning without incident.

## Follow-up actions

- Added a test fixture representing legacy records that predate recently added fields.
- Enabled a staged rollout so a regression reaches a fraction of traffic first.
- Added an automatic post-deployment error rate check over a ten minute window.

## Lessons

A brand new exception signature is much stronger evidence of a regression than a rise in the volume
of an existing error. Rolling back and confirming recovery both restored service and proved the
hypothesis. Adding a field is not a backward-compatible change when existing rows will hold null.
