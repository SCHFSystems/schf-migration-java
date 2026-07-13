# SGH Payable Status Mapping

## Legacy Sources

### Previous Migration (PHP/SDK)
Status derived from:
- DATA_PAGAMENTO (date): if present → 'paid'
- PREVISTO_REALIZADO = 'R': if present → 'paid'
- DATA_VENCIMENTO < today: if no payment → 'overdue'
- Otherwise: 'pending'

### Current SGH Database Reality
PREVISTO_REALIZADO: ALL NULL (not used in this database)

## Status Mapping for Java Adapter (Bundle Format 1.1)

Status computed using snapshot date **2026-07-01** (fixed, not LocalDate.now()):

```java
var hasPayment = valorPago != null && valorPago.compareTo(BigDecimal.ZERO) > 0;
if (hasPayment && valorPago.compareTo(valor) > 0) {
    status = PayableStatus.PAID_EXCESS;
} else if (hasPayment && valorPago.compareTo(valor) < 0) {
    status = PayableStatus.PARTIALLY_PAID;
} else if (hasPayment) {
    status = PayableStatus.PAID;
} else if (dueDate != null && dueDate.isBefore(snapshotDate)) {
    status = PayableStatus.OVERDUE;
} else {
    status = PayableStatus.OPEN;
}
```

## Distribution (Filter D = 39,161)
| Status | Count | % |
|--------|-------|---|
| PAID | 38,088 | 97.26% |
| PARTIALLY_PAID | 373 | 0.95% |
| PAID_EXCESS | 1,079 | 2.75% |
| OVERDUE | 700 | 1.79% |
| OPEN | 1 | 0.003% |

## Partial / Overpaid Treatment

| Scenario | Count | Status | remainingAmount | overpaid |
|----------|-------|--------|-----------------|----------|
| VALORPAGO >= VALOR (fully paid) | 38,088 | PAID | 0.00 | false |
| 0 < VALORPAGO < VALOR (partial) | 373 | PARTIALLY_PAID | VALOR - VALORPAGO | false |
| VALORPAGO > VALOR (overpaid) | 1,079 | PAID_EXCESS | 0.00 | true |

## Date Anomalies

| Anomaly | Count | Treatment |
|---------|-------|-----------|
| Payment date before issue date | 405 | Keep dates, emit DATE_ORDER_INCONSISTENT warning |
| Payment date but zero payment | 6 | Keep date, mark zeroAmount=true |
| Bogus issue year (< 2000 or > 2026) | ~56 | Output null issueDate, emit INVALID_ISSUE_DATE warning |
| Bogus due year (< 2000 or > 2026) | ~10 | Output null dueDate, emit INVALID_DUE_DATE warning |

## Cardinality
ONE_ACCUMULATED_PAYMENT_PER_PAYABLE (CONFIRMED)
- 38,460 payables with VALORPAGO > 0
- Each produces exactly 1 Payment record
- Payment.externalId = Payable.externalId + "|PAYMENT"
- Payment.amount = VALORPAGO
- Payment.method = FORMA_PR mapped to PaymentMethod enum
- Payment.date = DATA_PAGAMENTO (omitted if null)

## Counterparty Resolution

| CODIGO_TIPO_CONTA | Canonical Type | Source | Confidence |
|-------------------|---------------|--------|------------|
| 3 (FORNECEDOR) | SUPPLIER | FORNECEDOR table (FK) | CONFIRMED |
| 2 (MEDICOS) | INTERNAL | CONTA table (type 2) | CONFIRMED |
| 7 (FINANCEIRO) | GOVERNMENT/OTHER | CONTAS table (type 7) | CONFIRMED |
| 15 (COLABORADOR) | EMPLOYEE | COLABORADOR table (FK) | CONFIRMED |

All types resolved. No STOP condition triggered.
