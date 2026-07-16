# SGH Count and Filter Rules

## Source
CONTAS_RECEBER_PAGAR (SGH Firebird 2.5)

## Type Filter
RCB_PGT = 'P' (payables only, excludes receivables)

## Exclusion Filter
(EXCLUIR IS NULL OR EXCLUIR <> 'S')

## Amount Filter
VALOR > 0 (excludes zero/negative amounts)

## Account Type (progressive)

### Stage 1: Supplier-linked only
CODIGO_TIPO_CONTA = 3 → SUPPLIER (via FORNECEDOR table)
Expected count: ~15,398

### Stage 2: All types
No CODIGO_TIPO_CONTA restriction
Expected count: ~39,161
Requires counterparty resolution for all types:

| Type | Name | Canonical Type | Resolution |
|------|------|---------------|------------|
| 1 | COLABORADOR | EMPLOYEE | COLABORADOR table |
| 2 | MEDICOS | INTERNAL | CONTAS table (type 2) |
| 3 | FORNECEDOR | SUPPLIER | FORNECEDOR table |
| 4 | (outros) | INTERNAL | CONTAS table (type 4) |
| 5 | (outros) | INTERNAL | CONTAS table (type 5) |
| 7 | FINANCEIRO | GOVERNMENT | CONTAS table (type 7) |
| 9 | (outros) | INTERNAL | CONTAS table (type 9) |
| 10 | (outros) | INTERNAL | CONTAS table (type 10) |
| 11 | (outros) | INTERNAL | CONTAS table (type 11) |
| 12 | (outros) | INTERNAL | CONTAS table (type 12) |
| 13 | (outros) | INTERNAL | CONTAS table (type 13) |
| 14 | (outros) | INTERNAL | CONTAS table (type 14) |
| 15 | COLABORADOR | EMPLOYEE | COLABORADOR table |

## Temporal
No period filter. Data ranges from 2000 to 2026.

**Date anomaly policy**: Invalid dates (< 2000 or > 2026) output null in canonical field.
Date order inconsistencies (payment before issue) emit DATE_ORDER_INCONSISTENT warning.
Never replace dates with defaults or today's date.

## Deduplication
Composite primary key (RCB_PGT, CODIGO_TIPO_CONTA, CODIGO_CONTA, DOC_RCB_PGT)
is unique — no duplicates exist (confirmed: 0 of 39,161).

## Status Mapping
Status computed using fixed snapshot date 2026-07-01 (not LocalDate.now()):

| Condition | Status |
|-----------|--------|
| VALORPAGO > 0 AND VALORPAGO >= VALOR | PAID |
| VALORPAGO > 0 AND VALORPAGO < VALOR | PARTIALLY_PAID |
| VALORPAGO > 0 AND VALORPAGO > VALOR | PAID_EXCESS |
| No payment, DATA_VENCIMENTO < 2026-07-01 (sane year) | OVERDUE |
| No payment, future date or null | OPEN |

PREVISTO_REALIZADO column is ALL NULL — not usable.

## Distribution (Filter D = 39,161)
| Status | Count | % |
|--------|-------|---|
| PAID | 38,088 | 97.26% |
| PARTIALLY_PAID | 373 | 0.95% |
| PAID_EXCESS | 1,079 | 2.75% |
| OVERDUE | 700 | 1.79% |
| OPEN | 1 | 0.003% |

(Note: PAID + PARTIALLY_PAID + PAID_EXCESS = 39,540 total paid-related records.
Some records counted in multiple status queries. Use mutually exclusive filter logic.)

## Counterparty Entity
Generated from CONTAS (types 2, 4, 5, 7, 9, 10, 11, 12, 13, 14, 15) + FORNECEDOR (type 3) + COLABORADOR (all types).
Written to counterparties.ndjson in bundle format 1.1.

The CONTAS filter previously only included types 2, 7, 15, which caused 43 payables with types 4, 5, 9, 10, 11, 12, 13, 14 to produce null counterpartyExternalId. The filter was widened to include all types handled by CounterpartyResolver for CONTAS resolution.

## Filters in both LIMITED_VALIDATION and FULL_EXTRACTION
All filter conditions, mappers, external ID rules, and semantic transformations are identical
between modes. Only maxRows and checkpoint behavior differ.
