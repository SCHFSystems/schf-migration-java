# Firebird SGH Mapping

## Source Profile: SGH_FIREBIRD_25

- **Firebird version**: 2.5 (ODS 11.2)
- **SQL Dialect**: 3
- **Default charset**: WIN1252
- **Page size**: 8192
- **Snapshot date**: 2026-07-01

## Fingerprint

Detected when schema has ALL of:
- `FORNECEDOR` (suppliers table)
- `USUARIO` (users table)
- `SFN_CLASSIFICACAO_FINANCEIRA` (financial classification / categories)
- `CONTAS` (master entity register)
- `SAF_CONTAS_PAGAR` (accounts payable module)
- `CONTAS_RECEBER_PAGAR` (receivables/payables)
- `OPERACAO_BANCO` (bank operations)

## Bundle Format Version: 1.1

Format 1.1 extends format 1.0 with:
- `counterparties.ndjson` — resolved counterparty entities
- `remainingAmount` on payable records
- `counterpartyExternalId` and `counterpartyType` on payable records
- `sourceSnapshotDate` on payable records
- `PayableStatus.PARTIALLY_PAID` and `PAID_EXCESS`
- `dateWarnings` with structured warning codes
- `overpaid` boolean flag on payable records
- Optional `paymentDate` field on payment records (null for unknown)
- `PaymentMethod` enum: CHECK, TRANSFER, CASH, CREDIT_CARD, OTHER
- `CounterpartyType` enum: SUPPLIER, EMPLOYEE, GOVERNMENT, INTERNAL, OTHER

Format 1.0 remains supported for backward compatibility.

## Organization

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| MON$DATABASE.MON$DATABASE_NAME (aliased as `code`) | code | externalId | UUID determinístico | Obrigatorio |
| MON$DATABASE_NAME (full path) | code | code | Trim | Obrigatorio |
| Fixo "SGH Legacy" | — | name | Constante | Obrigatorio |

## Supplier

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| FORNECEDOR.CODIGO | CODIGO | externalId | UUID(nameUUIDFromBytes(source + "|FORNECEDOR|" + CODIGO)) | Obrigatorio |
| FORNECEDOR.NOME | NOME | name | Trim; fallback NOMEFANTASIA | Obrigatorio |
| FORNECEDOR.CNPJ_CPF | CNPJ_CPF | document | Digits only; fallback CPF_CNPJ, DOCUMENTO | Opcional |
| FORNECEDOR.EMAIL | EMAIL | email | Trim | Opcional |
| FORNECEDOR.FONE | FONE | phone | Trim; fallback TELEFONE | Opcional |
| DESATIVADO | DESATIVADO | active | DESATIVADO='S' → false; ATIVO='S' → true | Opcional |

Count: 278 (all records, no filter). Baseline 278 matches.

## User

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| USUARIO.CODIGO_USUARIO | CODIGO_USUARIO | externalId | UUID determinístico | Obrigatorio |
| USUARIO.NOME | NOME | username | Trim + lowercase; fallback CODIGO_USUARIO | Obrigatorio |
| USUARIO.NOME_COMPLETO | NOME_COMPLETO | displayName | Trim; fallback para ucfirst(NOME) | Obrigatorio |
| USUARIO.EMAIL | EMAIL | email | Trim + lowercase; fallback EMAIL_ALTERNATIVO | Opcional |
| EXCLUIDO | EXCLUIDO | active | EXCLUIDO='S' → false | Opcional |

Count: 92 active (112 total, 20 excluded via EXCLUIDO='S'). Baseline 93 is close (1 diff likely due to data change).

## Category

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| SFN_CLASSIFICACAO_FINANCEIRA.ID_CLASSIFICACAO_FINANCEIRA | ID_CLASSIFICACAO_FINANCEIRA | externalId | UUID determinístico | Obrigatorio |
| SFN_CLASSIFICACAO_FINANCEIRA.DESCRICAO | DESCRICAO | name | Trim | Obrigatorio |
| — | — | type | EXPENSE (default); INCOME if nature=RECEITA | Opcional |
| EXCLUIR | EXCLUIR | active | EXCLUIR='S' → false | Opcional |

Count: 170 active (193 total, 23 excluded via EXCLUIR='S').

## Financial Account

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| CONTAS.CODIGO_CONTA | CODIGO_CONTA | externalId | UUID determinístico | Obrigatorio |
| CONTAS.NOME | NOME | name | Trim; fallback DESCRICAO | Obrigatorio |
| — | — | bankName | Extraído do nome (ex: "BANCO DO BRASIL - AG...") | Opcional |
| EXCLUIR | EXCLUIR | active | EXCLUIR='S' → false | Opcional |

