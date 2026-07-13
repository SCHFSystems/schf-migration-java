package br.com.schf.migration.source.firebird;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface QueryCatalog {
    boolean hasQuery(String key);
    String query(String key);
    Map<String, String> allQueries();

    default Set<String> expectedSchemaTables() {
        return Set.of();
    }

    default String idColumn(String entityType) {
        return "id";
    }

    default int maxRows(String entityType) {
        return 0;
    }

    default String buildExternalId(String sourceInstanceId, String entityType, Map<String, Object> row) {
        var idCol = idColumn(entityType);
        var pk = row.getOrDefault(idCol, "unknown").toString();
        return UUID.nameUUIDFromBytes((sourceInstanceId + "|" + entityType + "|" + pk).getBytes()).toString();
    }
}
