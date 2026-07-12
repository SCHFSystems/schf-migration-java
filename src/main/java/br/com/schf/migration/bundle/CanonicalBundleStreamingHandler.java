package br.com.schf.migration.bundle;

import br.com.schf.migration.contract.BundleContract;
import br.com.schf.migration.source.ExtractionReport;
import br.com.schf.migration.source.RecordHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CanonicalBundleStreamingHandler implements RecordHandler {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Path output;
    private final ExtractionReport report;
    private final int batchSize;
    private final Map<String, StringBuilder> buffers = new LinkedHashMap<>();
    private final Map<String, Long> counts = new LinkedHashMap<>();
    private final Map<String, String> checksums = new LinkedHashMap<>();
    private final Map<String, MessageDigest> digests = new LinkedHashMap<>();
    private long totalRecords;
    private UUID orgExternalId;
    private String sourceSystem;
    private UUID sourceInstanceId;
    private boolean anonymized;

    public CanonicalBundleStreamingHandler(Path output, ExtractionReport report, int batchSize) {
        this.output = output;
        this.report = report;
        this.batchSize = batchSize;
        for (String name : BundleContract.DATA_FILES) {
            buffers.put(name, new StringBuilder());
            counts.put(name, 0L);
            try { digests.put(name, MessageDigest.getInstance("SHA-256")); } catch (Exception ex) {}
        }
    }

    @Override
    public void accept(String entityType, Map<String, Object> record) throws Exception {
        var fileName = entityType + ".ndjson";
        var buf = buffers.get(fileName);
        if (buf == null) throw new IllegalArgumentException("Unknown entity type: " + entityType);
        var json = mapper.writeValueAsString(record);
        buf.append(json).append('\n');
        totalRecords++;
        counts.merge(fileName, 1L, Long::sum);
        var digest = digests.get(fileName);
        if (digest != null) digest.update(json.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
        if ("organizations".equals(entityType)) {
            captureOrgMetadata(record);
        }
        if (totalRecords % batchSize == 0) {
            System.err.println("  Flushed " + totalRecords + " records");
        }
    }

    private void captureOrgMetadata(Map<String, Object> record) {
        if (orgExternalId == null && record.get("externalId") != null) {
            try { orgExternalId = UUID.fromString(record.get("externalId").toString()); } catch (Exception ignored) {}
        }
        if (sourceSystem == null) sourceSystem = (String) record.get("code");
        if (sourceInstanceId == null && record.get("externalId") != null) {
            try { sourceInstanceId = UUID.fromString(record.get("externalId").toString()); } catch (Exception ignored) {}
        }
    }

    public void finish() throws Exception {
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(output))) {
            for (String name : BundleContract.DATA_FILES) {
                var buf = buffers.get(name);
                if (buf == null) continue;
                var bytes = buf.toString().getBytes(StandardCharsets.UTF_8);
                var path = "bundle/data/" + name;
                var entry = new ZipEntry(path);
                zip.putNextEntry(entry);
                zip.write(bytes);
                zip.closeEntry();
                checksums.put(path, CanonicalBundleWriter.sha256(bytes));
                report.record(name, counts.get(name), counts.get(name), 0, 0);
            }
            for (String name : BundleContract.DATA_FILES) {
                var path = "bundle/data/" + name;
                if (!checksums.containsKey(path)) {
                    checksums.put(path, CanonicalBundleWriter.sha256(new byte[0]));
                }
            }
            var manifest = new BundleContract.Manifest(
                BundleContract.FORMAT_VERSION, BundleContract.SCHEMA_VERSION,
                sourceSystem != null ? sourceSystem : "unknown",
                sourceInstanceId != null ? sourceInstanceId : UUID.randomUUID(),
                OffsetDateTime.now(ZoneOffset.UTC), "schf-migration-java/0.1.0", "0.1.0",
                orgExternalId != null ? orgExternalId : UUID.randomUUID(),
                Map.copyOf(counts), Map.copyOf(checksums), anonymized, UUID.randomUUID());
            var manifestBytes = mapper.writeValueAsBytes(manifest);
            zip.putNextEntry(new ZipEntry("bundle/manifest.json"));
            zip.write(manifestBytes);
            zip.closeEntry();
            var checksumText = new StringBuilder();
            checksums.forEach((path, hash) -> checksumText.append(hash).append("  ").append(path).append('\n'));
            zip.putNextEntry(new ZipEntry("bundle/checksums.sha256"));
            zip.write(checksumText.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            var summary = Map.of("recordCounts", counts, "anonymized", anonymized, "sourceSystem", sourceSystem);
            zip.putNextEntry(new ZipEntry("bundle/reports/migration-summary.json"));
            zip.write(mapper.writeValueAsBytes(summary));
            zip.closeEntry();
        }
        report.bundleSize(Files.size(output));
    }

    public Map<String, Object> getManifest() {
        var m = new LinkedHashMap<String, Object>();
        m.put("recordCounts", Map.copyOf(counts));
        m.put("totalRecords", totalRecords);
        m.put("bundleSizeBytes", report.bundleSizeBytes());
        m.put("generatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        return m;
    }
}
