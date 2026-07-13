# SGH Payment Cardinality

## Resolution: ONE_ACCUMULATED_PAYMENT_PER_PAYABLE

## Evidence

### 1. No Standalone Payment Table
Confirmed in GATE B1.3. No `PAGAMENTO` table exists. Payment data is embedded in CONTAS_RECEBER_PAGAR columns:
- VALORPAGO (accumulated paid amount)
- DATA_PAGAMENTO (payment date)
- FORMA_PR (payment method: B=boleto/check, C=cash, NULL=unknown)

### 2. COD_FRACIONADO Analysis
| Metric | Value |
|--------|-------|
| Records with COD_FRACIONADO | 20 (of 39,722) |
| All with payment | 20 (100%) |
| Without payment | 0 |
| Fractional mechanism | Negligible (0.05% of records) |

COD_FRACIONADO is an edge-case partial payment flag affecting only 20 records. Not a general cardinality driver.

### 3. OPERACAO_BANCO Analysis
| Metric | Value |
|--------|-------|
| Payables with 0 operations | 2,424 |
| Payables with exactly 1 operation | 37,298 |
| Payables with >1 operation | 0 |
| Max operations per payable | 1 |
| Operations linked to paid payables | 37,298 |
| Operations linked to excluded payables | Not determined |

OPERACAO_BANCO is a 1:0..1 confirmation of payment, not a payment source.

### 4. VALORPAGO Behavior
- Single NUMERIC(14,2) column on each payable row
- Represents total accumulated payment
- No partial payment installments table exists
- VALORPAGO = VALOR for most records (38,088 of 39,161)
- 373 partials, 1,079 overpaid — all single payments

## Model
```
1 Payable → 0 or 1 Payment (accumulated on VALORPAGO)
Payment.externalId = Payable.externalId + "|PAYMENT"
Payment.amount = VALORPAGO
Payment.method = FORMA_PR → map to canonical method
Payment.date = DATA_PAGAMENTO
```

## Future Consideration
If COD_FRACIONADO-linked records need to be modeled as multiple payments:
- These 20 records have the same DOC_RCB_PGT shared across rows
- A GROUP BY on DOC_RCB_PGT could reconstruct split payments
- This is deferred to GATE B2+ as an edge case refinement
