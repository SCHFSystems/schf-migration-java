package br.com.schf.migration.source.firebird.mapping;

import java.util.LinkedHashMap;
import java.util.Map;

public class CounterpartyResolver {
    private final Map<String, CounterpartyInfo> supplierByCodigo;
    private final Map<String, CounterpartyInfo> contasByTipoAndCodigo;
    private final Map<String, CounterpartyInfo> colaboradorByTipoAndCodigo;

    public CounterpartyResolver(
            Map<String, CounterpartyInfo> supplierByCodigo,
            Map<String, CounterpartyInfo> contasByTipoAndCodigo,
            Map<String, CounterpartyInfo> colaboradorByTipoAndCodigo) {
        this.supplierByCodigo = Map.copyOf(supplierByCodigo);
        this.contasByTipoAndCodigo = Map.copyOf(contasByTipoAndCodigo);
        this.colaboradorByTipoAndCodigo = Map.copyOf(colaboradorByTipoAndCodigo);
    }

    public ResolvedCounterparty resolve(int codigoTipoConta, String codigoContaStr) {
        var codigoConta = codigoContaStr != null ? codigoContaStr.strip() : "";
        var tipoContaKey = String.valueOf(codigoTipoConta);

        return switch (codigoTipoConta) {
            case 3 -> resolveSupplier(codigoConta);
            case 2 -> resolveInternal(tipoContaKey, codigoConta);
            case 7 -> resolveGovernment(tipoContaKey, codigoConta);
            case 15 -> resolveEmployee(tipoContaKey, codigoConta);
            default -> new ResolvedCounterparty(null, CounterpartyType.OTHER, null, null);
        };
    }

    private ResolvedCounterparty resolveSupplier(String codigoConta) {
        var info = supplierByCodigo.get(codigoConta);
        if (info == null) return new ResolvedCounterparty(null, CounterpartyType.SUPPLIER, codigoConta, null);
        return new ResolvedCounterparty(info.externalId(), CounterpartyType.SUPPLIER, codigoConta, info.name());
    }

    private ResolvedCounterparty resolveInternal(String tipoContaKey, String codigoConta) {
        var key = tipoContaKey + "|" + codigoConta;
        var info = contasByTipoAndCodigo.get(key);
        if (info == null) return new ResolvedCounterparty(null, CounterpartyType.INTERNAL, key, null);
        return new ResolvedCounterparty(info.externalId(), CounterpartyType.INTERNAL, key, info.name());
    }

    private ResolvedCounterparty resolveGovernment(String tipoContaKey, String codigoConta) {
        var key = tipoContaKey + "|" + codigoConta;
        var info = contasByTipoAndCodigo.get(key);
        if (info == null) return new ResolvedCounterparty(null, CounterpartyType.GOVERNMENT, key, null);
        return new ResolvedCounterparty(info.externalId(), CounterpartyType.GOVERNMENT, key, info.name());
    }

    private ResolvedCounterparty resolveEmployee(String tipoContaKey, String codigoConta) {
        var key = tipoContaKey + "|" + codigoConta;
        var info = colaboradorByTipoAndCodigo.get(key);
        if (info == null) return new ResolvedCounterparty(null, CounterpartyType.EMPLOYEE, key, null);
        return new ResolvedCounterparty(info.externalId(), CounterpartyType.EMPLOYEE, key, info.name());
    }

    public record CounterpartyInfo(String externalId, String name) {}
    public record ResolvedCounterparty(String externalId, CounterpartyType type, String sourceReference, String name) {}
}