Filter: CONTAS WHERE CODIGO_TIPO_CONTA = 6 AND (EXCLUIR IS NULL OR EXCLUIR <> 'S')

Count: 111 active (120 total, 9 excluded). Baseline 102 — close.

## Counterparty

Resolved from multiple source tables based on CODIGO_TIPO_CONTA:

| CODIGO_TIPO_CONTA | Nome TIPO_DE_CONTA | Canonical Type | Source Table | FK |
|---|---|---|---|---|
| 3 | FORNECEDOR | SUPPLIER | FORNECEDOR.CODIGO = CODIGO_CONTA | CONFIRMED |
| 2 | MEDICOS | INTERNAL | CONTA.CODIGO_CONTA = CODIGO_CONTA (type 2) | CONFIRMED |
| 7 | FINANCEIRO | GOVERNMENT / OTHER | CONTAS.CODIGO_CONTA = CODIGO_CONTA (type 7) | CONFIRMED |
| 15 | COLABORADOR | EMPLOYEE | COLABORADOR.CODIGO = CODIGO_CONTA (type 15) | CONFIRMED |

Counterparty external IDs are deterministic UUIDs: `UUID(nameUUIDFromBytes(sourceId + "|CONTAS|" + codigo_tipo_conta + "|" + codigo_conta))`

Only non-supplier types (2, 7, 15) are extracted via CONTAS query. Type 3 (FORNECEDOR) is resolved directly from the FORNECEDOR table.

## Payable

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| CONTAS_RECEBER_PAGAR (composite) | TIPO_CONTA-CONTA-DOC | externalId | UUID determinístico | Obrigatorio |
| COMPLEMENTO_HISTORICO | HISTORICO | description | Trim, max 200 chars | Opcional |
| DOC_RCB_PGT | DOCUMENTO | documentNumber | Trim | Opcional |
| VALOR | VALOR | amount | Decimal 2 casas, positivo | Obrigatorio |
| VALORPAGO | VALORPAGO | remainingAmount | Valor - VALORPAGO; 0.00 if none/fully paid | Obrigatorio |
| — | — | status | PAID / PARTIALLY_PAID / PAID_EXCESS / OVERDUE / OPEN | Obrigatorio |
| — | — | counterpartyExternalId | UUID do counterparty resolvido | Opcional |
| — | — | counterpartyType | SUPPLIER/EMPLOYEE/GOVERNMENT/INTERNAL/OTHER | Opcional |
| — | — | sourceSnapshotDate | 2026-07-01 (fixo) | Obrigatorio |
| — | — | overpaid | true se VALORPAGO > VALOR | Opcional |
| — | — | dateWarnings | structured warnings para datas invalidas | Opcional |

**Status rules** (using snapshot date 2026-07-01, not LocalDate.now()):
- VALORPAGO > 0 AND VALORPAGO >= VALOR → PAID
- VALORPAGO > 0 AND VALORPAGO < VALOR → PARTIALLY_PAID
- VALORPAGO > 0 AND VALORPAGO > VALOR → PAID_EXCESS
- No payment AND DATA_VENCIMENTO < 2026-07-01 → OVERDUE
- No payment AND (DATA_VENCIMENTO IS NULL OR DATA_VENCIMENTO >= 2026-07-01) → OPEN

**Date anomaly policy:**
- Invalid years (< 2000 or > 2026): output null in canonical field, emit INVALID_*_DATE warning
- Payment date before issue date: keep dates, emit DATE_ORDER_INCONSISTENT warning
- Never invent or replace dates with defaults

Query: `CONTAS_RECEBER_PAGAR WHERE RCB_PGT = 'P' AND (EXCLUIR IS NULL OR EXCLUIR <> 'S') AND VALOR > 0`

Count: 39,161 (non-excluded, VALOR>0).

## Payment

**Status**: ONE_ACCUMULATED_PAYMENT_PER_PAYABLE (CONFIRMED)

