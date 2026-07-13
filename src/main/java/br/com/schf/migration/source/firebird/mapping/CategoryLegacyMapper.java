package br.com.schf.migration.source.firebird.mapping;

import java.util.LinkedHashMap;
import java.util.Map;

public class CategoryLegacyMapper {

    public Map<String, Object> normalize(Map<String, Object> raw) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", string(raw, "descricao"));
        result.put("type", mapType(raw));
        result.put("active", active(raw));
        return result;
    }

    private String mapType(Map<String, Object> raw) {
        var type = string(raw, "tipo");
        if (type == null) type = string(raw, "natureza");
        if (type == null) return "EXPENSE";
        var upper = type.toUpperCase();
        if (upper.contains("RECEITA") || upper.contains("RECEIPT") || upper.contains("INCOME")) return "INCOME";
        return "EXPENSE";
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
