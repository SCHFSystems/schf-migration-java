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
CODIGO_TIPO_CONTA = 3 (maps to FORNECEDOR via CODIGO_CONTA)
Expected count: ~15,398

### Stage 2: All types
No CODIGO_TIPO_CONTA restriction
Expected count: ~39,161
Requires reference resolution for types 2, 7, 15 (non-supplier)

## Temporal
No period filter. Data ranges from 2000 to 2026.
Bogus dates (< 2000 or > 2026) should have year corrected per BOGUS_YEARS rule:
- Invalid year detected → use year from DATA_PAGAMENTO if available
- If DATA_PAGAMENTO also invalid → use 2000 as default

## Deduplication
Composite primary key (RCB_PGT, CODIGO_TIPO_CONTA, CODIGO_CONTA, DOC_RCB_PGT)
is unique — no duplicates exist (confirmed: 0 of 39,161).

## Status Mapping
| Condition | Status |
|-----------|--------|
| VALORPAGO > 0 AND DATA_PAGAMENTO NOT NULL | paid |
| No payment, DATA_VENCIMENTO < today (sane year) | overdue |
| No payment, future date or bogus year | open |

PREVISTO_REALIZADO column is ALL NULL in this database — not usable.

## Filters in both LIMITED_VALIDATION and FULL_EXTRACTION
All filter conditions are identical between modes. Only maxRows changes (10 vs unlimited).
