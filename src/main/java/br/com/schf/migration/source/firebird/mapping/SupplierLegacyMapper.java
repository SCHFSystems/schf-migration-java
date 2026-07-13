package br.com.schf.migration.source.firebird.mapping;

import java.util.LinkedHashMap;
import java.util.Map;

public class SupplierLegacyMapper {

    public Map<String, Object> normalize(Map<String, Object> raw) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", name(raw));
        result.put("document", normalizeDocument(raw));
        result.put("email", string(raw, "email"));
        result.put("phone", string(raw, "telefone", "fone"));
        result.put("active", booleanValue(raw, "ativo", "desativado", true));
        return result;
    }

    private String name(Map<String, Object> raw) {
        var name = string(raw, "nomefor");
        if (name != null) return name;
        name = string(raw, "nomefantasia");
        if (name != null) return name;
        return string(raw, "nome");
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

    private String normalizeDocument(Map<String, Object> raw) {
        var doc = string(raw, "cgc", "cnpj_cpf");
        if (doc == null) doc = string(raw, "cpf", "cpf_cnpj");
        if (doc == null) doc = string(raw, "documento", "doc");
        if (doc == null) return null;
        return doc.replaceAll("\\D", "");
    }

    private boolean booleanValue(Map<String, Object> raw, String activeKey, String inactiveKey, boolean def) {
        var activeVal = string(raw, activeKey);
        if (activeVal != null) {
            var s = activeVal.toLowerCase();
            if ("s".equals(s) || "sim".equals(s) || "true".equals(s) || "1".equals(s)) return true;
            if ("n".equals(s) || "nao".equals(s) || "false".equals(s) || "0".equals(s)) return false;
        }
        var inactiveVal = string(raw, inactiveKey);
        if (inactiveVal != null) {
            var s = inactiveVal.toLowerCase();
            if ("s".equals(s) || "sim".equals(s) || "true".equals(s) || "1".equals(s)) return false;
            if ("n".equals(s) || "nao".equals(s) || "false".equals(s) || "0".equals(s)) return true;
        }
        return def;
    }
}
