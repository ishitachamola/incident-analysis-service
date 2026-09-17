package com.incidentplatform.ai.chat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Applies the same grounding rule to chat replies as to analyses: a citation only counts if it
 * refers to evidence the model was actually shown.
 *
 * <p>A reply is free text, so an invalid reference cannot simply be dropped from a field. Instead it
 * is replaced in place with a visible marker. The reader then sees that the assistant referred to
 * something unsupported, rather than a convincing-looking reference to evidence that does not exist.
 */
@Component
public class ChatCitationValidator {

    static final String REMOVED_MARKER = "[unverified reference removed]";
    private static final Pattern CITATION = Pattern.compile(
            "\\[((?:INCIDENT|LOG|DEPLOY|RULE|EVENT|RUNBOOK|HISTORICAL_INCIDENT)#[^\\]\\s]+)]");

    public Result validate(String reply, Set<String> citableRefs) {
        Matcher matcher = CITATION.matcher(reply == null ? "" : reply);
        Set<String> valid = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();
        StringBuilder cleaned = new StringBuilder();

        while (matcher.find()) {
            String ref = matcher.group(1);
            if (citableRefs.contains(ref)) {
                valid.add(ref);
                matcher.appendReplacement(cleaned, Matcher.quoteReplacement("[" + ref + "]"));
            } else {
                invalid.add(ref);
                matcher.appendReplacement(cleaned, Matcher.quoteReplacement(REMOVED_MARKER));
            }
        }
        matcher.appendTail(cleaned);

        return new Result(cleaned.toString(), List.copyOf(valid), List.copyOf(invalid));
    }

    public record Result(String reply, List<String> sources, List<String> removedCitations) {
    }
}
