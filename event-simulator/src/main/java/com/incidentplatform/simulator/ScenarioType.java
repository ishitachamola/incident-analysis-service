package com.incidentplatform.simulator;

/**
 * The failure scenarios the platform is expected to investigate. Each one produces a distinct
 * evidence signature so retrieval and root-cause analysis can be evaluated against a known answer.
 */
public enum ScenarioType {
    DB_POOL_EXHAUSTION,
    KAFKA_CONSUMER_LAG,
    DOWNSTREAM_TIMEOUT,
    DEPLOYMENT_REGRESSION,
    SLOW_QUERY
}
