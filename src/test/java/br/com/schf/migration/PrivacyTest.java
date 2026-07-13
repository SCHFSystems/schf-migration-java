package br.com.schf.migration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.schf.migration.source.firebird.mapping.CategoryLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.CounterpartyResolver;
import br.com.schf.migration.source.firebird.mapping.DateValidator;
import br.com.schf.migration.source.firebird.mapping.FinancialAccountLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.PayableLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.PaymentLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.SupplierLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.UserLegacyMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrivacyTest {

    private static final LocalDate SNAPSHOT = LocalDate.of(2026, 7, 1);
    private static final DateValidator DATE_VALIDATOR = new DateValidator(SNAPSHOT);
    private static final CounterpartyResolver EMPTY_RESOLVER = new CounterpartyResolver(Map.of(), Map.of(), Map.of());

    private static final String SYNTHETIC_CPF = "111.222.333-44";
    private static final String SYNTHETIC_CNPJ = "99.888.777/0001-66";
    private static final String SYNTHETIC_NAME = "PII Test User";
    private static final String SYNTHETIC_EMAIL = "pii-test@santacasa.example.com";
    private static final String SYNTHETIC_PHONE = "+55-11-99999-8888";
    private static final String SYNTHETIC_ACCOUNT = "12345-6";
    private static final String SYNTHETIC_AGENCY = "4321";

    private final ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void captureStreams() {
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(outCapture));
        System.setErr(new PrintStream(errCapture));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void supplierMappingDoesNotLeakPii() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("nomefor", SYNTHETIC_NAME);
        raw.put("cgc", SYNTHETIC_CNPJ);
        raw.put("email", SYNTHETIC_EMAIL);
        raw.put("telefone", SYNTHETIC_PHONE);
        raw.put("ativo", "S");
        new SupplierLegacyMapper().normalize(raw);
        assertNoPiiInLogs();
    }

    @Test
    void supplierMappingDoesNotLeakPiiWithCpf() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("nome", SYNTHETIC_NAME);
        raw.put("cpf", SYNTHETIC_CPF);
        raw.put("ativo", "S");
        new SupplierLegacyMapper().normalize(raw);
        assertNoPiiInLogs();
    }

    @Test
    void userMappingDoesNotLeakPii() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("login", "pii_user");
        raw.put("nome", SYNTHETIC_NAME);
        raw.put("email", SYNTHETIC_EMAIL);
        raw.put("perfil", "ADMIN");
        new UserLegacyMapper().normalize(raw);
        assertNoPiiInLogs();
    }

    @Test
    void financialAccountMappingDoesNotLeakPii() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("descricao", "Conta PII Test");
        raw.put("banco", "Banco Test");
        raw.put("agencia", SYNTHETIC_AGENCY);
        raw.put("conta", SYNTHETIC_ACCOUNT);
        new FinancialAccountLegacyMapper().normalize(raw);
        assertNoPiiInLogs();
    }

    @Test
    void categoryMappingDoesNotLeakPii() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("descricao", "Categoria Teste");
        raw.put("tipo", "DESPESA");
        new CategoryLegacyMapper().normalize(raw);
        assertNoPiiInLogs();
    }

    @Test
    void payableMappingDoesNotLeakPii() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("complemento_historico", "Pagamento para " + SYNTHETIC_NAME);
        raw.put("documento", "NF-PII-001");
        raw.put("valor", "1500,00");
        raw.put("emissao", "2026-01-15");
        raw.put("vencimento", "2026-02-15");
        raw.put("valorpago", "1500.00");
        raw.put("datapagamento", "2026-02-10");
        raw.put("codigo_tipo_conta", "3");
        raw.put("codigo_conta", "1");
        var mapper = new PayableLegacyMapper(DATE_VALIDATOR, EMPTY_RESOLVER, SNAPSHOT);
        mapper.normalize(raw, "P-PII", "C-EXT", "A-EXT", "3");
        assertNoPiiInLogs();
    }

    @Test
    void paymentMappingDoesNotLeakPii() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("datapagamento", "2026-02-10");
        raw.put("valorpago", "1500,00");
        raw.put("forma_pr", "B");
        var mapper = new PaymentLegacyMapper(DATE_VALIDATOR);
        mapper.normalize(raw, "P-PII");
        assertNoPiiInLogs();
    }

    @Test
    void supplierMappingWithMissingFieldsDoesNotLeakPii() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("nomefor", SYNTHETIC_NAME);
        var result = new SupplierLegacyMapper().normalize(raw);
        assertThat(result).isNotNull();
        assertThat(result.get("document")).isNull();
        assertNoPiiInLogs();
    }

    @Test
    void userMappingWithInvalidRoleDoesNotLeakPii() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("login", "pii_user");
        raw.put("nome", SYNTHETIC_NAME);
        raw.put("perfil", null);
        new UserLegacyMapper().normalize(raw);
        assertNoPiiInLogs();
    }

    @Test
    void financialAccountMappingWithNullFieldsDoesNotLeakPii() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("descricao", "Conta Teste");
        raw.put("banco", null);
        raw.put("agencia", null);
        raw.put("conta", null);
        new FinancialAccountLegacyMapper().normalize(raw);
        assertNoPiiInLogs();
    }

    private void assertNoPiiInLogs() {
        var combined = outCapture.toString() + errCapture.toString();
        assertThat(combined)
            .as("PII markers must not appear in stdout or stderr")
            .doesNotContain(SYNTHETIC_CPF)
            .doesNotContain(SYNTHETIC_CNPJ)
            .doesNotContain(SYNTHETIC_NAME)
            .doesNotContain(SYNTHETIC_EMAIL)
            .doesNotContain(SYNTHETIC_PHONE)
            .doesNotContain(SYNTHETIC_ACCOUNT)
            .doesNotContain(SYNTHETIC_AGENCY);
    }
}
