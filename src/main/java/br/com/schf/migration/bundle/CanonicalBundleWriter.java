package br.com.schf.migration.bundle;

import br.com.schf.migration.contract.BundleContract;
import br.com.schf.migration.contract.BundleContract.Dataset;
import br.com.schf.migration.contract.BundleContract.Manifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayOutputStream;
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

public class CanonicalBundleWriter {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public void write(Dataset dataset, Path output) {
        try {
            var files = new LinkedHashMap<String, byte[]>();
            var checksums = new LinkedHashMap<String, String>();
            var counts = new LinkedHashMap<String, Long>();
            for (String name : BundleContract.DATA_FILES) {
                var lines = new StringBuilder();
                for (var record : dataset.records().getOrDefault(name, java.util.List.of()))
                    lines.append(mapper.writeValueAsString(record)).append('\n');
                var path = "bundle/data/" + name;
                var bytes = lines.toString().getBytes(StandardCharsets.UTF_8);
                files.put(path, bytes); checksums.put(path, sha256(bytes));
                counts.put(countKey(name), (long) dataset.records().getOrDefault(name, java.util.List.of()).size());
            }
            var manifest = new Manifest("1.0", "1", dataset.sourceSystem(), dataset.sourceInstanceId(),
                OffsetDateTime.now(ZoneOffset.UTC), "schf-migration-java/0.1.0", "0.1.0",
                dataset.organizationExternalId(), counts, checksums, dataset.anonymized(), UUID.randomUUID());
            files.put("bundle/manifest.json", mapper.writeValueAsBytes(manifest));
            var checksumText = new StringBuilder(); checksums.forEach((path, hash) -> checksumText.append(hash).append("  ").append(path).append('\n'));
            files.put("bundle/checksums.sha256", checksumText.toString().getBytes(StandardCharsets.UTF_8));
            files.put("bundle/reports/migration-summary.json", mapper.writeValueAsBytes(Map.of("recordCounts", counts, "anonymized", dataset.anonymized())));
            Files.createDirectories(output.toAbsolutePath().normalize().getParent());
            try (var zip = new ZipOutputStream(Files.newOutputStream(output))) {
                for (var entry : files.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
            }
        } catch (Exception ex) { throw new IllegalStateException("Canonical bundle generation failed", ex); }
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private String countKey(String name) { return name.replace(".ndjson", "").replace("financial-accounts", "financialAccounts"); }
}
