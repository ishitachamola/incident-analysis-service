package com.incidentplatform.ingestion;

import com.incidentplatform.ingestion.detection.DetectionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(DetectionProperties.class)
public class LogIngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogIngestionServiceApplication.class, args);
    }
}