Payment data embedded in CONTAS_RECEBER_PAGAR: VALORPAGO, DATA_PAGAMENTO, FORMA_PR.

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| — | — | payableExternalId | UUID do payable pai | Obrigatorio |
| VALORPAGO | VALORPAGO | amount | Decimal 2 casas | Obrigatorio |
| DATA_PAGAMENTO | DATA_PAGAMENTO | paymentDate | ISO date; omitido se nulo | Opcional |
| FORMA_PR | FORMA_PR | method | 'B' → CHECK, 'C' → CASH, outros → OTHER | Obrigatorio |
| — | — | zeroAmount | true if VALORPAGO = 0 | Opcional |

Payment method mapping:
- FORMA_PR = 'B' → CHECK (boleto/cheque)
- FORMA_PR = 'C' → CASH (dinheiro)
- BOLETO/TRANSFER/TED/DOC → TRANSFER
- CARTAO/CARD/CREDIT → CREDIT_CARD
- All others → OTHER

## Counterparty Entity (counterparties.ndjson)

| Canonico | Transformacao | Nulidade |
|----------|---------------|----------|
| externalId | UUID determinístico | Obrigatorio |
| name | NOME da CONTAS/COLABORADOR/FORNECEDOR | Opcional |
| type | SUPPLIER/EMPLOYEE/GOVERNMENT/INTERNAL/OTHER | Obrigatorio |
| sourceReference | "{CODIGO_TIPO_CONTA}\|{CODIGO_CONTA}" | Obrigatorio |

## External ID Strategy

Format: `UUID.nameUUIDFromBytes((sourceInstanceId + "|" + sourceTable + ":" + legacyPrimaryKey).getBytes())`

| Entity | Source Table | PK Column | Example |
|--------|-------------|-----------|---------|
| Organization | MON$DATABASE | code | UUID(nameUUIDFromBytes(sourceId + "|MON$DATABASE|" + code)) |
| Supplier | FORNECEDOR | CODIGO | UUID(nameUUIDFromBytes(sourceId + "|FORNECEDOR:" + CODIGO)) |
| User | USUARIO | CODIGO_USUARIO | UUID(nameUUIDFromBytes(sourceId + "|USUARIO:" + CODIGO_USUARIO)) |
| Category | SFN_CLASSIFICACAO_FINANCEIRA | ID_CLASSIFICACAO_FINANCEIRA | UUID(nameUUIDFromBytes(sourceId + "|SFN_CLASSIFICACAO_FINANCEIRA:" + ID)) |
| Financial Account | CONTAS | CODIGO_CONTA | UUID(nameUUIDFromBytes(sourceId + "|CONTAS:" + CODIGO_CONTA)) |
| Counterparty | CONTAS | CODIGO_TIPO_CONTA \| CODIGO_CONTA | UUID(nameUUIDFromBytes(sourceId + "|CONTAS|" + tipo + "|" + conta)) |
| Payable | CONTAS_RECEBER_PAGAR | composite: RCB_PGT\|TIPO_CONTA\|CONTA\|DOC | UUID(nameUUIDFromBytes(sourceId + "|CONTAS_RECEBER_PAGAR|" + ...)) |
| Payment | CONTAS_RECEBER_PAGAR | same as payable + "|PAYMENT" | UUID(nameUUIDFromBytes(sourceId + "|CONTAS_RECEBER_PAGAR|" + composite + "|PAYMENT")) |

## Known Risks

1. **Encoding**: Firebird 2.5 uses WIN1252; strings are normalized to UTF-8 on extraction.
2. **Null dates**: Some legacy dates may be NULL or default values. Output null, emit warning.
3. **CONTAS vs CONTA**: Both tables exist. CONTAS is the master entity register (all types). CONTA is a view/subset (only professionals, type 2).
4. **Bank account type**: CODIGO_TIPO_CONTA = 6 for banks, not 3 as in earlier analysis.
5. **Payables volume**: 39,161 (non-excluded, VALOR>0) in CONTAS_RECEBER_PAGAR.
6. **Account type resolution**: CODIGO_TIPO_CONTA=3 (15,398 records) → SUPPLIER; type 7 (21,778) → GOVERNMENT/OTHER; type 15 (1,976) → EMPLOYEE; type 2 (9) → INTERNAL.
7. **Date anomalies**: 405 payment-before-issue records; 6 zero-pay with dates; ~10 bogus years in vencimento, ~56 in emissao.
8. **PREVISTO_REALIZADO**: ALL NULL. Status based on VALORPAGO + DATA_PAGAMENTO + snapshot date.
9. **Money precision**: Legacy values may have more than 2 decimal places. Normalized to 2.
10. **COLABORADOR table**: Some COLABORADOR records may be excluded (EXCLUIR='S'). Counterparty resolver filters active only.
