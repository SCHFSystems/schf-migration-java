package br.com.schf.migration.source.firebird;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.schf.migration.source.firebird.mapping.CategoryLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.CounterpartyResolver;
import br.com.schf.migration.source.firebird.mapping.DateValidator;
import br.com.schf.migration.source.firebird.mapping.FinancialAccountLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.PayableLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.PaymentLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.SupplierLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.UserLegacyMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapperTest {
    private static final LocalDate SNAPSHOT = LocalDate.of(2026, 7, 1);
    private static final DateValidator DATE_VALIDATOR = new DateValidator(SNAPSHOT);
    private static final CounterpartyResolver EMPTY_RESOLVER = new CounterpartyResolver(Map.of());

    @Test
    void supplierNormalizesDocument() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("cgc", "12.345.678/0001-90");
        var result = new SupplierLegacyMapper().normalize(raw);
        assertThat(result.get("document")).isEqualTo("12345678000190");
    }

    @Test
    void supplierMapsCommonFields() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("nomefor", "Fornecedor Teste");
        raw.put("email", "teste@example.com");
        raw.put("telefone", "11999999999");
        raw.put("ativo", "S");
        var result = new SupplierLegacyMapper().normalize(raw);
        assertThat(result.get("name")).isEqualTo("Fornecedor Teste");
        assertThat(result.get("email")).isEqualTo("teste@example.com");
        assertThat(result.get("phone")).isEqualTo("11999999999");
        assertThat(result.get("active")).isEqualTo(true);
    }

    @Test
    void supplierMapsSghColumns() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("nome", "Fornecedor SGH");
        raw.put("cnpj_cpf", "99.888.777/0001-66");
        raw.put("email", "fornecedor@sgh.example.com");
        raw.put("fone", "1133334444");
        raw.put("desativado", null);
        var result = new SupplierLegacyMapper().normalize(raw);
        assertThat(result.get("name")).isEqualTo("Fornecedor SGH");
        assertThat(result.get("document")).isEqualTo("99888777000166");
        assertThat(result.get("phone")).isEqualTo("1133334444");
        assertThat(result.get("active")).isEqualTo(true);
    }

    @Test
    void supplierInactiveWhenDesativadoS() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("nome", "Inactive Supplier");
        raw.put("desativado", "S");
        var result = new SupplierLegacyMapper().normalize(raw);
        assertThat(result.get("active")).isEqualTo(false);
    }

    @Test
    void categoryDefaultsToExpense() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("descricao", "Material de Escritorio");
        var result = new CategoryLegacyMapper().normalize(raw);
        assertThat(result.get("name")).isEqualTo("Material de Escritorio");
        assertThat(result.get("type")).isEqualTo("EXPENSE");
    }

    @Test
    void categoryMapsIncomeType() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("descricao", "Receita Financeira");
        raw.put("tipo", "RECEITA");
        var result = new CategoryLegacyMapper().normalize(raw);
        assertThat(result.get("type")).isEqualTo("INCOME");
    }

    @Test
    void categoryExcludedWhenExcluirS() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("descricao", "Excluded Category");
        raw.put("excluir", "S");
        var result = new CategoryLegacyMapper().normalize(raw);
        assertThat(result.get("active")).isEqualTo(false);
    }

    @Test
    void financialAccountNormalizes() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("descricao", "Conta Corrente");
        raw.put("banco", "Banco do Brasil");
        raw.put("agencia", "1234");
        raw.put("conta", "56789-0");
        var result = new FinancialAccountLegacyMapper().normalize(raw);
        assertThat(result.get("name")).isEqualTo("Conta Corrente");
        assertThat(result.get("bankName")).isEqualTo("Banco do Brasil");
        assertThat(result.get("agency")).isEqualTo("1234");
        assertThat(result.get("accountNumber")).isEqualTo("56789-0");
    }

    @Test
    void financialAccountWithSghColumns() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("nome", "BANCO DO BRASIL");
        raw.put("banco", "Banco do Brasil");
        raw.put("agencia", "4321");
        raw.put("conta", "12345-6");
        raw.put("excluir", null);
        var result = new FinancialAccountLegacyMapper().normalize(raw);
        assertThat(result.get("name")).isEqualTo("BANCO DO BRASIL");
        assertThat(result.get("active")).isEqualTo(true);
    }

    @Test
    void financialAccountExcludedWhenExcluirS() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("nome", "Excluded Account");
        raw.put("excluir", "S");
        var result = new FinancialAccountLegacyMapper().normalize(raw);
        assertThat(result.get("active")).isEqualTo(false);
    }

    @Test
    void payableNormalizesMoney() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("complemento_historico", "Pagamento de servico");
        raw.put("documento", "NF-123");
        raw.put("valor", "1500,50");
        raw.put("emissao", "2026-01-15");
        raw.put("vencimento", "2026-02-15");
        raw.put("valorpago", "1500.50");
        raw.put("datapagamento", "2026-02-10");
        raw.put("codigo_tipo_conta", "3");
        raw.put("codigo_conta", "123");
        var mapper = new PayableLegacyMapper(DATE_VALIDATOR, EMPTY_RESOLVER, SNAPSHOT);
        var result = mapper.normalize(raw, "P-EXT", "C-EXT", "A-EXT", "3");
        assertThat(result.canonical().get("description")).isEqualTo("Pagamento de servico");
        assertThat(result.canonical().get("amount")).isEqualTo("1500.50");
        assertThat(result.canonical().get("issueDate")).isEqualTo("2026-01-15");
        assertThat(result.canonical().get("dueDate")).isEqualTo("2026-02-15");
        assertThat(result.canonical().get("status")).isEqualTo("PAID");
        assertThat(result.canonical().get("remainingAmount")).isEqualTo("0.00");
    }

    @Test
    void payableWithPartialPayment() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("complemento_historico", "Partial payment");
        raw.put("documento", "NF-456");
        raw.put("valor", "1000.00");
        raw.put("valorpago", "500.00");
        raw.put("datapagamento", "2026-03-01");
        raw.put("emissao", "2026-02-01");
        raw.put("vencimento", "2026-02-15");
        raw.put("codigo_tipo_conta", "3");
        raw.put("codigo_conta", "456");
        var mapper = new PayableLegacyMapper(DATE_VALIDATOR, EMPTY_RESOLVER, SNAPSHOT);
        var result = mapper.normalize(raw, "P-456", "C-EXT", "A-EXT", "3");
        assertThat(result.canonical().get("status")).isEqualTo("PARTIALLY_PAID");
        assertThat(result.canonical().get("remainingAmount")).isEqualTo("500.00");
    }

    @Test
    void payableWithOverpayment() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("complemento_historico", "Overpayment");
        raw.put("documento", "NF-789");
        raw.put("valor", "1000.00");
        raw.put("valorpago", "1200.00");
        raw.put("datapagamento", "2026-03-01");
        raw.put("emissao", "2026-02-01");
        raw.put("vencimento", "2026-02-15");
        raw.put("codigo_tipo_conta", "3");
        raw.put("codigo_conta", "789");
        var mapper = new PayableLegacyMapper(DATE_VALIDATOR, EMPTY_RESOLVER, SNAPSHOT);
        var result = mapper.normalize(raw, "P-789", "C-EXT", "A-EXT", "3");
        assertThat(result.canonical().get("status")).isEqualTo("PAID_EXCESS");
        assertThat(result.canonical().get("remainingAmount")).isEqualTo("0.00");
        assertThat(result.canonical().get("overpaid")).isEqualTo(true);
    }

    @Test
    void payableOverdueBasedOnSnapshot() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("complemento_historico", "Overdue bill");
        raw.put("documento", "NF-101");
        raw.put("valor", "500.00");
        raw.put("vencimento", "2026-05-01");
        raw.put("codigo_tipo_conta", "3");
        raw.put("codigo_conta", "101");
        var mapper = new PayableLegacyMapper(DATE_VALIDATOR, EMPTY_RESOLVER, SNAPSHOT);
        var result = mapper.normalize(raw, "P-101", null, null, "3");
        assertThat(result.canonical().get("status")).isEqualTo("OVERDUE");
    }

    @Test
    void payableOpenWhenFutureDate() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("complemento_historico", "Future bill");
        raw.put("documento", "NF-202");
        raw.put("valor", "500.00");
        raw.put("vencimento", "2026-08-01");
        raw.put("codigo_tipo_conta", "3");
        raw.put("codigo_conta", "202");
        var mapper = new PayableLegacyMapper(DATE_VALIDATOR, EMPTY_RESOLVER, SNAPSHOT);
        var result = mapper.normalize(raw, "P-202", null, null, "3");
        assertThat(result.canonical().get("status")).isEqualTo("OPEN");
    }

    @Test
    void payableWithInvalidDatesGetsWarnings() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("complemento_historico", "Bad dates");
        raw.put("documento", "NF-303");
        raw.put("valor", "500.00");
        raw.put("emissao", "1999-01-01");
        raw.put("vencimento", "2027-01-01");
        raw.put("codigo_tipo_conta", "3");
        raw.put("codigo_conta", "303");
        var mapper = new PayableLegacyMapper(DATE_VALIDATOR, EMPTY_RESOLVER, SNAPSHOT);
        var result = mapper.normalize(raw, "P-303", null, null, "3");
        assertThat(result.canonical().get("issueDate")).isNull();
        assertThat(result.canonical().get("dueDate")).isNull();
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.canonical().get("dateWarnings")).isNotNull();
    }

    @Test
    void payableWithPaymentBeforeIssueGetsOrderWarning() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("complemento_historico", "Early payment");
        raw.put("documento", "NF-404");
        raw.put("valor", "500.00");
        raw.put("emissao", "2026-03-15");
        raw.put("vencimento", "2026-03-20");
        raw.put("valorpago", "500.00");
        raw.put("datapagamento", "2026-03-10");
        raw.put("codigo_tipo_conta", "3");
        raw.put("codigo_conta", "404");
        var mapper = new PayableLegacyMapper(DATE_VALIDATOR, EMPTY_RESOLVER, SNAPSHOT);
        var result = mapper.normalize(raw, "P-404", null, null, "3");
        // Date is kept despite order inconsistency; warning is emitted
        assertThat(result.canonical().get("issueDate")).isEqualTo("2026-03-15");
        assertThat(result.warnings()).anyMatch(w -> w.code().name().equals("DATE_ORDER_INCONSISTENT"));
    }

    @Test
    void paymentNormalizes() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("datapagamento", "2026-02-10");
        raw.put("valorpago", "1500,50");
        raw.put("forma_pr", "B");
        var mapper = new PaymentLegacyMapper(DATE_VALIDATOR);
        var result = mapper.normalize(raw, "P-EXT", "PMT-EXT");
        assertThat(result.canonical().get("externalId")).isEqualTo("PMT-EXT");
        assertThat(result.canonical().get("payableExternalId")).isEqualTo("P-EXT");
        assertThat(result.canonical().get("paymentDate")).isEqualTo("2026-02-10");
        assertThat(result.canonical().get("amount")).isEqualTo("1500.50");
        assertThat(result.canonical().get("method")).isEqualTo("CHECK");
    }

    @Test
    void paymentMethodCashWhenC() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("valorpago", "100.00");
        raw.put("forma_pr", "C");
        var mapper = new PaymentLegacyMapper(DATE_VALIDATOR);
        var result = mapper.normalize(raw, "P-CASH", "PMT-CASH");
        assertThat(result.canonical().get("method")).isEqualTo("CASH");
    }

    @Test
    void paymentMethodOtherWhenUnknown() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("valorpago", "100.00");
        raw.put("forma_pr", "X");
        var mapper = new PaymentLegacyMapper(DATE_VALIDATOR);
        var result = mapper.normalize(raw, "P-OTHER", "PMT-OTHER");
        assertThat(result.canonical().get("method")).isEqualTo("OTHER");
    }

    @Test
    void paymentWithoutDateOmitsField() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("valorpago", "100.00");
        raw.put("forma_pr", "B");
        var mapper = new PaymentLegacyMapper(DATE_VALIDATOR);
        var result = mapper.normalize(raw, "P-NODATE", "PMT-NODATE");
        assertThat(result.canonical()).doesNotContainKey("paymentDate");
    }

    @Test
    void paymentZeroAmountMarked() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("valorpago", "0.00");
        raw.put("forma_pr", "B");
        var mapper = new PaymentLegacyMapper(DATE_VALIDATOR);
        var result = mapper.normalize(raw, "P-ZERO", "PMT-ZERO");
        assertThat(result.canonical().get("zeroAmount")).isEqualTo(true);
    }

    @Test
    void userNormalizesWithoutEmailFallback() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("login", "joao");
        raw.put("nome", "Joao Silva");
        raw.put("perfil", "ADMIN");
        var result = new UserLegacyMapper().normalize(raw);
        assertThat(result.get("username")).isEqualTo("joao");
        assertThat(result.get("displayName")).isEqualTo("Joao Silva");
        assertThat(result.get("roleCodes")).asList().contains("ADMIN");
        assertThat(result.get("email")).isEqualTo("imported+ed2befb1@invalid.local");
    }

    @Test
    void userNormalizesWithAlternativeEmail() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("login", "maria");
        raw.put("nome", "Maria Souza");
        raw.put("email_alternativo", "MARIA@EXAMPLE.COM");
        var result = new UserLegacyMapper().normalize(raw);
        assertThat(result.get("email")).isEqualTo("maria@example.com");
    }

    @Test
    void userExcludedWhenExcluidoS() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("login", "inactive");
        raw.put("nome", "Inactive User");
        raw.put("excluido", "S");
        var result = new UserLegacyMapper().normalize(raw);
        assertThat(result.get("active")).isEqualTo(false);
    }
}
