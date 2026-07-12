package br.com.schf.migration.source.firebird;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.schf.migration.bundle.BundleComparer;
import br.com.schf.migration.bundle.CanonicalBundleStreamingHandler;
import br.com.schf.migration.bundle.CanonicalBundleValidator;
import br.com.schf.migration.source.ExtractionReport;
import br.com.schf.migration.source.ProgressTracker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
@EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
class FirebirdIntegrationTest {

    private static final String INSTANCE_ID = "ci-test-001";
    private static final int FB_PORT = 3050;

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> FIREBIRD = new GenericContainer<>("firebirdsql/firebird:5")
        .withEnv("FIREBIRD_DATABASE", "SCHF_TEST")
        .withEnv("FIREBIRD_PASSWORD", "schf_test_pwd")
        .withEnv("ISC_PASSWORD", "schf_test_pwd")
        .withExposedPorts(FB_PORT);

    private static FirebirdSourceConfiguration config;
    private static FirebirdSourceAdapter adapter;

    @BeforeAll
    static void setup() throws Exception {
        var host = FIREBIRD.getHost();
        var port = FIREBIRD.getMappedPort(FB_PORT);
        var jdbcUrl = "jdbc:firebirdsql://" + host + ":" + port + "/SCHF_TEST";
        config = new FirebirdSourceConfiguration(
            jdbcUrl, "sysdba", "schf_test_pwd",
            "firebird-sgh", INSTANCE_ID,
            100, 10,
            Path.of(System.getProperty("java.io.tmpdir"), "schf-it-checkpoints"),
            Path.of(System.getProperty("java.io.tmpdir"), "schf-it-reports")
        );
        adapter = new FirebirdSourceAdapter(config);
        initSchema(jdbcUrl);
    }

    private static void initSchema(String jdbcUrl) throws Exception {
        try (var conn = DriverManager.getConnection(jdbcUrl, "sysdba", "schf_test_pwd");
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE ORGANIZACAO (CODORG INTEGER PRIMARY KEY, NOME VARCHAR(100))");
            stmt.execute("INSERT INTO ORGANIZACAO VALUES (1, 'SCHF Synthetic')");
            stmt.execute("CREATE TABLE FORNECEDOR (CODFOR INTEGER PRIMARY KEY, NOMEFOR VARCHAR(100), CGC VARCHAR(20), EMAIL VARCHAR(100), TELEFONE VARCHAR(20), ATIVO CHAR(1))");
            stmt.execute("INSERT INTO FORNECEDOR VALUES (1, 'Fornecedor A', '12.345.678/0001-90', 'fornecedor@example.com', '11999999999', 'S')");
            stmt.execute("INSERT INTO FORNECEDOR VALUES (2, 'Fornecedor B', NULL, NULL, NULL, 'N')");
            stmt.execute("CREATE TABLE CATEGORIA (CODCAT INTEGER PRIMARY KEY, DESCRICAO VARCHAR(100), TIPO VARCHAR(20))");
            stmt.execute("INSERT INTO CATEGORIA VALUES (1, 'Material de Escritorio', 'DESPESA')");
            stmt.execute("INSERT INTO CATEGORIA VALUES (2, 'Receita Financeira', 'RECEITA')");
            stmt.execute("CREATE TABLE CONTA_BANCARIA (CODCTG INTEGER PRIMARY KEY, DESCRICAO VARCHAR(100), BANCO VARCHAR(50), AGENCIA VARCHAR(10), CONTA VARCHAR(20))");
            stmt.execute("INSERT INTO CONTA_BANCARIA VALUES (1, 'Conta Corrente', 'Banco do Brasil', '1234', '56789-0')");
            stmt.execute("INSERT INTO CONTA_BANCARIA VALUES (2, 'Conta Salario', NULL, NULL, NULL)");
            stmt.execute("CREATE TABLE CONTA_PAGAR (CODDCTO INTEGER PRIMARY KEY, HISTORICO VARCHAR(200), DOCUMENTO VARCHAR(50), CODFOR INTEGER, CODCAT INTEGER, CODCTG INTEGER, EMISSAO DATE, VENCIMENTO DATE, VALOR NUMERIC(15,2), STATUS VARCHAR(20))");
            stmt.execute("INSERT INTO CONTA_PAGAR VALUES (1, 'Pagamento de servico', 'NF-123', 1, 1, 1, '2026-01-15', '2026-02-15', 1500.50, 'PENDENTE')");
            stmt.execute("INSERT INTO CONTA_PAGAR VALUES (2, 'Servico concluido', 'NF-456', 1, 1, 1, '2026-03-01', '2026-03-15', 3200.00, 'PAGO')");
            stmt.execute("CREATE TABLE PAGAMENTO (CODPAG INTEGER PRIMARY KEY, CODDCTO INTEGER, DATAPAG DATE, VALOR NUMERIC(15,2), FORMA VARCHAR(30))");
            stmt.execute("INSERT INTO PAGAMENTO VALUES (1, 2, '2026-03-10', 3200.00, 'BOLETO')");
            stmt.execute("CREATE TABLE USUARIO (CODUSU INTEGER PRIMARY KEY, LOGIN VARCHAR(50), EMAIL VARCHAR(100), NOME VARCHAR(100), PERFIL VARCHAR(20))");
            stmt.execute("INSERT INTO USUARIO VALUES (1, 'joao', 'joao@example.com', 'Joao Silva', 'ADMIN')");
            stmt.execute("INSERT INTO USUARIO VALUES (2, 'maria', 'maria@example.com', 'Maria Souza', 'FINANCE')");
        }
    }

