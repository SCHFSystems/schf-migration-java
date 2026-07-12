package br.com.schf.migration.source.firebird;

import br.com.schf.migration.source.ExtractionReport;
import java.util.LinkedHashMap;
import java.util.Map;

public class FirebirdExtractionReport {
    private final ExtractionReport report;
    private final Map<String, Object> errors = new LinkedHashMap<>();

    public FirebirdExtractionReport(ExtractionReport report) {
        this.report = report;
    }

    public void recordError(String phase, String entityType, String externalIdHash, String errorCode, String details) {
        errors.put(phase + ":" + entityType + ":" + externalIdHash, Map.of(
            "errorCode", errorCode, "details", details));
    }

    public Map<String, Object> toSanitizedMap() {
        var result = new LinkedHashMap<>(report.toSanitizedMap());
        result.put("sanitizedErrors", errors.size());
        result.put("generatedAt", java.time.OffsetDateTime.now().toString());
        return result;
    }
}
