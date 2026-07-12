package br.com.schf.migration.source.firebird.mapping;

import java.util.LinkedHashMap;
import java.util.Map;

public class FinancialAccountLegacyMapper {

    public Map<String, Object> normalize(Map<String, Object> raw) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", string(raw, "descricao"));
        result.put("type", mapType(raw));
        result.put("bankName", string(raw, "banco"));
        result.put("agency", string(raw, "agencia"));
        result.put("accountNumber", string(raw, "conta"));
        result.put("active", true);
        return result;
    }

    private String mapType(Map<String, Object> raw) {
        var type = string(raw, "tipo");
        if (type == null) return "BANK";
        var upper = type.toUpperCase();
        if (upper.contains("CAIXA") || upper.contains("CASH")) return "CASH";
        if (upper.contains("POUPANCA") || upper.contains("SAVINGS")) return "SAVINGS";
        return "BANK";
    }

    private String string(Map<String, Object> raw, String key) {
        var val = raw.get(key);
        return val == null ? null : val.toString().strip();
    }
}
