package br.com.schf.migration.source.firebird.mapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserLegacyMapper {

    public Map<String, Object> normalize(Map<String, Object> raw) {
        var result = new LinkedHashMap<String, Object>();
        result.put("username", string(raw, "login"));
        result.put("email", normalizeEmail(raw));
        result.put("displayName", string(raw, "nome"));
        result.put("active", true);
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

    private String string(Map<String, Object> raw, String key) {
        var val = raw.get(key);
        return val == null ? null : val.toString().strip();
    }
}
