package br.com.schf.migration.source.firebird.mapping;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class PayableLegacyMapper {

    public Map<String, Object> normalize(Map<String, Object> raw, String supplierExternalId,
                                          String categoryExternalId, String accountExternalId) {
        var result = new LinkedHashMap<String, Object>();
        result.put("description", safeSubstring(string(raw, "historico"), 200));
        result.put("documentNumber", string(raw, "documento"));
        result.put("supplierExternalId", supplierExternalId);
        result.put("categoryExternalId", categoryExternalId);
        result.put("financialAccountExternalId", accountExternalId);
        result.put("issueDate", date(raw, "emissao"));
        result.put("dueDate", date(raw, "vencimento"));
        result.put("amount", normalizeMoney(raw, "valor"));
        result.put("status", mapStatus(raw));
        return result;
    }

    private String mapStatus(Map<String, Object> raw) {
        var status = string(raw, "status");
        if (status == null) status = string(raw, "situacao");
        if (status == null) return "OPEN";
        var upper = status.toUpperCase();
        if (upper.contains("PAGO") || upper.contains("PAID")) return "PAID";
        if (upper.contains("CANCEL") || upper.contains("CANC")) return "CANCELLED";
        if (upper.contains("VENC") || upper.contains("OVERDUE")) return "OVERDUE";
        return "OPEN";
    }

    private String normalizeMoney(Map<String, Object> raw, String key) {
        var val = raw.get(key);
        if (val == null) return "0.00";
        var str = val.toString().replace(",", ".").replaceAll("[^0-9.-]", "");
        try {
            var d = Double.parseDouble(str);
            return String.format(Locale.US, "%.2f", Math.abs(d));
        } catch (NumberFormatException e) {
            return "0.00";
        }
    }

    private String string(Map<String, Object> raw, String key) {
        var val = raw.get(key);
        return val == null ? null : val.toString().strip();
    }

    private String date(Map<String, Object> raw, String key) {
        var val = raw.get(key);
        if (val == null) return null;
        var str = val.toString().strip();
        if (str.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(str.substring(0, 10)).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return str.length() >= 10 ? str.substring(0, 10) : null;
        }
    }

    private String safeSubstring(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
