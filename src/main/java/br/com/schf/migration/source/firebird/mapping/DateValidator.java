package br.com.schf.migration.source.firebird.mapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class DateValidator {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2026;

    private final LocalDate snapshotDate;

    public DateValidator(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public SingleDateValidationResult validateIssueDate(String raw) {
        var warnings = new ArrayList<DateWarning>();
        var parsed = parseDate(raw, warnings);
        if (parsed != null && (parsed.getYear() < MIN_YEAR || parsed.getYear() > MAX_YEAR)) {
            warnings.add(new DateWarning(DateWarningCode.INVALID_ISSUE_DATE, "issueDate", raw));
            parsed = null;
        }
        return new SingleDateValidationResult(parsed, List.copyOf(warnings));
    }

    public SingleDateValidationResult validateDueDate(String raw) {
        var warnings = new ArrayList<DateWarning>();
        var parsed = parseDate(raw, warnings);
        if (parsed != null && (parsed.getYear() < MIN_YEAR || parsed.getYear() > MAX_YEAR)) {
            warnings.add(new DateWarning(DateWarningCode.INVALID_DUE_DATE, "dueDate", raw));
            parsed = null;
        }
        return new SingleDateValidationResult(parsed, List.copyOf(warnings));
    }

    public SingleDateValidationResult validatePaymentDate(String raw) {
        var warnings = new ArrayList<DateWarning>();
        var parsed = parseDate(raw, warnings);
        if (parsed != null && (parsed.getYear() < MIN_YEAR || parsed.getYear() > MAX_YEAR)) {
            warnings.add(new DateWarning(DateWarningCode.INVALID_PAYMENT_DATE, "paymentDate", raw));
            parsed = null;
        }
        return new SingleDateValidationResult(parsed, List.copyOf(warnings));
    }

    public AllDatesValidationResult validateAllDates(String issueRaw, String dueRaw, String paymentRaw) {
        var warnings = new ArrayList<DateWarning>();
        var issue = parseDate(issueRaw, warnings);
        var due = parseDate(dueRaw, warnings);
        var payment = parseDate(paymentRaw, warnings);

        if (issue != null && (issue.getYear() < MIN_YEAR || issue.getYear() > MAX_YEAR)) {
            warnings.add(new DateWarning(DateWarningCode.INVALID_ISSUE_DATE, "issueDate", issueRaw));
            issue = null;
        }
        if (due != null && (due.getYear() < MIN_YEAR || due.getYear() > MAX_YEAR)) {
            warnings.add(new DateWarning(DateWarningCode.INVALID_DUE_DATE, "dueDate", dueRaw));
            due = null;
        }
        if (payment != null && (payment.getYear() < MIN_YEAR || payment.getYear() > MAX_YEAR)) {
            warnings.add(new DateWarning(DateWarningCode.INVALID_PAYMENT_DATE, "paymentDate", paymentRaw));
            payment = null;
        }

        if (payment != null && issue != null && payment.isBefore(issue)) {
            warnings.add(new DateWarning(DateWarningCode.DATE_ORDER_INCONSISTENT, "paymentDate", paymentRaw));
        }
        if (payment != null && due != null && payment.isBefore(due)) {
            warnings.add(new DateWarning(DateWarningCode.DATE_ORDER_INCONSISTENT, "paymentDate", paymentRaw));
        }

        return new AllDatesValidationResult(issue, due, payment, List.copyOf(warnings));
    }

    private LocalDate parseDate(String raw, List<DateWarning> warnings) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.substring(0, 10), ISO);
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    public record SingleDateValidationResult(LocalDate date, List<DateWarning> warnings) {
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }

    public record AllDatesValidationResult(LocalDate issueDate, LocalDate dueDate, LocalDate paymentDate, List<DateWarning> warnings) {
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
}
