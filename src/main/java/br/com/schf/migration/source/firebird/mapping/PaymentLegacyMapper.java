package br.com.schf.migration.source.firebird.mapping;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class PaymentLegacyMapper {

    public Map<String, Object> normalize(Map<String, Object> raw, String payableExternalId) {
        var result = new LinkedHashMap<String, Object>();
        result.put("payableExternalId", payableExternalId);
        result.put("paymentDate", date(raw, "datapag"));
        result.put("amount", normalizeMoney(raw, "valor"));
        result.put("method", mapMethod(raw));
        return result;
    }

    private String mapMethod(Map<String, Object> raw) {
        var method = string(raw, "forma");
        if (method == null) method = string(raw, "tipopag");
        if (method == null) return "OTHER";
        var upper = method.toUpperCase();
        if (upper.contains("CHEQUE") || upper.contains("CHECK")) return "CHECK";
        if (upper.contains("BOLETO") || upper.contains("TRANSFER") || upper.contains("TED") || upper.contains("DOC")) return "TRANSFER";
        if (upper.contains("CARTAO") || upper.contains("CARD") || upper.contains("CREDIT")) return "CREDIT_CARD";
        return "OTHER";
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
}
