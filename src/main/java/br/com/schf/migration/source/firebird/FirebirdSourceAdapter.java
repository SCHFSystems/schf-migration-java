package br.com.schf.migration.source.firebird;

import br.com.schf.migration.contract.BundleContract.Dataset;
import br.com.schf.migration.source.CheckpointStore;
import br.com.schf.migration.source.ExtractionReport;
import br.com.schf.migration.source.ProgressTracker;
import br.com.schf.migration.source.RecordHandler;
import br.com.schf.migration.source.StreamingSourceAdapter;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FirebirdSourceAdapter implements StreamingSourceAdapter {
    private final FirebirdConnectionFactory connectionFactory;
    private final FirebirdSchemaInspector schemaInspector;
    private final FirebirdQueryCatalog catalog;
    private final FirebirdRowMapper rowMapper;
    private final FirebirdSourceConfiguration config;

    public FirebirdSourceAdapter(FirebirdSourceConfiguration config) {
        this.config = config;
        this.connectionFactory = new FirebirdConnectionFactory(config);
        this.catalog = new FirebirdQueryCatalog();
        this.schemaInspector = new FirebirdSchemaInspector(catalog);
        this.rowMapper = new FirebirdRowMapper();
    }

    @Override
    public String id() {
        return config.sourceId();
    }

    @Override
    public Map<String, Long> analyze() {
        try (var conn = connectionFactory.openReadOnly()) {
            var result = new LinkedHashMap<String, Long>();
            var counts = List.of(
                Map.entry("organizations", "count-organizations"),
                Map.entry("suppliers", "count-suppliers"),
                Map.entry("categories", "count-categories"),
                Map.entry("financial-accounts", "count-accounts"),
                Map.entry("payables", "count-payables"),
                Map.entry("users", "count-users"));
            for (var entry : counts) {
                try (var stmt = conn.createStatement();
                     var rs = stmt.executeQuery(catalog.query(entry.getValue()))) {
                    if (rs.next()) result.put(entry.getKey(), rs.getLong(1));
                }
            }
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to analyze source: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Dataset readCanonical() {
        throw new UnsupportedOperationException(
            "Firebird adapter does not support in-memory readCanonical(). Use extractTo() for streaming.");
    }

    @Override
    public void extractTo(RecordHandler handler, CheckpointStore checkpoints, ProgressTracker progress) throws Exception {
        try (var conn = connectionFactory.openReadOnly()) {
            extractPhase(conn, handler, checkpoints, progress, "organizations", "organization", "count-organizations");
            extractPhase(conn, handler, checkpoints, progress, "users", "users", "count-users");
            extractPhase(conn, handler, checkpoints, progress, "suppliers", "suppliers", "count-suppliers");
            extractPhase(conn, handler, checkpoints, progress, "categories", "categories", "count-categories");
            extractPhase(conn, handler, checkpoints, progress, "financial-accounts", "financial-accounts", "count-accounts");
            extractPhase(conn, handler, checkpoints, progress, "payables", "payables", "count-payables");
            extractPhase(conn, handler, checkpoints, progress, "payments", "payments", "count-payables");
        }
    }

    private void extractPhase(Connection conn, RecordHandler handler, CheckpointStore checkpoints,
                               ProgressTracker progress, String entityType, String queryKey, String countQueryKey) throws Exception {
        if (!catalog.hasQuery(queryKey) || checkpoints.hasCompleted(entityType)) {
            return;
        }
        progress.phaseStarted(entityType, estimateCount(conn, countQueryKey));
        try (var stmt = conn.prepareStatement(catalog.query(queryKey))) {
            stmt.setFetchSize(config.fetchSize());
            try (var rs = stmt.executeQuery()) {
                long count = 0;
                while (rs.next()) {
                    if (progress.isCancelled()) return;
                    var row = rowMapper.mapRow(rs);
                    var externalId = buildExternalId(entityType, row);
                    row.put("externalId", externalId);
                    handler.accept(entityType, row);
                    count++;
                    if (count % config.batchSize() == 0) {
                        checkpoints.save(entityType, Map.of("lastCount", count));
                        progress.recordsProcessed(entityType, count);
                    }
                }
                progress.recordsProcessed(entityType, count);
            }
        }
        checkpoints.markCompleted(entityType);
        progress.phaseCompleted(entityType);
    }

    private long estimateCount(Connection conn, String queryKey) {
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(catalog.query(queryKey))) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception ex) {
            return -1;
        }
    }

    private String buildExternalId(String entityType, Map<String, Object> row) {
        var sourceId = config.sourceInstanceId();
        var idColumn = switch (entityType) {
            case "suppliers" -> "codfor";
            case "categories" -> "codcat";
            case "financial-accounts" -> "codctg";
            case "payables" -> "coddcto";
            case "payments" -> "codpag";
            case "users" -> "codusu";
            case "organizations" -> "codorg";
            default -> "id";
        };
        var pk = row.getOrDefault(idColumn, "unknown").toString();
        return UUID.nameUUIDFromBytes((sourceId + "|" + entityType + "|" + pk).getBytes()).toString();
    }

    public FirebirdSourceConfiguration config() { return config; }
    public FirebirdConnectionFactory connectionFactory() { return connectionFactory; }
    public FirebirdSchemaInspector schemaInspector() { return schemaInspector; }
}
