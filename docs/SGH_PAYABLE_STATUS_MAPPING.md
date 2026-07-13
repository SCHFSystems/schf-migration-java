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

## Proposed Status Mapping for Java Adapter

```java
if (valorPago > 0 && dataPagamento != null) {
    status = "paid";
} else if (valorPago > 0) {
    status = "paid_no_date";  // edge case: 0 records
} else if (dataVencimento != null && isSaneYear(dataVencimento) 
           && dataVencimento < today) {
    status = "overdue";
} else {
    status = "open";
}
```

## Distribution (Filter D = 39,161)
| Status | Count | % |
|--------|-------|---|
| paid | 38,460 | 98.21% |
| overdue | 700 | 1.79% |
| open | 1 | 0.003% |

## Partial / Overpaid Subset
| Scenario | Count | Status | Treatment |
|----------|-------|--------|-----------|
| VALORPAGO >= VALOR (fully paid) | 38,088 | paid | Normal |
| 0 < VALORPAGO < VALOR (partial) | 373 | paid_short | Same payment, reduced amount |
| VALORPAGO > VALOR (overpaid) | 1,079 | paid_excess | Same payment, excess amount |

All 38,460 paid records map to a single accumulated Payment per Payable.
