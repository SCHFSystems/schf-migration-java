package br.com.schf.migration.source.firebird.mapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserLegacyMapper {

    public Map<String, Object> normalize(Map<String, Object> raw) {
        var result = new LinkedHashMap<String, Object>();
        result.put("username", string(raw, "login", "codigo_usuario"));
        result.put("email", normalizeEmail(raw));
        result.put("displayName", string(raw, "nome"));
        result.put("active", active(raw));
        result.put("roleCodes", List.of(mapRole(raw)));
        return result;
    }

    private String mapRole(Map<String, Object> raw) {
        var role = string(raw, "perfil");
        if (role == null) role = string(raw, "cargo");
        if (role == null) return "VIEWER";
        var upper = role.toUpperCase();
        if (upper.contains("ADMIN") || upper.contains("MASTER")) return "ADMIN";
        if (upper.contains("FINANCE") || upper.contains("FINANC")) return "FINANCE";
        return "VIEWER";
    }

    private String normalizeEmail(Map<String, Object> raw) {
        var email = string(raw, "email");
        if (email == null) email = string(raw, "email_alternativo");
        if (email == null) return null;
        return email.strip().toLowerCase();
    }

    private boolean active(Map<String, Object> raw) {
        var excluido = string(raw, "excluido");
        if (excluido == null) return true;
        var s = excluido.toLowerCase();
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
