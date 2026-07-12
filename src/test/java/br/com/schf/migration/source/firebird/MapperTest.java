package br.com.schf.migration.source.firebird;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.schf.migration.source.firebird.mapping.CategoryLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.FinancialAccountLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.PayableLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.PaymentLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.SupplierLegacyMapper;
import br.com.schf.migration.source.firebird.mapping.UserLegacyMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapperTest {

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
    void payableNormalizesMoney() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("historico", "Pagamento de servico");
        raw.put("documento", "NF-123");
        raw.put("valor", "1500,50");
        raw.put("emissao", "2026-01-15");
        raw.put("vencimento", "2026-02-15");
        var result = new PayableLegacyMapper().normalize(raw, "S-EXT", "C-EXT", "A-EXT");
        assertThat(result.get("description")).isEqualTo("Pagamento de servico");
        assertThat(result.get("amount")).isEqualTo("1500.50");
        assertThat(result.get("issueDate")).isEqualTo("2026-01-15");
        assertThat(result.get("dueDate")).isEqualTo("2026-02-15");
        assertThat(result.get("supplierExternalId")).isEqualTo("S-EXT");
        assertThat(result.get("categoryExternalId")).isEqualTo("C-EXT");
    }

    @Test
    void paymentNormalizes() {
        var raw = new LinkedHashMap<String, Object>();
        raw.put("datapag", "2026-02-10");
        raw.put("valor", "1500,50");
        raw.put("forma", "BOLETO");
        var result = new PaymentLegacyMapper().normalize(raw, "P-EXT");
        assertThat(result.get("payableExternalId")).isEqualTo("P-EXT");
        assertThat(result.get("paymentDate")).isEqualTo("2026-02-10");
        assertThat(result.get("amount")).isEqualTo("1500.50");
        assertThat(result.get("method")).isEqualTo("TRANSFER");
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
        assertThat(result.get("email")).isNull();
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
}
