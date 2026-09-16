package com.incidentplatform.ai;

import com.incidentplatform.ai.config.AnalysisProperties;
import com.incidentplatform.ai.config.LlmProperties;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties({LlmProperties.class, AnalysisProperties.class})
public class AiAnalysisServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAnalysisServiceApplication.class, args);
    }

    /** Injected rather than read statically, so quota windows and day boundaries are testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
