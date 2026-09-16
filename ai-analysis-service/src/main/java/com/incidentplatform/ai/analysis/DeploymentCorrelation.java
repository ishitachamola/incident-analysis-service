package com.incidentplatform.ai.analysis;

import java.time.Instant;

/**
 * Deployment timing relative to the incident, calculated in code rather than asked of the model.
 * Arithmetic on timestamps is exactly the kind of fact a model can get subtly wrong, and handing it
 * over pre-computed means the model reasons about the timing instead of re-deriving it.
 *
 * <p>This records correlation only. Whether the deployment caused the incident is for the analysis
 * to argue from the rest of the evidence.
 *
 * @param minutesFromDeploymentToFirstError negative when errors began before the deployment, which
 *                                          is evidence against the deployment being the cause
 */
public record DeploymentCorrelation(
        boolean deploymentInWindow,
        String deploymentRef,
        String deploymentSummary,
        Instant deployedAt,
        Long minutesBeforeDetection,
        Instant firstErrorAt,
        Long minutesFromDeploymentToFirstError,
        String note
) {
}
