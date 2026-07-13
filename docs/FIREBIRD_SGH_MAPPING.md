# Firebird SGH Mapping

## Source Profile: SGH_FIREBIRD_25

- **Firebird version**: 2.5 (ODS 11.2)
- **SQL Dialect**: 3
- **Default charset**: WIN1252
- **Page size**: 8192

## Fingerprint

Detected when schema has ALL of:
- `FORNECEDOR` (suppliers table)
- `USUARIO` (users table)
- `SFN_CLASSIFICACAO_FINANCEIRA` (financial classification / categories)
- `CONTAS` (master entity register)
- `SAF_CONTAS_PAGAR` (accounts payable module)
- `CONTAS_RECEBER_PAGAR` (receivables/payables)
- `OPERACAO_BANCO` (bank operations)

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
| FORNECEDOR.NOME | NOME | name | Trim | Obrigatorio |
| FORNECEDOR.CNPJ_CPF | CNPJ_CPF | document | Digits only | Opcional |
| FORNECEDOR.EMAIL | EMAIL | email | Trim | Opcional |
| FORNECEDOR.FONE | FONE | phone | Trim | Opcional |
| — | — | active | All suppliers migrated (DESATIVADO not used in previous migration) | Opcional |

Count: 278 (all records, no filter). Baseline 278 matches.

## User

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| USUARIO.CODIGO_USUARIO | CODIGO_USUARIO | externalId | UUID determinístico | Obrigatorio |
| USUARIO.NOME | NOME | username | Trim + lowercase | Obrigatorio |
| USUARIO.NOME_COMPLETO | NOME_COMPLETO | displayName | Trim; fallback para ucfirst(NOME) | Obrigatorio |
| USUARIO.EMAIL | EMAIL | email | Trim + lowercase; se vazio, gerar login@domain | Opcional |

Count: 92 active (112 total, 20 excluded via EXCLUIDO='S'). Baseline 93 is close (1 diff likely due to data change).

## Category

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| SFN_CLASSIFICACAO_FINANCEIRA.ID_CLASSIFICACAO_FINANCEIRA | ID_CLASSIFICACAO_FINANCEIRA | externalId | UUID determinístico | Obrigatorio |
| SFN_CLASSIFICACAO_FINANCEIRA.DESCRICAO | DESCRICAO | name | Trim | Obrigatorio |
| — | — | type | EXPENSE (default; classificação financeira) | Opcional |

Count: 170 active (193 total, 23 excluded via EXCLUIR='S'). Baseline 366 — discrepancy may be hierarchical expansion or different source. Investigate.

## Financial Account

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| CONTAS.CODIGO_CONTA | CODIGO_CONTA | externalId | UUID determinístico | Obrigatorio |
| CONTAS.NOME | NOME | name | Trim | Obrigatorio |
| — | — | bankName | Extraído do nome (ex: "BANCO DO BRASIL - AG...") | Opcional |

Filter: CONTAS WHERE CODIGO_TIPO_CONTA = 6 AND (EXCLUIR IS NULL OR EXCLUIR <> 'S')

CODIGO_TIPO_CONTA reference:
- 1 = Cost center
- 2 = Professional
- 3 = Supplier (redundant with FORNECEDOR)
- 4 = Health plan
- 5 = Cash flow
- 6 = Bank account
- 7 = Others (1775 recs)
- 15, 18 = Other types

Count: 111 active (120 total, 9 excluded). Baseline 102 — close; 9-record difference may be accounts created after baseline.

## Payable

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| CONTAS_RECEBER_PAGAR (composite) | TIPO_CONTA-CONTA-NUMERO_OPERACAO | externalId | UUID determinístico | Obrigatorio |
| CONTAS_RECEBER_PAGAR.COMPLEMENTO_HISTORICO | COMPLEMENTO_HISTORICO | description | Trim, max 200 chars | Opcional |
| CONTAS_RECEBER_PAGAR.DOC_RCB_PGT | DOC_RCB_PGT | documentNumber | Trim | Opcional |
| CODIGO_CONTA (FK → CONTAS or FORNECEDOR) | CODIGO_CONTA | supplierExternalId | UUID do fornecedor | Obrigatorio |
| CONTAS_RECEBER_PAGAR.DATA_EMISSAO_DOC_REFERENTE | DATA_EMISSAO | issueDate | LocalDate ISO | Opcional |
| CONTAS_RECEBER_PAGAR.DATA_VENCIMENTO | DATA_VENCIMENTO | dueDate | LocalDate ISO | Obrigatorio |
| CONTAS_RECEBER_PAGAR.VALOR | VALOR | amount | Decimal 2 casas, positivo | Obrigatorio |
| CONTAS_RECEBER_PAGAR.VALORPAGO | VALORPAGO | status | >0 AND DATA_PAGAMENTO NOT NULL = paid; no payment + overdue = overdue; else open | Obrigatorio |

