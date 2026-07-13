package br.com.schf.migration.source.firebird;

import br.com.schf.migration.contract.BundleContract.Dataset;
import br.com.schf.migration.source.CheckpointStore;
import br.com.schf.migration.source.ExtractionReport;
import br.com.schf.migration.source.ProgressTracker;
import br.com.schf.migration.source.RecordHandler;
import br.com.schf.migration.source.StreamingSourceAdapter;
import br.com.schf.migration.source.firebird.mapping.CanonicalRecordMapper;
import br.com.schf.migration.source.firebird.mapping.CounterpartyResolver;
import br.com.schf.migration.source.firebird.mapping.CounterpartyResolver.CounterpartyInfo;
import br.com.schf.migration.source.firebird.mapping.CounterpartyResolver.ResolvedCounterparty;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FirebirdSourceAdapter implements StreamingSourceAdapter {
    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 7, 1);

    private final FirebirdConnectionFactory connectionFactory;
    private final FirebirdSchemaInspector schemaInspector;
    private final QueryCatalog catalog;
    private final FirebirdRowMapper rowMapper;
    private final FirebirdSourceConfiguration config;
    private final CanonicalRecordMapper canonicalMapper;

    public FirebirdSourceAdapter(FirebirdSourceConfiguration config) {
        this.config = config;
        this.connectionFactory = new FirebirdConnectionFactory(config);
        this.catalog = createCatalog(config);
        this.schemaInspector = new FirebirdSchemaInspector(catalog);
        this.rowMapper = new FirebirdRowMapper();
        this.canonicalMapper = createCanonicalMapper(config);
    }

    private static QueryCatalog createCatalog(FirebirdSourceConfiguration config) {
        return switch (config.profile()) {
            case SGH_FIREBIRD_25 -> new SghFirebird25QueryCatalog(config.extractionMode());
            case SYNTHETIC -> new FirebirdQueryCatalog();
        };
    }

    private CanonicalRecordMapper createCanonicalMapper(FirebirdSourceConfiguration config) {
        if (!config.profile().isSgh()) return null;
        try (var conn = connectionFactory.openReadOnly()) {
            var supplierInfos = loadSupplierInfos(conn);
            var contasInfos = loadContasInfos(conn);
            var colaboradorInfos = loadColaboradorInfos(conn);
            var resolver = new CounterpartyResolver(supplierInfos, contasInfos, colaboradorInfos);
            return new CanonicalRecordMapper(SNAPSHOT_DATE, resolver);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize canonical mapper: " + ex.getMessage(), ex);
        }
    }

    private Map<String, CounterpartyInfo> loadSupplierInfos(Connection conn) throws Exception {
        var map = new LinkedHashMap<String, CounterpartyInfo>();
        try (var stmt = conn.prepareStatement(catalog.query("counterparties-suppliers"));
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                var codigo = rs.getString("codigo_conta").strip();
                var nome = rs.getString("nome");
                map.put(codigo, new CounterpartyInfo(
                    catalog.buildExternalId(config.sourceInstanceId(), "counterparties",
                        Map.of("codigo_tipo_conta", "3", "codigo_conta", codigo)),
                    nome != null ? nome.strip() : null));
            }
        }
        return map;
    }

    private Map<String, CounterpartyInfo> loadContasInfos(Connection conn) throws Exception {
        var map = new LinkedHashMap<String, CounterpartyInfo>();
        try (var stmt = conn.prepareStatement(catalog.query("counterparties"));
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                var tipo = rs.getString("codigo_tipo_conta").strip();
                var codigo = rs.getString("codigo_conta").strip();
                var nome = rs.getString("nome");
                map.put(tipo + "|" + codigo, new CounterpartyInfo(
                    catalog.buildExternalId(config.sourceInstanceId(), "counterparties",
                        Map.of("codigo_tipo_conta", tipo, "codigo_conta", codigo)),
                    nome != null ? nome.strip() : null));
            }
        }
        return map;
    }

    private Map<String, CounterpartyInfo> loadColaboradorInfos(Connection conn) throws Exception {
        var map = new LinkedHashMap<String, CounterpartyInfo>();
        try (var stmt = conn.prepareStatement(catalog.query("counterparties-colaboradores"));
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                var tipo = rs.getString("codigo_tipo_conta").strip();
                var codigo = rs.getString("codigo_conta").strip();
                var nome = rs.getString("nome");
                map.put(tipo + "|" + codigo, new CounterpartyInfo(
                    catalog.buildExternalId(config.sourceInstanceId(), "counterparties",
                        Map.of("codigo_tipo_conta", tipo, "codigo_conta", codigo)),
                    nome != null ? nome.strip() : null));
            }
        }
        return map;
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
                Map.entry("counterparties", "count-counterparties"),
                Map.entry("payables", "count-payables"),
                Map.entry("users", "count-users"));
            for (var entry : counts) {
                if (!catalog.hasQuery(entry.getValue())) continue;
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
        if (canonicalMapper == null) {
            extractSynthetic(handler, checkpoints, progress);
            return;
        }
        try (var conn = connectionFactory.openReadOnly()) {
            extractPhaseCanonical(conn, handler, checkpoints, progress, "organizations", "organization", "count-organizations");
            extractPhaseCanonical(conn, handler, checkpoints, progress, "users", "users", "count-users");
            extractPhaseCanonical(conn, handler, checkpoints, progress, "suppliers", "suppliers", "count-suppliers");
            extractPhaseCanonical(conn, handler, checkpoints, progress, "categories", "categories", "count-categories");
            extractPhaseCanonical(conn, handler, checkpoints, progress, "financial-accounts", "financial-accounts", "count-accounts");
            extractPhaseRaw(conn, handler, checkpoints, progress, "counterparties", "counterparties", "count-counterparties");
            extractPayablesAndPayments(conn, handler, checkpoints, progress);
        }
    }

    private void extractSynthetic(RecordHandler handler, CheckpointStore checkpoints, ProgressTracker progress) throws Exception {
        try (var conn = connectionFactory.openReadOnly()) {
            extractPhaseSynthetic(conn, handler, checkpoints, progress, "organizations", "organization", "count-organizations");
            extractPhaseSynthetic(conn, handler, checkpoints, progress, "users", "users", "count-users");
            extractPhaseSynthetic(conn, handler, checkpoints, progress, "suppliers", "suppliers", "count-suppliers");
            extractPhaseSynthetic(conn, handler, checkpoints, progress, "categories", "categories", "count-categories");
            extractPhaseSynthetic(conn, handler, checkpoints, progress, "financial-accounts", "financial-accounts", "count-accounts");
            extractPayablesSynthetic(conn, handler, checkpoints, progress);
        }
    }

    private void extractPhaseSynthetic(Connection conn, RecordHandler handler, CheckpointStore checkpoints,
                                        ProgressTracker progress, String entityType, String queryKey, String countQueryKey) throws Exception {
        if (!catalog.hasQuery(queryKey) || checkpoints.hasCompleted(entityType)) return;
        progress.phaseStarted(entityType, estimateCount(conn, countQueryKey));
        try (var stmt = conn.prepareStatement(catalog.query(queryKey))) {
            stmt.setFetchSize(config.fetchSize());
            var max = catalog.maxRows(entityType);
            if (max > 0) stmt.setMaxRows(max);
            try (var rs = stmt.executeQuery()) {
                long count = 0;
                while (rs.next()) {
                    if (progress.isCancelled()) return;
                    var raw = rowMapper.mapRow(rs);
                    var externalId = catalog.buildExternalId(config.sourceInstanceId(), entityType, raw);
                    var m = new LinkedHashMap<>(raw);
                    m.put("externalId", externalId);
                    handler.accept(entityType, m);
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

    private void extractPayablesSynthetic(Connection conn, RecordHandler handler, CheckpointStore checkpoints,
                                           ProgressTracker progress) throws Exception {
        var entityType = "payables";
        if (!catalog.hasQuery("payables") || checkpoints.hasCompleted(entityType)) return;
        if (checkpoints.hasCompleted("payments")) return;
        progress.phaseStarted(entityType, estimateCount(conn, "count-payables"));
        try (var stmt = conn.prepareStatement(catalog.query("payables"))) {
            stmt.setFetchSize(config.fetchSize());
            try (var rs = stmt.executeQuery()) {
                long count = 0;
                while (rs.next()) {
                    if (progress.isCancelled()) return;
                    var raw = rowMapper.mapRow(rs);
                    var externalId = catalog.buildExternalId(config.sourceInstanceId(), entityType, raw);
                    var m = new LinkedHashMap<>(raw);
                    m.put("externalId", externalId);
                    handler.accept(entityType, m);
                    count++;
                }
                progress.recordsProcessed(entityType, count);
            }
        }
        checkpoints.markCompleted(entityType);
        checkpoints.markCompleted("payments");
        progress.phaseCompleted(entityType);
    }

    private void extractPayablesAndPayments(Connection conn, RecordHandler handler,
                                             CheckpointStore checkpoints, ProgressTracker progress) throws Exception {
        if (checkpoints.hasCompleted("payables") && checkpoints.hasCompleted("payments")) return;

        var estimatedCount = estimateCount(conn, "count-payables");
        progress.phaseStarted("payables", estimatedCount);

        // Pre-load supplier IDs for lookup
        var supplierExtIds = new LinkedHashMap<String, String>();
        try (var stmt = conn.prepareStatement("SELECT CODIGO FROM FORNECEDOR");
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                var codigo = rs.getString(1).strip();
                supplierExtIds.put(codigo, catalog.buildExternalId(config.sourceInstanceId(), "suppliers",
                    Map.of("codigo", codigo)));
            }
        }

        var categoryExtIds = new LinkedHashMap<String, String>();
        try (var stmt = conn.prepareStatement("SELECT ID_CLASSIFICACAO_FINANCEIRA FROM SFN_CLASSIFICACAO_FINANCEIRA");
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                var id = rs.getString(1).strip();
                categoryExtIds.put(id, catalog.buildExternalId(config.sourceInstanceId(), "categories",
                    Map.of("id_classificacao_financeira", id)));
            }
        }

        var accountExtIds = new LinkedHashMap<String, String>();
        try (var stmt = conn.prepareStatement("SELECT CODIGO_CONTA FROM CONTAS WHERE CODIGO_TIPO_CONTA = 6");
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                var codigo = rs.getString(1).strip();
                accountExtIds.put(codigo, catalog.buildExternalId(config.sourceInstanceId(), "financial-accounts",
                    Map.of("codigo_conta", codigo)));
            }
        }

        var payablesFilter = "SELECT CRP.*, CRP.CODIGO_TIPO_CONTA, CRP.CODIGO_CONTA, CRP.DOC_RCB_PGT, "
            + "CRP.DATA_EMISSAO_DOC_REFERENTE AS EMISSAO, CRP.DATA_VENCIMENTO AS VENCIMENTO, "
            + "CRP.VALOR, CRP.VALORPAGO, CRP.DATA_PAGAMENTO, CRP.COMPLEMENTO_HISTORICO AS HISTORICO, "
            + "CRP.DOC_RCB_PGT AS DOCUMENTO, CRP.FORMA_PR, CRP.PREVISTO_REALIZADO "
            + "FROM CONTAS_RECEBER_PAGAR CRP WHERE RCB_PGT = 'P' "
            + "AND (EXCLUIR IS NULL OR EXCLUIR <> 'S') AND VALOR > 0 ORDER BY DATA_VENCIMENTO";

        var max = catalog.maxRows("payables");
        try (var stmt = conn.prepareStatement(payablesFilter)) {
            if (max > 0) stmt.setMaxRows(max);
            try (var rs = stmt.executeQuery()) {
                long count = 0;
                while (rs.next()) {
                    if (progress.isCancelled()) return;
                    var raw = rowMapper.mapRow(rs);
                    var externalId = catalog.buildExternalId(config.sourceInstanceId(), "payables", raw);

                    // Resolve supplier/category/account external IDs
                    var codigoFornecedor = string(raw, "codigo_conta");
                    var codigoCat = string(raw, "id_classificacao_financeira");
                    if (codigoCat == null) codigoCat = string(raw, "codigo_classificacao");
                    var codigoConta = string(raw, "numero_operacao");

                    var sExt = supplierExtIds.get(codigoFornecedor);
                    var cExt = codigoCat != null ? categoryExtIds.get(codigoCat) : null;
                    var aExt = codigoConta != null ? accountExtIds.get(codigoConta) : null;

                    var payableResult = canonicalMapper.mapPayable(raw, externalId, cExt, aExt);
                    handler.accept("payables", payableResult.canonical());
                    count++;

                    // Create payment record if paid
                    var valorPagoStr = normalizeMoney(raw, "valorpago");
                    if (valorPagoStr != null && !"0.00".equals(valorPagoStr)) {
                        var paymentExtId = externalId + "|PAYMENT";
                        raw.put("paymentExternalId", paymentExtId);
                        var paymentResult = canonicalMapper.mapPayment(raw, externalId);
                        handler.accept("payments", paymentResult.canonical());
                    }

                    if (count % config.batchSize() == 0) {
                        checkpoints.save("payables", Map.of("lastCount", count));
                        progress.recordsProcessed("payables", count);
                    }
                }
                progress.recordsProcessed("payables", count);
            }
        }
        checkpoints.markCompleted("payables");
        checkpoints.markCompleted("payments");
        progress.phaseCompleted("payables");
    }

    private void extractPhaseCanonical(Connection conn, RecordHandler handler, CheckpointStore checkpoints,
                                        ProgressTracker progress, String entityType, String queryKey, String countQueryKey) throws Exception {
        if (!catalog.hasQuery(queryKey) || checkpoints.hasCompleted(entityType)) return;
        progress.phaseStarted(entityType, estimateCount(conn, countQueryKey));
        try (var stmt = conn.prepareStatement(catalog.query(queryKey))) {
            stmt.setFetchSize(config.fetchSize());
            var max = catalog.maxRows(entityType);
            if (max > 0) stmt.setMaxRows(max);
            try (var rs = stmt.executeQuery()) {
                long count = 0;
                while (rs.next()) {
                    if (progress.isCancelled()) return;
                    var raw = rowMapper.mapRow(rs);
                    var externalId = catalog.buildExternalId(config.sourceInstanceId(), entityType, raw);
                    var canonical = mapCanonical(entityType, raw, externalId);
                    handler.accept(entityType, canonical);
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

    private void extractPhaseRaw(Connection conn, RecordHandler handler, CheckpointStore checkpoints,
                                  ProgressTracker progress, String entityType, String queryKey, String countQueryKey) throws Exception {
        if (!catalog.hasQuery(queryKey) || checkpoints.hasCompleted(entityType)) return;
        var count = estimateCount(conn, countQueryKey);
        progress.phaseStarted(entityType, count);
        if (count == 0) {
            progress.phaseCompleted(entityType);
            return;
        }
        try (var stmt = conn.prepareStatement(catalog.query(queryKey))) {
            stmt.setFetchSize(config.fetchSize());
            try (var rs = stmt.executeQuery()) {
                long processed = 0;
                while (rs.next()) {
                    if (progress.isCancelled()) return;
                    var raw = rowMapper.mapRow(rs);
                    var externalId = catalog.buildExternalId(config.sourceInstanceId(), entityType, raw);

                    var tipoConta = string(raw, "codigo_tipo_conta");
                    var codConta = string(raw, "codigo_conta");
                    var nome = string(raw, "nome");
                    var resolved = canonicalMapper.counterpartyResolver().resolve(
                        tipoConta != null ? Integer.parseInt(tipoConta) : 0, codConta);

                    var canonical = canonicalMapper.mapCounterparty(externalId, resolved);
                    handler.accept(entityType, canonical);
                    processed++;
                }
                checkpoints.markCompleted(entityType);
                progress.phaseCompleted(entityType);
            }
        }
    }

    private Map<String, Object> mapCanonical(String entityType, Map<String, Object> raw, String externalId) {
        return switch (entityType) {
            case "organizations" -> canonicalMapper.mapOrganization(raw, externalId);
            case "suppliers" -> canonicalMapper.mapSupplier(raw, externalId);
            case "users" -> canonicalMapper.mapUser(raw, externalId);
            case "categories" -> canonicalMapper.mapCategory(raw, externalId);
            case "financial-accounts" -> canonicalMapper.mapFinancialAccount(raw, externalId);
            default -> {
                var m = new LinkedHashMap<>(raw);
                m.put("externalId", externalId);
                yield m;
            }
        };
    }

    private long estimateCount(Connection conn, String queryKey) {
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(catalog.query(queryKey))) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception ex) {
            return -1;
        }
    }

    private String normalizeMoney(Map<String, Object> raw, String... keys) {
        for (var key : keys) {
            var val = raw.get(key);
            if (val == null) continue;
            var str = val.toString().replace(",", ".").replaceAll("[^0-9.-]", "");
            try {
                var d = Double.parseDouble(str);
                return String.format(java.util.Locale.US, "%.2f", Math.abs(d));
            } catch (NumberFormatException e) {
                return "0.00";
            }
        }
        return null;
    }

    private String string(Map<String, Object> raw, String key) {
        var val = raw.get(key);
        return val == null ? null : val.toString().strip();
    }

    public FirebirdSourceConfiguration config() { return config; }
    public FirebirdConnectionFactory connectionFactory() { return connectionFactory; }
    public FirebirdSchemaInspector schemaInspector() { return schemaInspector; }
}
