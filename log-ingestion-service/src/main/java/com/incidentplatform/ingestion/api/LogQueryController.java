package com.incidentplatform.ingestion.api;

import com.incidentplatform.ingestion.repository.LogEntryRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API over ingested logs. This service owns the log data, so other services query it here
 * rather than reaching into its table directly.
 */
@RestController
@RequestMapping("/api/logs")
public class LogQueryController {

    private static final int MAX_LIMIT = 1000;

    private final LogEntryRepository repository;

    public LogQueryController(LogEntryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<LogEntryResponse> findLogs(
            @RequestParam String service,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "500") int limit) {

        int effectiveLimit = Math.clamp(limit, 1, MAX_LIMIT);
        return repository
                .findByServiceNameAndOccurredAtBetweenOrderByOccurredAtAsc(
                        service, from, to, PageRequest.of(0, effectiveLimit))
                .stream()
                .map(LogEntryResponse::from)
                .toList();
    }
}
