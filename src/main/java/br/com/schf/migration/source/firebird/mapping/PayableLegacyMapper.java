package br.com.schf.migration.source.firebird.mapping;

import br.com.schf.migration.source.firebird.mapping.CounterpartyResolver.ResolvedCounterparty;
import br.com.schf.migration.source.firebird.mapping.DateValidator.AllDatesValidationResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PayableLegacyMapper {
    private static final int MAX_DESCRIPTION_LENGTH = 200;
    private static final int ZERO_YEAR = 0;

    private final DateValidator dateValidator;
    private final CounterpartyResolver counterpartyResolver;
    private final LocalDate snapshotDate;

    public PayableLegacyMapper(DateValidator dateValidator, CounterpartyResolver counterpartyResolver, LocalDate snapshotDate) {
        this.dateValidator = dateValidator;
        this.counterpartyResolver = counterpartyResolver;
        this.snapshotDate = snapshotDate;
    }

    public PayableMappingResult normalize(Map<String, Object> raw, String externalId,
                                           String categoryExternalId, String financialAccountExternalId,
                                           String codigoTipoContaStr) {
        var warnings = new ArrayList<DateWarning>();

        var description = safeSubstring(string(raw, "complemento_historico", "historico"), MAX_DESCRIPTION_LENGTH);
        var documentNumber = string(raw, "documento");
        var valorStr = normalizeMoney(raw, "valor");
        var valorPagoStr = normalizeMoney(raw, "valorpago");
        var valor = parseBigDecimal(valorStr);
        var valorPago = parseBigDecimal(valorPagoStr);

        var issueRaw = string(raw, "emissao");
        var dueRaw = string(raw, "vencimento");
        var paymentRaw = string(raw, "datapagamento");

        var dateResult = dateValidator.validateAllDates(issueRaw, dueRaw, paymentRaw);
        warnings.addAll(dateResult.warnings());

        var status = computeStatus(valor, valorPago, dateResult.dueDate(), dateResult.paymentDate());
        var remainingAmount = computeRemaining(valor, valorPago);
        var overpaid = valorPago != null && valor.compareTo(BigDecimal.ZERO) > 0 && valorPago.compareTo(valor) > 0;

        var tipoInt = parseInt(codigoTipoContaStr, 0);
        var counterparty = counterpartyResolver.resolve(tipoInt, string(raw, "codigo_conta"));

        var result = new LinkedHashMap<String, Object>();
        result.put("externalId", externalId);
        result.put("description", description);
        result.put("documentNumber", documentNumber);
        result.put("amount", valorStr);
        result.put("remainingAmount", remainingAmount);
        result.put("issueDate", fmtDate(dateResult.issueDate()));
        result.put("dueDate", fmtDate(dateResult.dueDate()));
        result.put("status", status.name());
        result.put("counterpartyExternalId", counterparty.externalId());
        result.put("counterpartyType", counterparty.type().name());
        result.put("sourceSnapshotDate", snapshotDate.toString());

        if (categoryExternalId != null) result.put("categoryExternalId", categoryExternalId);
        if (financialAccountExternalId != null) result.put("financialAccountExternalId", financialAccountExternalId);

        if (overpaid) {
            result.put("overpaid", true);
        }

        if (!warnings.isEmpty()) {
            result.put("dateWarnings", warnings.stream().map(w -> {
                var m = new LinkedHashMap<String, String>();
                m.put("code", w.code().name());
                m.put("field", w.field());
                m.put("rawValue", w.rawValue());
                return m;
            }).toList());
        }

        return new PayableMappingResult(result, List.copyOf(warnings));
    }

    private String computeRemaining(BigDecimal valor, BigDecimal valorPago) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) return "0.00";
        if (valorPago == null || valorPago.compareTo(BigDecimal.ZERO) <= 0) return valor.setScale(2, RoundingMode.HALF_UP).toString();
        if (valorPago.compareTo(valor) >= 0) return "0.00";
        return valor.subtract(valorPago).setScale(2, RoundingMode.HALF_UP).toString();
    }

    private PayableStatus computeStatus(BigDecimal valor, BigDecimal valorPago, LocalDate dueDate, LocalDate paymentDate) {
        var hasPayment = valorPago != null && valorPago.compareTo(BigDecimal.ZERO) > 0;
        if (hasPayment && paymentDate == null) {
            return PayableStatus.PAID;
        }
        if (hasPayment && valorPago.compareTo(valor) > 0) {
            return PayableStatus.PAID_EXCESS;
        }
        if (hasPayment && valorPago.compareTo(valor) < 0) {
            return PayableStatus.PARTIALLY_PAID;
        }
        if (hasPayment) {
            return PayableStatus.PAID;
        }
        if (dueDate != null && dueDate.isBefore(snapshotDate)) {
            return PayableStatus.OVERDUE;
        }
        return PayableStatus.OPEN;
    }

    private String fmtDate(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    private BigDecimal parseBigDecimal(String s) {
        if (s == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.strip()); } catch (NumberFormatException e) { return def; }
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

    private String safeSubstring(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record PayableMappingResult(Map<String, Object> canonical, List<DateWarning> warnings) {}
}
