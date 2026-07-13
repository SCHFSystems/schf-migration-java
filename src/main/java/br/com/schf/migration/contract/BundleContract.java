package br.com.schf.migration.contract;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BundleContract {
    public static final String FORMAT_VERSION = "1.0";
    public static final String FORMAT_VERSION_1_1 = "1.1";
    public static final String SCHEMA_VERSION = "1";
    public static final List<String> DATA_FILES = List.of("organizations.ndjson", "users.ndjson",
        "suppliers.ndjson", "categories.ndjson", "financial-accounts.ndjson", "payables.ndjson", "payments.ndjson");
    public static final List<String> DATA_FILES_1_1 = List.of("organizations.ndjson", "users.ndjson",
        "suppliers.ndjson", "categories.ndjson", "financial-accounts.ndjson",
        "counterparties.ndjson", "payables.ndjson", "payments.ndjson");

    private BundleContract() {}

    public record Manifest(String bundleFormatVersion, String schemaVersion, String sourceSystem,
                           UUID sourceInstanceId, OffsetDateTime generatedAt, String generatorVersion,
                           String coreMinimumVersion, UUID organizationExternalId,
                           Map<String, Long> recordCounts, Map<String, String> fileChecksums,
                           boolean anonymized, UUID correlationId) {}

    public record Dataset(UUID organizationExternalId, String sourceSystem, UUID sourceInstanceId,
                          boolean anonymized, Map<String, List<Map<String, Object>>> records) {}

    public static boolean isFormat11(String version) {
        return FORMAT_VERSION_1_1.equals(version);
    }

    public static List<String> dataFilesForVersion(String version) {
        return isFormat11(version) ? DATA_FILES_1_1 : DATA_FILES;
    }
}