    @Test
    void validatesConnection() {
        adapter.connectionFactory().validateConnection();
    }

    @Test
    void connectionIsReadOnly() throws Exception {
        try (var conn = adapter.connectionFactory().openReadOnly()) {
            assertThat(conn.isReadOnly()).isTrue();
        }
    }

    @Test
    void inspectsSchema() throws Exception {
        try (var conn = adapter.connectionFactory().openReadOnly()) {
            var schema = adapter.schemaInspector().inspect(conn);
            assertThat(schema.get("expectedTablesPresent"))
                .asList()
                .contains("ORGANIZACAO", "FORNECEDOR", "CATEGORIA", "CONTA_BANCARIA", "CONTA_PAGAR", "PAGAMENTO", "USUARIO");
            assertThat(schema.get("expectedTablesMissing"))
                .asList()
                .isEmpty();
        }
    }

    @Test
    void queriesReturnExpectedCounts() {
        var analysis = adapter.analyze();
        assertThat(analysis.get("suppliers")).isEqualTo(2L);
        assertThat(analysis.get("categories")).isEqualTo(2L);
        assertThat(analysis.get("financial-accounts")).isEqualTo(2L);
        assertThat(analysis.get("payables")).isEqualTo(2L);
        assertThat(analysis.get("users")).isEqualTo(2L);
    }

    @Test
    void streamsAllEntities(@TempDir Path tempDir) throws Exception {
        var output = tempDir.resolve("bundle-streaming.schf");
        var checkpoints = new FirebirdCheckpointStore(config.workDirectory());
        checkpoints.clear();
        var report = new ExtractionReport();
        var progress = dummyProgress();
        var handler = new CanonicalBundleStreamingHandler(output, report, config.batchSize());
        adapter.extractTo(handler, checkpoints, progress);
        handler.finish();
        assertThat(Files.size(output)).isPositive();
        new CanonicalBundleValidator().validate(output);
    }

    @Test
    void checkpointsPersistAndResume() throws Exception {
        var checkpoints = new FirebirdCheckpointStore(config.workDirectory());
        checkpoints.clear();
        assertThat(checkpoints.exists()).isFalse();
        checkpoints.save("suppliers", Map.of("lastCount", 10L));
        checkpoints.markCompleted("suppliers");
        checkpoints.save("categories", Map.of("lastCount", 5L));
        assertThat(checkpoints.hasCompleted("suppliers")).isTrue();
        assertThat(checkpoints.hasCompleted("categories")).isFalse();
        assertThat(checkpoints.exists()).isTrue();
        checkpoints.clear();
        assertThat(checkpoints.exists()).isFalse();
    }

    @Test
    void externalIdsAreDeterministic(@TempDir Path tempDir) throws Exception {
        var checkpoints = new FirebirdCheckpointStore(config.workDirectory());
        checkpoints.clear();
        var output = tempDir.resolve("deterministic-ids.schf");
        var handler = new CanonicalBundleStreamingHandler(output, new ExtractionReport(), config.batchSize());
        adapter.extractTo(handler, checkpoints, dummyProgress());
        handler.finish();
        var manifest = handler.getManifest();
        assertThat(manifest).containsKey("recordsByEntity");
    }

    @Test
    void bundleCompareIdentical(@TempDir Path tempDir) throws Exception {
        var aPath = tempDir.resolve("bundle-compare-a.schf");
        var bPath = tempDir.resolve("bundle-compare-b.schf");
        for (var path : new Path[]{aPath, bPath}) {
            var cp = new FirebirdCheckpointStore(config.workDirectory());
            cp.clear();
            var handler = new CanonicalBundleStreamingHandler(path, new ExtractionReport(), config.batchSize());
            adapter.extractTo(handler, cp, dummyProgress());
            handler.finish();
        }
        var result = new BundleComparer().compare(aPath, bPath);
        assertThat(result.isIdentical()).isTrue();
    }

    private static ProgressTracker dummyProgress() {
        var start = Instant.now();
        return new ProgressTracker() {
            final Map<String, AtomicLong> counts = new java.util.LinkedHashMap<>();
            @Override public void phaseStarted(String phase, long total) {}
            @Override public void recordsProcessed(String phase, long count) {
                counts.computeIfAbsent(phase, k -> new AtomicLong()).set(count);
            }
            @Override public void phaseCompleted(String phase) {}
            @Override public void reportError(String phase, String entityType, String id, String code, String d) {}
            @Override public boolean isCancelled() { return false; }
            @Override public Duration elapsed() { return Duration.between(start, Instant.now()); }
            @Override public long processedCount(String phase) { return counts.getOrDefault(phase, new AtomicLong()).longValue(); }
        };
    }
}
