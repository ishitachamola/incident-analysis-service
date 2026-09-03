package com.incidentplatform.ingestion.detection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DetectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DetectionScheduler.class);

    private final ErrorRateDetector detector;

    public DetectionScheduler(ErrorRateDetector detector) {
        this.detector = detector;
    }

    @Scheduled(fixedDelayString = "${incident-detection.interval:15s}")
    public void evaluate() {
        try {
            detector.evaluate();
        } catch (Exception ex) {
            // A failed evaluation must never kill the scheduler: the next cycle should try again.
            log.error("Incident detection cycle failed", ex);
        }
    }
}
