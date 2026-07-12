# Firebird SGH Mapping

## Legend

- **Origem**: Nome da tabela/coluna no Firebird SGH
- **Chave legada**: Campo usado como identificador na origem
- **Canonico**: Campo no bundle SCHF
- **Transformacao**: Regra aplicada
- **Nulidade**: `Obrigatorio` / `Opcional` / `Fallback`

## Organization

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| RDB$DATABASE.RDB$OWNER_NAME | — | externalId | UUID determinístico | Obrigatorio |
| Fixo "SGH" | — | code | Constante | Obrigatorio |
| Fixo "SGH Legacy" | — | name | Constante | Obrigatorio |

## User

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| USUARIO.CODUSU | CODUSU | externalId | UUID(nameUUIDFromBytes) | Obrigatorio |
| USUARIO.LOGIN | LOGIN | username | Trim | Obrigatorio |
| USUARIO.EMAIL | EMAIL | email | Trim + lowercase | Opcional |
| USUARIO.NOME | NOME | displayName | Trim | Obrigatorio |
| USUARIO.PERFIL | PERFIL | roleCodes | ADMIN/FINANCE/VIEWER | Opcional |

## Supplier

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| FORNECEDOR.CODFOR | CODFOR | externalId | UUID determinístico | Obrigatorio |
| FORNECEDOR.NOMEFOR | NOMEFOR | name | Trim | Obrigatorio |
| FORNECEDOR.CGC | CGC | document | Digits only | Opcional |
| FORNECEDOR.EMAIL | EMAIL | email | Trim | Opcional |
| FORNECEDOR.TELEFONE | TELEFONE | phone | Trim | Opcional |
| FORNECEDOR.ATIVO | ATIVO | active | S/N → true/false | Opcional |

## Category

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| CATEGORIA.CODCAT | CODCAT | externalId | UUID determinístico | Obrigatorio |
| CATEGORIA.DESCRICAO | DESCRICAO | name | Trim | Obrigatorio |
| CATEGORIA.TIPO | TIPO | type | RECEITA→INCOME, else EXPENSE | Opcional |

## Financial Account

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| CONTA_BANCARIA.CODCTG | CODCTG | externalId | UUID determinístico | Obrigatorio |
| CONTA_BANCARIA.DESCRICAO | DESCRICAO | name | Trim | Obrigatorio |
| CONTA_BANCARIA.BANCO | BANCO | bankName | Trim | Opcional |
| CONTA_BANCARIA.AGENCIA | AGENCIA | agency | Trim | Opcional |
| CONTA_BANCARIA.CONTA | CONTA | accountNumber | Trim | Opcional |

## Payable

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| CONTA_PAGAR.CODDCTO | CODDCTO | externalId | UUID determinístico | Obrigatorio |
| CONTA_PAGAR.HISTORICO | HISTORICO | description | Trim, max 200 chars | Opcional |
| CONTA_PAGAR.DOCUMENTO | DOCUMENTO | documentNumber | Trim | Opcional |
| CODFOR (FK) | — | supplierExternalId | UUID do fornecedor | Obrigatorio |
| CODCAT (FK) | — | categoryExternalId | UUID da categoria | Obrigatorio |
| CODCTG (FK) | — | financialAccountExternalId | UUID da conta | Obrigatorio |
| CONTA_PAGAR.EMISSAO | EMISSAO | issueDate | LocalDate ISO | Opcional |
| CONTA_PAGAR.VENCIMENTO | VENCIMENTO | dueDate | LocalDate ISO | Obrigatorio |
| CONTA_PAGAR.VALOR | VALOR | amount | Decimal 2 casas, positivo | Obrigatorio |
| CONTA_PAGAR.STATUS | STATUS | status | PAGO/CANC/VENC/OPEN | Opcional |

## Payment

| Origem | Chave legada | Canonico | Transformacao | Nulidade |
|--------|-------------|----------|---------------|----------|
| PAGAMENTO.CODPAG | CODPAG | externalId | UUID determinístico | Obrigatorio |
| CODDCTO (FK) | — | payableExternalId | UUID do payable | Obrigatorio |
| PAGAMENTO.DATAPAG | DATAPAG | paymentDate | LocalDate ISO | Opcional |
| PAGAMENTO.VALOR | VALOR | amount | Decimal 2 casas | Obrigatorio |
| PAGAMENTO.FORMA | FORMA | method | CHEQUE/TRANSFER/CREDIT_CARD/OTHER | Opcional |

## Known Risks

1. **Encoding**: Firebird 2.5 may use ISO-8859-1; strings are normalized to UTF-8.
2. **Null dates**: Some legacy dates may be `NULL` or `0000-00-00`.
3. **Duplicate documents**: CGC/CPF may have inconsistent formatting across suppliers.
4. **Orphan references**: Payables may reference deleted suppliers/categories.
5. **Money precision**: Legacy values may have more than 2 decimal places.
