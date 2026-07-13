package br.com.schf.migration.source.firebird.mapping;

import java.util.LinkedHashMap;
import java.util.Map;

public class FinancialAccountLegacyMapper {

    public Map<String, Object> normalize(Map<String, Object> raw) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", string(raw, "descricao", "nome"));
        result.put("type", mapType(raw));
        result.put("bankName", string(raw, "banco"));
        result.put("agency", string(raw, "agencia"));
        result.put("accountNumber", string(raw, "conta"));
        result.put("active", active(raw));
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

    private boolean active(Map<String, Object> raw) {
        var excluir = string(raw, "excluir");
        if (excluir == null) return true;
        var s = excluir.toLowerCase();
        if ("s".equals(s) || "sim".equals(s) || "true".equals(s)) return false;
        if ("n".equals(s) || "nao".equals(s) || "false".equals(s)) return true;
        return true;
    }

    private String string(Map<String, Object> raw, String... keys) {
        for (var key : keys) {
            var val = raw.get(key);
            if (val == null) continue;
            var str = val.toString().strip();
            if (!str.isBlank()) return str;
        }
        return null;
    }
}
