package br.com.schf.migration.source.firebird.mapping;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CounterpartyResolver {
    private final String sourceInstanceId;
    private final Map<String, CounterpartyInfo> allByTipoAndCodigo;
    private final Set<String> unresolvedKeys = new LinkedHashSet<>();

    public CounterpartyResolver(String sourceInstanceId,
            Map<String, CounterpartyInfo> allByTipoAndCodigo) {
        this.sourceInstanceId = sourceInstanceId.replace("-", "").toLowerCase();
        this.allByTipoAndCodigo = Map.copyOf(allByTipoAndCodigo);
    }

    public ResolvedCounterparty resolve(int codigoTipoConta, String codigoContaStr) {
        var codigoConta = codigoContaStr != null ? codigoContaStr.strip() : "";
        var tipoContaKey = String.valueOf(codigoTipoConta);
        var key = tipoContaKey + "|" + codigoConta;
        var info = allByTipoAndCodigo.get(key);

        var type = switch (codigoTipoConta) {
            case 3 -> CounterpartyType.SUPPLIER;
            case 2, 4, 5, 9, 10, 11, 12, 13, 14 -> CounterpartyType.INTERNAL;
            case 7 -> CounterpartyType.GOVERNMENT;
            case 1, 15 -> CounterpartyType.EMPLOYEE;
            default -> CounterpartyType.OTHER;
        };

        if (info == null) {
            unresolvedKeys.add(key);
            var uuidKey = sourceInstanceId + "|UNRESOLVED_COUNTERPARTY|" + key;
            var externalId = UUID.nameUUIDFromBytes(uuidKey.getBytes()).toString();
            return new ResolvedCounterparty(externalId, type, key, null);
        }
        return new ResolvedCounterparty(info.externalId(), type, key, info.name());
    }

    public Set<String> getUnresolvedKeys() {
        return Set.copyOf(unresolvedKeys);
    }

    public record CounterpartyInfo(String externalId, String name) {}
    public record ResolvedCounterparty(String externalId, CounterpartyType type, String sourceReference, String name) {}
}
