---
documentType: RUNBOOK
title: Deployment Rollback
service: user-service
source: runbooks/deployment-rollback.md
---

# Deployment Rollback

## Symptoms

- Error rate rising within minutes of a release.
- A new exception type appearing that was absent from previous versions, commonly
  `NullPointerException` on a newly added field.
- Errors concentrated on endpoints touched by the release.
- Metrics healthy immediately before the deployment and degraded immediately after.

## What is actually happening

A release introduced a regression. The strongest evidence is temporal: the error signature did not
exist before the deployment and appeared immediately after it. A new exception type is more
convincing than an increase in volume of an existing error, because it indicates a genuinely new
code path rather than pre-existing load.

Correlation is not proof. A deployment that coincides with a traffic spike, or with an unrelated
dependency failure, can look identical from the error rate alone. Check whether the specific error
signature is new before concluding the release is responsible.

## Diagnosis

1. Establish the deployment time precisely and compare it against the first occurrence of the error.
2. Determine whether the exception type is new. Search the previous version's logs for the same
   signature; absence there is strong evidence of a regression.
3. Check which endpoints are affected and whether the release touched them.
4. Rule out coincidence: confirm traffic volume was stable and no dependency degraded at the same
   moment.
5. Review the release diff for the specific code path in the stack trace.

## Resolution

- Roll back first. A rollback is almost always faster than a forward fix, and diagnosis is easier
  once users are no longer affected.
- Confirm the rollback resolved the problem. If the error rate stays elevated afterwards, the
  release was not the cause and the investigation must continue.
- Reproduce the failure in a non-production environment using the rolled-back build.
- Fix, add a regression test that would have caught it, and redeploy.

## Rollback procedure

1. Identify the last known good version.
2. Confirm the rollback is safe with respect to database migrations. A release that applied a
   destructive or non-backward-compatible migration cannot be rolled back by redeploying the
   previous build alone.
3. Deploy the previous version.
4. Watch the error rate for at least ten minutes to confirm recovery.
5. Record the incident and keep the failing build for analysis.

## Verification

- Error rate returns to its pre-deployment baseline.
- The new exception type stops appearing.
- Latency returns to normal.

## Prevention

- Write backward-compatible migrations so a rollback is always possible.
- Deploy progressively (canary or staged rollout) so a regression affects a fraction of traffic.
- Watch error rate and latency for a defined window after every release before declaring success.
