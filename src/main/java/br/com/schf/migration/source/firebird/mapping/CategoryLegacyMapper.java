package br.com.schf.migration.source.firebird.mapping;

import java.util.LinkedHashMap;
import java.util.Map;

public class CategoryLegacyMapper {

    public Map<String, Object> normalize(Map<String, Object> raw) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", string(raw, "descricao"));
        result.put("type", mapType(raw));
        result.put("active", true);
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

    private String string(Map<String, Object> raw, String key) {
        var val = raw.get(key);
        return val == null ? null : val.toString().strip();
    }
}
