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
| CONTAS_RECEBER_PAGAR.PREVISTO_REALIZADO / DATA_PAGAMENTO | PREVISTO_REALIZADO | status | 'P'+sem data=pending, com data=paid, 'R'=paid, excluído=cancelled | Opcional |

Query: `CONTAS_RECEBER_PAGAR WHERE RCB_PGT = 'P' AND (EXCLUIR IS NULL OR EXCLUIR <> 'S') AND VALOR > 0`

Count: ~39,722 total payable records (RCB_PGT='P'). Baseline 13,313 was from a limited extraction or had additional filters. Investigate.

## Payment

**Status**: UNRESOLVED (see SGH_PAYMENT_MAPPING_INVESTIGATION.md)

Previous migration mapped `CONTAS_RECEBER_PAGAR` with payment data embedded (DATA_PAGAMENTO, VALORPAGO, FORMA_PR) rather than a separate payment table. No standalone `PAGAMENTO` table exists.

## External ID Strategy

Format: `UUID.nameUUIDFromBytes((sourceInstanceId + "|" + sourceTable + ":" + legacyPrimaryKey).getBytes())`

| Entity | Source Table | PK Column | Example |
|--------|-------------|-----------|---------|
| Organization | MON$DATABASE | code | UUID(nameUUIDFromBytes(sourceId + "|MON$DATABASE|" + code)) |
| Supplier | FORNECEDOR | CODIGO | UUID(nameUUIDFromBytes(sourceId + "|FORNECEDOR:" + CODIGO)) |
| User | USUARIO | CODIGO_USUARIO | UUID(nameUUIDFromBytes(sourceId + "|USUARIO:" + CODIGO_USUARIO)) |
| Category | SFN_CLASSIFICACAO_FINANCEIRA | ID_CLASSIFICACAO_FINANCEIRA | UUID(nameUUIDFromBytes(sourceId + "|SFN_CLASSIFICACAO_FINANCEIRA:" + ID)) |
| Financial Account | CONTAS | CODIGO_CONTA | UUID(nameUUIDFromBytes(sourceId + "|CONTAS:" + CODIGO_CONTA)) |
| Payable | CONTAS_RECEBER_PAGAR | TIPO_CONTA-CONTA-NUMERO_OPERACAO | UUID(nameUUIDFromBytes(sourceId + "|CONTAS_RECEBER_PAGAR:" + composite)) |

## Known Risks

1. **Encoding**: Firebird 2.5 uses WIN1252; strings are normalized to UTF-8 on extraction.
2. **Null dates**: Some legacy dates may be NULL or default values.
3. **CONTAS vs CONTA**: Both tables exist. CONTAS is the master entity register (all types). CONTA is a view/subset (only professionals, type 2).
4. **Bank account type**: CODIGO_TIPO_CONTA = 6 for banks, not 3 as in earlier analysis.
5. **Payables volume**: ~39,722 records with RCB_PGT='P'; only ~13k were in the previous bundle — additional filters unknown.
6. **Orphan references**: Payables reference CODIGO_CONTA which may map to suppliers (type 3) or other entities.
7. **Money precision**: Legacy values may have more than 2 decimal places.
