package br.com.schf.migration.source.firebird;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SghFirebird25QueryCatalog implements QueryCatalog {
    private final Map<String, String> queries = new LinkedHashMap<>();
    private final ExtractionMode mode;

    public SghFirebird25QueryCatalog() {
        this(ExtractionMode.LIMITED_VALIDATION);
    }

    public SghFirebird25QueryCatalog(ExtractionMode mode) {
        this.mode = mode;
        queries.put("inspect-tables",
            "SELECT RDB$RELATION_NAME FROM RDB$RELATIONS WHERE RDB$SYSTEM_FLAG = 0 AND RDB$RELATION_TYPE = 0 ORDER BY RDB$RELATION_NAME");
        queries.put("inspect-columns",
            "SELECT rf.RDB$FIELD_NAME, f.RDB$FIELD_TYPE, f.RDB$FIELD_SUB_TYPE, f.RDB$FIELD_LENGTH FROM RDB$RELATION_FIELDS rf JOIN RDB$FIELDS f ON rf.RDB$FIELD_SOURCE = f.RDB$FIELD_NAME WHERE rf.RDB$RELATION_NAME = ? ORDER BY rf.RDB$FIELD_POSITION");
        queries.put("source-instance",
            "SELECT FIRST 1 MON$DATABASE_NAME FROM MON$DATABASE");
        queries.put("organization",
            "SELECT FIRST 1 MON$DATABASE_NAME AS code FROM MON$DATABASE");
        queries.put("count-organizations", "SELECT 1 FROM RDB$DATABASE");
        queries.put("count-suppliers",
            "SELECT COUNT(*) FROM FORNECEDOR");
        queries.put("count-categories",
            "SELECT COUNT(*) FROM SFN_CLASSIFICACAO_FINANCEIRA WHERE EXCLUIR IS NULL OR EXCLUIR <> 'S'");
        queries.put("count-accounts",
            "SELECT COUNT(*) FROM CONTAS WHERE CODIGO_TIPO_CONTA = 6 AND (EXCLUIR IS NULL OR EXCLUIR <> 'S')");
        queries.put("count-payables",
            "SELECT COUNT(*) FROM CONTAS_RECEBER_PAGAR WHERE RCB_PGT = 'P' AND (EXCLUIR IS NULL OR EXCLUIR <> 'S') AND VALOR > 0");
        queries.put("count-payments",
            "SELECT COUNT(*) FROM CONTAS_RECEBER_PAGAR WHERE RCB_PGT = 'P' AND (EXCLUIR IS NULL OR EXCLUIR <> 'S') AND VALORPAGO > 0");
        queries.put("count-users",
            "SELECT COUNT(*) FROM USUARIO WHERE EXCLUIDO IS NULL OR EXCLUIDO <> 'S'");
        queries.put("count-counterparties",
            "SELECT COUNT(*) FROM ("
            + " SELECT CODIGO_TIPO_CONTA, CODIGO_CONTA FROM CONTAS WHERE (EXCLUIR IS NULL OR EXCLUIR <> 'S') AND CODIGO_TIPO_CONTA IN (2, 4, 5, 7, 9, 10, 11, 12, 13, 14, 15)"
            + " UNION"
            + " SELECT '3', CODIGO FROM FORNECEDOR"
            + " UNION"
            + " SELECT CODIGO_TIPO_CONTA, CODIGO FROM COLABORADOR WHERE (DESATIVADO IS NULL OR DESATIVADO <> 'S')"
            + ") src");
        queries.put("suppliers",
            "SELECT * FROM FORNECEDOR ORDER BY CODIGO");
        queries.put("categories",
            "SELECT * FROM SFN_CLASSIFICACAO_FINANCEIRA WHERE EXCLUIR IS NULL OR EXCLUIR <> 'S' ORDER BY ID_CLASSIFICACAO_FINANCEIRA");
        queries.put("financial-accounts",
            "SELECT * FROM CONTAS WHERE CODIGO_TIPO_CONTA = 6 AND (EXCLUIR IS NULL OR EXCLUIR <> 'S') ORDER BY CODIGO_CONTA");
        queries.put("counterparties",
            "SELECT CODIGO_TIPO_CONTA, CODIGO_CONTA, NOME FROM ("
            + "SELECT CODIGO_TIPO_CONTA, CODIGO_CONTA, NOME FROM CONTAS WHERE (EXCLUIR IS NULL OR EXCLUIR <> 'S') AND CODIGO_TIPO_CONTA IN (2, 4, 5, 7, 9, 10, 11, 12, 13, 14, 15) "
            + "UNION "
            + "SELECT '3' AS CODIGO_TIPO_CONTA, CODIGO AS CODIGO_CONTA, NOME FROM FORNECEDOR "
            + "UNION "
            + "SELECT CODIGO_TIPO_CONTA, CODIGO AS CODIGO_CONTA, NOME FROM COLABORADOR WHERE (DESATIVADO IS NULL OR DESATIVADO <> 'S')"
            + ") src ORDER BY CODIGO_TIPO_CONTA, CODIGO_CONTA");
        queries.put("counterparties-suppliers",
            "SELECT CODIGO AS CODIGO_CONTA, NOME, '3' AS CODIGO_TIPO_CONTA FROM FORNECEDOR");
        queries.put("counterparties-colaboradores",
            "SELECT CODIGO AS CODIGO_CONTA, NOME, CODIGO_TIPO_CONTA FROM COLABORADOR WHERE (DESATIVADO IS NULL OR DESATIVADO <> 'S')");
        queries.put("payables",
            "SELECT * FROM CONTAS_RECEBER_PAGAR WHERE RCB_PGT = 'P' AND (EXCLUIR IS NULL OR EXCLUIR <> 'S') AND VALOR > 0 ORDER BY DATA_VENCIMENTO, CODIGO_TIPO_CONTA, CODIGO_CONTA, DOC_RCB_PGT");
        queries.put("payments", buildPaymentsQuery());
        queries.put("users",
            "SELECT * FROM USUARIO WHERE EXCLUIDO IS NULL OR EXCLUIDO <> 'S' ORDER BY CODIGO_USUARIO");
    }

    private String buildPaymentsQuery() {
        if (mode.isLimited()) {
            var sort = " ORDER BY DATA_PAGAMENTO, CODIGO_TIPO_CONTA, CODIGO_CONTA, DOC_RCB_PGT";
            return "SELECT * FROM (SELECT FIRST 3 * FROM CONTAS_RECEBER_PAGAR WHERE RCB_PGT='P' AND VALORPAGO>0 AND FORMA_PR='B' AND (EXCLUIR IS NULL OR EXCLUIR<>'S') AND VALORPAGO<VALOR" + sort + ") t1"
                + " UNION ALL"
                + " SELECT * FROM (SELECT FIRST 1 * FROM CONTAS_RECEBER_PAGAR WHERE RCB_PGT='P' AND VALORPAGO>0 AND FORMA_PR='B' AND (EXCLUIR IS NULL OR EXCLUIR<>'S') AND VALORPAGO>VALOR" + sort + ") t2"
                + " UNION ALL"
                + " SELECT * FROM (SELECT FIRST 1 * FROM CONTAS_RECEBER_PAGAR WHERE RCB_PGT='P' AND VALORPAGO>0 AND FORMA_PR='B' AND (EXCLUIR IS NULL OR EXCLUIR<>'S') AND VALORPAGO=VALOR" + sort + ") t3"
                + " UNION ALL"
                + " SELECT * FROM (SELECT FIRST 2 * FROM CONTAS_RECEBER_PAGAR WHERE RCB_PGT='P' AND VALORPAGO>0 AND FORMA_PR='C' AND (EXCLUIR IS NULL OR EXCLUIR<>'S')" + sort + ") t4"
                + " UNION ALL"
                + " SELECT * FROM (SELECT FIRST 2 * FROM CONTAS_RECEBER_PAGAR WHERE RCB_PGT='P' AND VALORPAGO>0 AND (FORMA_PR IS NULL OR FORMA_PR='') AND (EXCLUIR IS NULL OR EXCLUIR<>'S')" + sort + ") t5"
                + " UNION ALL"
                + " SELECT * FROM (SELECT FIRST 1 * FROM CONTAS_RECEBER_PAGAR WHERE RCB_PGT='P' AND VALORPAGO>0 AND EXCLUIR='S'" + sort + ") t6";
        }
        return "SELECT * FROM CONTAS_RECEBER_PAGAR WHERE RCB_PGT='P' AND (EXCLUIR IS NULL OR EXCLUIR<>'S') AND VALORPAGO>0 ORDER BY DATA_PAGAMENTO, CODIGO_TIPO_CONTA, CODIGO_CONTA, DOC_RCB_PGT";
    }

    public boolean hasQuery(String key) {
        return queries.containsKey(key);
    }

    public String query(String key) {
        var q = queries.get(key);
        if (q == null) {
            throw new IllegalArgumentException("Unknown query: " + key);
        }
        return q;
    }

    public Map<String, String> allQueries() {
        return Map.copyOf(queries);
    }

    @Override
    public Set<String> expectedSchemaTables() {
        return Set.of("FORNECEDOR", "USUARIO", "SFN_CLASSIFICACAO_FINANCEIRA",
            "CONTAS", "SAF_CONTAS_PAGAR", "CONTAS_RECEBER_PAGAR", "OPERACAO_BANCO",
            "COLABORADOR", "CONTA");
    }

    @Override
    public String idColumn(String entityType) {
        return switch (entityType) {
            case "suppliers" -> "codigo";
            case "categories" -> "id_classificacao_financeira";
            case "financial-accounts" -> "codigo_conta";
            case "counterparties" -> "codigo_conta";
            case "payables" -> "doc_rcb_pgt";
            case "payments" -> "doc_rcb_pgt";
            case "users" -> "codigo_usuario";
            case "organizations" -> "code";
            default -> "id";
        };
    }

    @Override
    public int maxRows(String entityType) {
        if (!mode.isLimited()) return 0;
        if ("organizations".equals(entityType)) return 1;
        if ("payments".equals(entityType)) return 0;
        if ("counterparties".equals(entityType)) return 0;
        return 10;
    }

    @Override
    public String buildExternalId(String sourceInstanceId, String entityType, Map<String, Object> row) {
        if ("payables".equals(entityType) || "payments".equals(entityType)) {
            var rcbPgt = row.getOrDefault("rcb_pgt", "?").toString();
            var tipoConta = row.getOrDefault("codigo_tipo_conta", "?").toString();
            var codConta = row.getOrDefault("codigo_conta", "?").toString();
            var docRcbPgt = row.getOrDefault("doc_rcb_pgt", "?").toString();
            var suffix = "payments".equals(entityType) ? "|PAYMENT" : "";
            var key = sourceInstanceId + "|CONTAS_RECEBER_PAGAR|"
                + rcbPgt + "|" + tipoConta + "|" + codConta + "|" + docRcbPgt + suffix;
            return UUID.nameUUIDFromBytes(key.getBytes()).toString();
        }
        if ("counterparties".equals(entityType)) {
            var tipoConta = row.getOrDefault("codigo_tipo_conta", "?").toString();
            var codConta = row.getOrDefault("codigo_conta", "?").toString();
            if ("3".equals(tipoConta)) {
                var key = sourceInstanceId + "|suppliers|" + codConta;
                return UUID.nameUUIDFromBytes(key.getBytes()).toString();
            }
            var key = sourceInstanceId + "|CONTAS|" + tipoConta + "|" + codConta;
            return UUID.nameUUIDFromBytes(key.getBytes()).toString();
        }
        return QueryCatalog.super.buildExternalId(sourceInstanceId, entityType, row);
    }
}
