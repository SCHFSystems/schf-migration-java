package br.com.schf.migration.source.firebird.mapping;

import java.util.LinkedHashMap;
import java.util.Map;

public class SupplierLegacyMapper {

    public Map<String, Object> normalize(Map<String, Object> raw) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", string(raw, "nomefor") != null ? string(raw, "nomefor") : string(raw, "nome"));
        result.put("document", normalizeDocument(raw));
        result.put("email", string(raw, "email"));
        result.put("phone", string(raw, "telefone"));
        result.put("active", booleanValue(raw, "ativo", true));
        return result;
    }

    private String string(Map<String, Object> raw, String key) {
        var val = raw.get(key);
        return val == null ? null : val.toString().strip();
    }

    private String normalizeDocument(Map<String, Object> raw) {
        var doc = string(raw, "cgc");
        if (doc == null) doc = string(raw, "cpf");
        if (doc == null) doc = string(raw, "documento");
        if (doc == null) return null;
        return doc.replaceAll("\\D", "");
    }

    private boolean booleanValue(Map<String, Object> raw, String key, boolean def) {
        var val = raw.get(key);
        if (val == null) return def;
        var s = val.toString().strip().toLowerCase();
        if ("s".equals(s) || "sim".equals(s) || "true".equals(s) || "1".equals(s)) return true;
        if ("n".equals(s) || "nao".equals(s) || "false".equals(s) || "0".equals(s)) return false;
        return def;
    }
}
