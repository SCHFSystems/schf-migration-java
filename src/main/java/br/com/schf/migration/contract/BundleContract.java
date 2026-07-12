package br.com.schf.migration.contract;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BundleContract {
    public static final String FORMAT_VERSION = "1.0";
    public static final String SCHEMA_VERSION = "1";
    public static final List<String> DATA_FILES = List.of("organizations.ndjson", "users.ndjson",
        "suppliers.ndjson", "categories.ndjson", "financial-accounts.ndjson", "payables.ndjson", "payments.ndjson");
    private BundleContract() {}
    public record Manifest(String bundleFormatVersion, String schemaVersion, String sourceSystem,
                           UUID sourceInstanceId, OffsetDateTime generatedAt, String generatorVersion,
                           String coreMinimumVersion, UUID organizationExternalId,
                           Map<String, Long> recordCounts, Map<String, String> fileChecksums,
                           boolean anonymized, UUID correlationId) {}
    public record Dataset(UUID organizationExternalId, String sourceSystem, UUID sourceInstanceId,
                          boolean anonymized, Map<String, List<Map<String, Object>>> records) {}
}
