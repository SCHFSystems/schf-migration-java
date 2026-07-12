package br.com.schf.migration.source.firebird;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FirebirdSchemaInspector {
    private final FirebirdQueryCatalog catalog;
    private static final List<String> EXPECTED_TABLES = List.of(
        "ORGANIZACAO", "FORNECEDOR", "CATEGORIA", "CONTA_BANCARIA",
        "CONTA_PAGAR", "PAGAMENTO", "USUARIO");

    public FirebirdSchemaInspector(FirebirdQueryCatalog catalog) {
        this.catalog = catalog;
    }

    public Map<String, Object> inspect(Connection conn) throws Exception {
        var result = new LinkedHashMap<String, Object>();
        var tables = listTables(conn);
        result.put("tableCount", tables.size());
        result.put("expectedTablesPresent", EXPECTED_TABLES.stream().filter(tables::contains).toList());
        result.put("expectedTablesMissing", EXPECTED_TABLES.stream().filter(t -> !tables.contains(t)).toList());
        result.put("allTableNames", tables);
        var columns = new LinkedHashMap<String, List<Map<String, String>>>();
        for (String table : EXPECTED_TABLES) {
            if (tables.contains(table)) {
                columns.put(table, inspectColumns(conn, table));
            }
        }
        result.put("columns", columns);
        return result;
    }

    public List<String> listTables(Connection conn) throws Exception {
        var tables = new ArrayList<String>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(catalog.query("inspect-tables"))) {
            while (rs.next()) {
                var name = rs.getString(1).trim();
                if (!name.isBlank()) tables.add(name);
            }
        }
        return tables;
    }

    public List<Map<String, String>> inspectColumns(Connection conn, String tableName) throws Exception {
        var columns = new ArrayList<Map<String, String>>();
        try (var stmt = conn.prepareStatement(catalog.query("inspect-columns"))) {
            stmt.setString(1, tableName);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var col = new LinkedHashMap<String, String>();
                    col.put("name", rs.getString(1).trim());
                    col.put("type", String.valueOf(rs.getInt(2)));
                    col.put("subType", String.valueOf(rs.getInt(3)));
                    col.put("length", String.valueOf(rs.getInt(4)));
                    columns.add(col);
                }
            }
        }
        return columns;
    }
}