Query: `CONTAS_RECEBER_PAGAR WHERE RCB_PGT = 'P' AND (EXCLUIR IS NULL OR EXCLUIR <> 'S') AND VALOR > 0`

Count: 39,161 (non-excluded, VALOR>0). Baseline 13,313 came from SAF_CONTAS_PAGAR (5,961) + SAF_CONTAS_PAGAR_DUP (7,352) — a different module. See SGH_COUNT_AND_FILTER_RULES.md.

## Payment

**Status**: ONE_ACCUMULATED_PAYMENT_PER_PAYABLE (GATE B1.5)

Payment data embedded in CONTAS_RECEBER_PAGAR: VALORPAGO, DATA_PAGAMENTO, FORMA_PR. No standalone PAGAMENTO table.

| Condition | Cardinality | Count |
|-----------|-------------|-------|
| VALORPAGO > 0 | 1 accumulated payment per payable | 38,460 |
| COD_FRACIONADO | Rare edge case (20 records) | 20 |
| OPERACAO_BANCO per payable | 0 or 1 (never >1) | 37,298 linked |

See SGH_PAYMENT_CARDINALITY.md for full analysis.

## External ID Strategy

Format: `UUID.nameUUIDFromBytes((sourceInstanceId + "|" + sourceTable + ":" + legacyPrimaryKey).getBytes())`

| Entity | Source Table | PK Column | Example |
|--------|-------------|-----------|---------|
| Organization | MON$DATABASE | code | UUID(nameUUIDFromBytes(sourceId + "|MON$DATABASE|" + code)) |
| Supplier | FORNECEDOR | CODIGO | UUID(nameUUIDFromBytes(sourceId + "|FORNECEDOR:" + CODIGO)) |
| User | USUARIO | CODIGO_USUARIO | UUID(nameUUIDFromBytes(sourceId + "|USUARIO:" + CODIGO_USUARIO)) |
| Category | SFN_CLASSIFICACAO_FINANCEIRA | ID_CLASSIFICACAO_FINANCEIRA | UUID(nameUUIDFromBytes(sourceId + "|SFN_CLASSIFICACAO_FINANCEIRA:" + ID)) |
| Financial Account | CONTAS | CODIGO_CONTA | UUID(nameUUIDFromBytes(sourceId + "|CONTAS:" + CODIGO_CONTA)) |
| Payable | CONTAS_RECEBER_PAGAR | composite: RCB_PGT\|CODIGO_TIPO_CONTA\|CODIGO_CONTA\|DOC_RCB_PGT | UUID(nameUUIDFromBytes(sourceId + "\|CONTAS_RECEBER_PAGAR\|" + rcb_pgt + "\|" + tipo_conta + "\|" + conta + "\|" + doc)) |
| Payment | CONTAS_RECEBER_PAGAR | same as payable + "\|PAYMENT" | UUID(nameUUIDFromBytes(sourceId + "\|CONTAS_RECEBER_PAGAR\|" + composite + "\|PAYMENT")) |

## Known Risks

1. **Encoding**: Firebird 2.5 uses WIN1252; strings are normalized to UTF-8 on extraction.
2. **Null dates**: Some legacy dates may be NULL or default values.
3. **CONTAS vs CONTA**: Both tables exist. CONTAS is the master entity register (all types). CONTA is a view/subset (only professionals, type 2).
4. **Bank account type**: CODIGO_TIPO_CONTA = 6 for banks, not 3 as in earlier analysis.
5. **Payables volume**: 39,161 (D) in CONTAS_RECEBER_PAGAR. Previous 13,313 baseline was from SAF_CONTAS_PAGAR (different table/module).
6. **Account type resolution**: CODIGO_TIPO_CONTA=3 (15,398 records) maps to FORNECEDOR; types 2,7,15 (23,763 records) map to CONTAS (cost centers, professionals, others). Reference resolution must handle all types.
7. **Bogus dates**: ~10 records with year < 2000 or > 2026 in vencimento; ~56 in emissao. Legacy BOGUS_YEARS rule applies.
8. **PREVISTO_REALIZADO**: ALL NULL in this database. Status based on VALORPAGO + DATA_PAGAMENTO instead.
9. **Money precision**: Legacy values may have more than 2 decimal places.
