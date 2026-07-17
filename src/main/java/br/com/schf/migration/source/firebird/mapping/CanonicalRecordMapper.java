package br.com.schf.migration.source.firebird.mapping;

import br.com.schf.migration.source.firebird.mapping.CounterpartyResolver.CounterpartyInfo;
import br.com.schf.migration.source.firebird.mapping.CounterpartyResolver.ResolvedCounterparty;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CanonicalRecordMapper {
    private final SupplierLegacyMapper supplierMapper;
    private final UserLegacyMapper userMapper;
    private final CategoryLegacyMapper categoryMapper;
    private final FinancialAccountLegacyMapper financialAccountMapper;
    private final PayableLegacyMapper payableMapper;
    private final PaymentLegacyMapper paymentMapper;
    private final CounterpartyResolver counterpartyResolver;
    private final LocalDate snapshotDate;

    public CanonicalRecordMapper(LocalDate snapshotDate, CounterpartyResolver counterpartyResolver) {
        this.snapshotDate = snapshotDate;
        this.counterpartyResolver = counterpartyResolver;
        this.supplierMapper = new SupplierLegacyMapper();
        this.userMapper = new UserLegacyMapper();
        this.categoryMapper = new CategoryLegacyMapper();
        this.financialAccountMapper = new FinancialAccountLegacyMapper();
        var dateValidator = new DateValidator(snapshotDate);
        this.payableMapper = new PayableLegacyMapper(dateValidator, counterpartyResolver, snapshotDate);
        this.paymentMapper = new PaymentLegacyMapper(dateValidator);
    }

    public Map<String, Object> mapOrganization(Map<String, Object> raw, String externalId) {
        var result = new LinkedHashMap<String, Object>();
        result.put("externalId", externalId);
        result.put("code", raw.getOrDefault("code", raw.get("mon$database_name")));
        result.put("name", "SGH Legacy");
        return result;
    }

    public Map<String, Object> mapSupplier(Map<String, Object> raw, String externalId) {
        var mapped = supplierMapper.normalize(raw);
        mapped.put("externalId", externalId);
        return mapped;
    }

    public Map<String, Object> mapUser(Map<String, Object> raw, String externalId) {
        var mapped = userMapper.normalize(raw);
        mapped.put("externalId", externalId);
        return mapped;
    }

    public Map<String, Object> mapCategory(Map<String, Object> raw, String externalId) {
        var mapped = categoryMapper.normalize(raw);
        mapped.put("externalId", externalId);
        return mapped;
    }

    public Map<String, Object> mapFinancialAccount(Map<String, Object> raw, String externalId) {
        var mapped = financialAccountMapper.normalize(raw);
        mapped.put("externalId", externalId);
        return mapped;
    }

    public PayableLegacyMapper.PayableMappingResult mapPayable(Map<String, Object> raw, String externalId,
                                                                String categoryExternalId, String financialAccountExternalId) {
        var codigoTipoConta = raw.getOrDefault("codigo_tipo_conta", "0").toString();
        return payableMapper.normalize(raw, externalId, categoryExternalId, financialAccountExternalId, codigoTipoConta);
    }

    public PaymentLegacyMapper.PaymentMappingResult mapPayment(Map<String, Object> raw, String payableExternalId,
                                                               String paymentExternalId) {
        return paymentMapper.normalize(raw, payableExternalId, paymentExternalId);
    }

    public Map<String, Object> mapCounterparty(String externalId, ResolvedCounterparty resolved) {
        var result = new LinkedHashMap<String, Object>();
        result.put("externalId", externalId);
        result.put("name", resolved.name());
        result.put("type", resolved.type().name());
        result.put("sourceReference", resolved.sourceReference());
        return result;
    }

    public Map<String, Object> mapUnresolvedCounterparty(String externalId, ResolvedCounterparty resolved, String shortHash) {
        var result = new LinkedHashMap<String, Object>();
        result.put("externalId", externalId);
        result.put("name", "Unresolved legacy counterparty — " + shortHash);
        result.put("type", resolved.type().name());
        result.put("resolutionStatus", "UNRESOLVED_LEGACY_REFERENCE");
        result.put("sourceReference", resolved.sourceReference());
        result.put("active", false);
        return result;
    }

    public CounterpartyResolver counterpartyResolver() { return counterpartyResolver; }
    public LocalDate snapshotDate() { return snapshotDate; }
}
