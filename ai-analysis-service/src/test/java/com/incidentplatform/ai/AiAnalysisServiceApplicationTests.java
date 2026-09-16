package com.incidentplatform.ai;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

@Import(AbstractKnowledgeIntegrationTest.OfflineModelConfiguration.class)
class AiAnalysisServiceApplicationTests extends AbstractKnowledgeIntegrationTest {

    @Test
    void contextLoads() {
    }
}
