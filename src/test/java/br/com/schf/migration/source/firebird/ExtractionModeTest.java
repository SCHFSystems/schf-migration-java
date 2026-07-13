package br.com.schf.migration.source.firebird;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtractionModeTest {

    @Test
    void limitedModeReturnsMaxRows() {
        var catalog = new SghFirebird25QueryCatalog(ExtractionMode.LIMITED_VALIDATION);
        assertThat(catalog.maxRows("suppliers")).isEqualTo(10);
        assertThat(catalog.maxRows("categories")).isEqualTo(10);
        assertThat(catalog.maxRows("financial-accounts")).isEqualTo(10);
        assertThat(catalog.maxRows("payables")).isEqualTo(10);
        assertThat(catalog.maxRows("users")).isEqualTo(10);
        assertThat(catalog.maxRows("organizations")).isEqualTo(1);
    }

    @Test
    void limitedModeHasNoRowLimitForPayments() {
        var catalog = new SghFirebird25QueryCatalog(ExtractionMode.LIMITED_VALIDATION);
        assertThat(catalog.maxRows("payments")).isZero();
    }

    @Test
    void fullModeReturnsNoMaxRows() {
        var catalog = new SghFirebird25QueryCatalog(ExtractionMode.FULL_EXTRACTION);
        assertThat(catalog.maxRows("suppliers")).isZero();
        assertThat(catalog.maxRows("categories")).isZero();
        assertThat(catalog.maxRows("financial-accounts")).isZero();
        assertThat(catalog.maxRows("payables")).isZero();
        assertThat(catalog.maxRows("payments")).isZero();
        assertThat(catalog.maxRows("users")).isZero();
        assertThat(catalog.maxRows("organizations")).isZero();
    }

    @Test
    void limitedPaymentsQueryContainsFirstClauses() {
        var catalog = new SghFirebird25QueryCatalog(ExtractionMode.LIMITED_VALIDATION);
        var query = catalog.query("payments");
        assertThat(query).contains("FIRST 3");
        assertThat(query).contains("UNION ALL");
        assertThat(query).contains("FORMA_PR='B'");
        assertThat(query).contains("FORMA_PR='C'");
        assertThat(query).contains("VALORPAGO<VALOR");
        assertThat(query).contains("VALORPAGO>VALOR");
        assertThat(query).contains("VALORPAGO=VALOR");
        assertThat(query).contains("EXCLUIR='S'");
    }

    @Test
    void fullPaymentsQueryDoesNotContainFirstClauses() {
        var catalog = new SghFirebird25QueryCatalog(ExtractionMode.FULL_EXTRACTION);
        var query = catalog.query("payments");
        assertThat(query).doesNotContain("FIRST");
        assertThat(query).doesNotContain("UNION ALL");
        assertThat(query).contains("VALORPAGO>0");
    }

    @Test
    void entityQueriesDoNotContainFirstInAnyMode() {
        for (var mode : ExtractionMode.values()) {
            var catalog = new SghFirebird25QueryCatalog(mode);
            for (var entity : new String[]{"suppliers", "categories", "financial-accounts", "payables", "users"}) {
                assertThat(catalog.query(entity))
                    .as("Query for " + entity + " in mode " + mode + " must not hardcode FIRST")
                    .doesNotContain("FIRST");
            }
        }
    }

    @Test
    void externalIdIsSameAcrossModes() {
        var row = new LinkedHashMap<String, Object>();
        row.put("codigo", "123");
        row.put("nome", "Test Supplier");
        var idLimited = new SghFirebird25QueryCatalog(ExtractionMode.LIMITED_VALIDATION)
            .buildExternalId("inst-1", "suppliers", row);
        var idFull = new SghFirebird25QueryCatalog(ExtractionMode.FULL_EXTRACTION)
            .buildExternalId("inst-1", "suppliers", row);
        assertThat(idLimited).isEqualTo(idFull);
    }

    @Test
    void financialFiltersAreSameAcrossModes() {
        for (var mode : ExtractionMode.values()) {
            var catalog = new SghFirebird25QueryCatalog(mode);
            assertThat(catalog.query("count-accounts")).contains("CODIGO_TIPO_CONTA = 6");
            assertThat(catalog.query("count-payables")).contains("RCB_PGT = 'P'");
            assertThat(catalog.query("count-payables")).contains("VALOR > 0");
            assertThat(catalog.query("count-payments")).contains("VALORPAGO > 0");
        }
    }

    @Test
    void defaultConstructorUsesLimitedMode() {
        var catalog = new SghFirebird25QueryCatalog();
        assertThat(catalog.maxRows("suppliers")).isEqualTo(10);
    }


}
