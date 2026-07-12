package br.com.schf.migration.cli;

import br.com.schf.migration.bundle.BundleComparer;
import br.com.schf.migration.bundle.CanonicalBundleStreamingHandler;
import br.com.schf.migration.bundle.CanonicalBundleValidator;
import br.com.schf.migration.bundle.CanonicalBundleWriter;
import br.com.schf.migration.source.ExtractionReport;
import br.com.schf.migration.source.ProgressTracker;
import br.com.schf.migration.source.RecordHandler;
import br.com.schf.migration.source.SourceAdapter;
import br.com.schf.migration.source.SyntheticScaleAdapter;
import br.com.schf.migration.source.SyntheticSourceAdapter;
import br.com.schf.migration.source.firebird.FirebirdCheckpointStore;
import br.com.schf.migration.source.firebird.FirebirdSourceAdapter;
import br.com.schf.migration.source.firebird.FirebirdSourceConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class MigrationCli {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private MigrationCli() {}

    public static void main(String[] args) {
        if (args.length == 0) { usage(); return; }
        try {
            switch (args[0]) {
                case "analyze" -> handleAnalyze(args);
                case "analyze-source" -> handleAnalyzeSource(args);
                case "inspect-schema" -> handleInspectSchema(args);
                case "validate-connection" -> handleValidateConnection(args);
                case "generate-bundle" -> handleGenerateBundle(args);
                case "resume-generation" -> handleResumeGeneration(args);
                case "validate-bundle" -> handleValidateBundle(args);
                case "compare-bundles" -> handleCompareBundles(args);
                case "print-summary" -> handlePrintSummary(args);
                default -> usage();
            }
        } catch (RuntimeException ex) {
            var message = ex.getMessage() != null ? ex.getMessage().substring(0, Math.min(ex.getMessage().length(), 200)) : "Unknown error";
            System.err.println("MIGRATION_COMMAND_FAILED: " + message);
            System.exit(exitCode(ex));
        }
    }

    private static int exitCode(Exception ex) {
        var msg = ex.getMessage();
        if (msg == null) return 9;
        if (msg.contains("configuration") || msg.contains("config") || msg.contains("credential")) return 2;
        if (msg.contains("connection") || msg.contains("connect")) return 3;
        if (msg.contains("schema") || msg.contains("table")) return 4;
        if (msg.contains("validation") || msg.contains("invalid") || msg.contains("checksum")) return 5;
        if (msg.contains("extract") || msg.contains("read")) return 6;
        if (msg.contains("bundle")) return 7;
        if (msg.contains("cancel")) return 8;
        return 9;
    }

    private static String value(String[] args, String option) {
        for (int i = 0; i < args.length - 1; i++) if (option.equals(args[i])) return args[i + 1];
        throw new IllegalArgumentException("Missing option: " + option);
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (flag.equals(a)) return true;
        return false;
    }

    private static SourceAdapter adapter(String id) {
        return switch (id) {
            case "synthetic" -> new SyntheticSourceAdapter();
            case "synthetic-scale" -> new SyntheticScaleAdapter(100, 50, 10, 500, 20);
            default -> throw new IllegalArgumentException("Adapter unavailable: " + id);
        };
    }

    private static void handleAnalyze(String[] args) {
        var adapter = adapter(value(args, "--source"));
        System.out.println(adapter.analyze());
    }

    private static void handleAnalyzeSource(String[] args) {
        var config = FirebirdSourceConfiguration.fromEnvironment();
        var adapter = new FirebirdSourceAdapter(config);
        System.out.println(adapter.analyze());
    }

    private static void handleInspectSchema(String[] args) {
        var config = FirebirdSourceConfiguration.fromEnvironment();
        var adapter = new FirebirdSourceAdapter(config);
        try (var conn = adapter.connectionFactory().openReadOnly()) {
            var result = adapter.schemaInspector().inspect(conn);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(System.out, result);
        } catch (Exception ex) {
            throw new RuntimeException("Schema inspection failed: " + ex.getMessage(), ex);
        }
    }

    private static void handleValidateConnection(String[] args) {
        var config = FirebirdSourceConfiguration.fromEnvironment();
        var adapter = new FirebirdSourceAdapter(config);
        adapter.connectionFactory().validateConnection();
        System.out.println("Connection OK to " + config.connectionUrl());
    }

    private static void handleGenerateBundle(String[] args) {
        var sourceId = value(args, "--source");
        var output = Path.of(value(args, "--output"));
        if ("firebird".equals(sourceId)) {
            var config = FirebirdSourceConfiguration.fromEnvironment();
            var adapter = new FirebirdSourceAdapter(config);
            generateBundleStreaming(adapter, output, args);
        } else {
            var adapter = adapter(sourceId);
            new CanonicalBundleWriter().write(adapter.readCanonical(), output);
            System.out.println("Bundle generated: " + output);
        }
    }

    private static void handleResumeGeneration(String[] args) {
        var output = Path.of(value(args, "--output"));
        var config = FirebirdSourceConfiguration.fromEnvironment();
        var adapter = new FirebirdSourceAdapter(config);
        generateBundleStreaming(adapter, output, args);
    }

    private static void generateBundleStreaming(FirebirdSourceAdapter adapter, Path output, String[] args) {
        var config = adapter.config();
        var checkpoints = new FirebirdCheckpointStore(config.workDirectory());
        if (hasFlag(args, "--resume") && !checkpoints.exists()) {
            throw new IllegalArgumentException("No checkpoints found to resume from. Start with a fresh extraction.");
        }
        if (!hasFlag(args, "--resume")) {
            checkpoints.clear();
        }
        var report = new ExtractionReport();
        var cancelled = new AtomicBoolean(false);
        var startTime = Instant.now();

        var progress = new ProgressTracker() {
            final Map<String, AtomicLong> counts = new LinkedHashMap<>();
            @Override public void phaseStarted(String phase, long total) { System.err.println("Phase: " + phase + " [" + total + "]"); }
            @Override public void recordsProcessed(String phase, long count) {
                counts.computeIfAbsent(phase, k -> new AtomicLong()).set(count);
                if (count % 1000 == 0) System.err.println("  " + phase + ": " + count + " records");
            }
            @Override public void phaseCompleted(String phase) { System.err.println("Phase completed: " + phase); }
            @Override public void reportError(String phase, String entityType, String id, String code, String d) { System.err.println("  Error [" + code + "]: " + entityType + " / " + id); }
            @Override public boolean isCancelled() { return cancelled.get(); }
            @Override public Duration elapsed() { return Duration.between(startTime, Instant.now()); }
            @Override public long processedCount(String phase) { return counts.getOrDefault(phase, new AtomicLong()).longValue(); }
        };

        var recordHandler = new CanonicalBundleStreamingHandler(output, report, config.batchSize());
        try {
            adapter.extractTo(recordHandler, checkpoints, progress);
            recordHandler.finish();
            var manifest = recordHandler.getManifest();
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(System.out, manifest);
            System.out.println("Bundle generated: " + output);
        } catch (Exception ex) {
            throw new RuntimeException("extraction failed: " + ex.getMessage(), ex);
        }
    }

    private static void handleValidateBundle(String[] args) {
        new CanonicalBundleValidator().validate(Path.of(value(args, "--bundle")));
        System.out.println("Bundle validation PASSED");
    }

    private static void handleCompareBundles(String[] args) {
        var left = Path.of(value(args, "--left"));
        var right = Path.of(value(args, "--right"));
        var result = new BundleComparer().compare(left, right);
        if (result.isIdentical()) {
            System.out.println("Bundles are semantically identical.");
        } else {
            System.out.println("Differences found:");
            System.out.println("  Added:   " + result.addedCount());
            System.out.println("  Removed: " + result.removedCount());
            System.out.println("  Changed: " + result.changedCount());
        }
        System.out.println("Left counts:  " + result.leftCounts());
        System.out.println("Right counts: " + result.rightCounts());
    }

    private static void handlePrintSummary(String[] args) {
        var bundle = Path.of(value(args, "--bundle"));
        try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(bundle))) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                if ("bundle/manifest.json".equals(entry.getName())) {
                    var manifest = MAPPER.readTree(zip.readAllBytes());
                    MAPPER.writerWithDefaultPrettyPrinter().writeValue(System.out, manifest);
                    return;
                }
                entry = zip.getNextEntry();
            }
            System.out.println("No manifest found in bundle.");
        } catch (Exception ex) {
            throw new RuntimeException("print-summary failed: " + ex.getMessage(), ex);
        }
    }

    private static void usage() {
        System.out.println("""
            SCHF Migration Java CLI
            Commands:
              analyze --source <id>
              analyze-source
              inspect-schema
              validate-connection
              generate-bundle --source <id> --output <path> [--resume]
              resume-generation --output <path>
              validate-bundle --bundle <path>
              compare-bundles --left <path> --right <path>
              print-summary --bundle <path>
            Adapters: synthetic, synthetic-scale, firebird
            """);
    }
}
