package br.com.schf.migration.source.firebird.mapping;

import br.com.schf.migration.source.firebird.mapping.DateValidator.SingleDateValidationResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PaymentLegacyMapper {
    private final DateValidator dateValidator;

    public PaymentLegacyMapper(DateValidator dateValidator) {
        this.dateValidator = dateValidator;
    }

    public PaymentMappingResult normalize(Map<String, Object> raw, String payableExternalId) {
        var warnings = new ArrayList<DateWarning>();

        var paymentRaw = string(raw, "datapagamento", "datapag");
        var amount = normalizeMoney(raw, "valorpago");
        var method = mapMethod(raw);

        var dateResult = dateValidator.validatePaymentDate(paymentRaw);
        warnings.addAll(dateResult.warnings());

        var result = new LinkedHashMap<String, Object>();
        result.put("payableExternalId", payableExternalId);
        result.put("amount", amount);
        result.put("method", method.name());

        if (dateResult.date() != null) {
            result.put("paymentDate", dateResult.date().toString());
        }

        if (amount == null || "0.00".equals(amount)) {
            result.put("zeroAmount", true);
        }

        if (!warnings.isEmpty()) {
            result.put("dateWarnings", warnings.stream().map(w ->
                Map.of("code", w.code().name(), "field", w.field(), "rawValue", w.rawValue())
            ).toList());
        }

        return new PaymentMappingResult(result, List.copyOf(warnings));
    }

    private PaymentMethod mapMethod(Map<String, Object> raw) {
        var method = string(raw, "forma_pr", "forma");
        if (method == null) return PaymentMethod.OTHER;
        var upper = method.toUpperCase();
        if ("B".equals(upper) || "CHEQUE".equals(upper) || "CHECK".equals(upper)) return PaymentMethod.CHECK;
        if ("C".equals(upper) || "DINHEIRO".equals(upper) || "CASH".equals(upper)) return PaymentMethod.CASH;
        if (upper.contains("BOLETO") || upper.contains("TRANSFER") || upper.contains("TED") || upper.contains("DOC")) return PaymentMethod.TRANSFER;
        if (upper.contains("CARTAO") || upper.contains("CARD") || upper.contains("CREDIT")) return PaymentMethod.CREDIT_CARD;
        return PaymentMethod.OTHER;
    }

    private String normalizeMoney(Map<String, Object> raw, String... keys) {
        for (var key : keys) {
            var val = raw.get(key);
            if (val == null) continue;
            var str = val.toString().replace(",", ".").replaceAll("[^0-9.-]", "");
            try {
                var d = Double.parseDouble(str);
                return String.format(java.util.Locale.US, "%.2f", Math.abs(d));
            } catch (NumberFormatException e) {
                return "0.00";
            }
        }
        return "0.00";
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

    public record PaymentMappingResult(Map<String, Object> canonical, List<DateWarning> warnings) {}
}
