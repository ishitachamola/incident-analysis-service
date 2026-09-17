package com.incidentplatform.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatCitationValidatorTest {

    private static final String LOG_REF = "LOG#168fef4a-97ed-4aaa-89c3-33a37c13114b";
    private static final String RUNBOOK_REF = "RUNBOOK#runbooks/database-connection-pool-exhaustion.md";
    private static final Set<String> CITABLE = Set.of(LOG_REF, RUNBOOK_REF);

    private final ChatCitationValidator validator = new ChatCitationValidator();

    @Test
    void keepsCitationsThatExistInTheEvidence() {
        var result = validator.validate(
                "The pool was saturated [" + LOG_REF + "], and the runbook says to roll back [" + RUNBOOK_REF + "].",
                CITABLE);

        assertThat(result.sources()).containsExactly(LOG_REF, RUNBOOK_REF);
        assertThat(result.removedCitations()).isEmpty();
        assertThat(result.reply()).contains("[" + LOG_REF + "]");
    }

    @Test
    void replacesCitationsOfEvidenceThatWasNeverShown() {
        var result = validator.validate(
                "Errors began earlier [LOG#invented-by-the-model] and the pool filled [" + LOG_REF + "].", CITABLE);

        assertThat(result.removedCitations()).containsExactly("LOG#invented-by-the-model");
        assertThat(result.sources()).containsExactly(LOG_REF);
        assertThat(result.reply())
                .contains(ChatCitationValidator.REMOVED_MARKER)
                .doesNotContain("LOG#invented-by-the-model");
    }

    @Test
    void deduplicatesRepeatedCitations() {
        var result = validator.validate("Twice [" + LOG_REF + "] and again [" + LOG_REF + "].", CITABLE);

        assertThat(result.sources()).containsExactly(LOG_REF);
    }

    @Test
    void leavesAReplyWithoutCitationsUnchanged() {
        var result = validator.validate("The evidence does not show which query was slow.", CITABLE);

        assertThat(result.reply()).isEqualTo("The evidence does not show which query was slow.");
        assertThat(result.sources()).isEmpty();
        assertThat(result.removedCitations()).isEmpty();
    }

    @Test
    void handlesAnEmptyReply() {
        var result = validator.validate(null, CITABLE);

        assertThat(result.reply()).isEmpty();
        assertThat(result.sources()).isEmpty();
    }
}
