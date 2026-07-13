package br.com.schf.migration.source.firebird;

import java.util.LinkedHashMap;
import java.util.Map;

public class FirebirdQueryCatalog {
    private final Map<String, String> queries = new LinkedHashMap<>();

    public FirebirdQueryCatalog() {
        queries.put("inspect-tables", "SELECT RDB$RELATION_NAME FROM RDB$RELATIONS WHERE RDB$SYSTEM_FLAG = 0 AND RDB$RELATION_TYPE = 0 ORDER BY RDB$RELATION_NAME");
        queries.put("inspect-columns", "SELECT rf.RDB$FIELD_NAME, f.RDB$FIELD_TYPE, f.RDB$FIELD_SUB_TYPE, f.RDB$FIELD_LENGTH FROM RDB$RELATION_FIELDS rf JOIN RDB$FIELDS f ON rf.RDB$FIELD_SOURCE = f.RDB$FIELD_NAME WHERE rf.RDB$RELATION_NAME = ? ORDER BY rf.RDB$FIELD_POSITION");
        queries.put("source-instance", "SELECT FIRST 1 MON$DATABASE_NAME FROM MON$DATABASE");
        queries.put("organization", "SELECT FIRST 1 MON$DATABASE_NAME AS code FROM MON$DATABASE");
        queries.put("count-organizations", "SELECT COUNT(*) FROM ORGANIZACAO");
        queries.put("count-suppliers", "SELECT COUNT(*) FROM FORNECEDOR");
        queries.put("count-categories", "SELECT COUNT(*) FROM CATEGORIA");
        queries.put("count-accounts", "SELECT COUNT(*) FROM CONTA_BANCARIA");
        queries.put("count-payables", "SELECT COUNT(*) FROM CONTA_PAGAR");
        queries.put("count-users", "SELECT COUNT(*) FROM USUARIO");
        queries.put("suppliers", "SELECT * FROM FORNECEDOR ORDER BY CODFOR");
        queries.put("categories", "SELECT * FROM CATEGORIA ORDER BY CODCAT");
        queries.put("financial-accounts", "SELECT * FROM CONTA_BANCARIA ORDER BY CODCTG");
        queries.put("payables", "SELECT * FROM CONTA_PAGAR ORDER BY CODDCTO");
        queries.put("payments", "SELECT * FROM PAGAMENTO ORDER BY CODPAG");
        queries.put("users", "SELECT * FROM USUARIO ORDER BY CODUSU");
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
}
