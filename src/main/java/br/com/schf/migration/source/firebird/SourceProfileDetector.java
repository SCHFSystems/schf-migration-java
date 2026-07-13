package br.com.schf.migration.source.firebird;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class SourceProfileDetector {

    public SourceProfile detect(Connection conn) {
        var tables = listUserTables(conn);
        if (tables.containsAll(sghFingerprint())) {
            return SourceProfile.SGH_FIREBIRD_25;
        }
        return SourceProfile.SYNTHETIC;
    }

    private Set<String> sghFingerprint() {
        return Set.of(
            "FORNECEDOR", "USUARIO", "SFN_CLASSIFICACAO_FINANCEIRA",
            "CONTAS", "SAF_CONTAS_PAGAR", "OPERACAO_BANCO");
    }

    private Set<String> listUserTables(Connection conn) {
        var tables = new HashSet<String>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                 "SELECT RDB$RELATION_NAME FROM RDB$RELATIONS " +
                 "WHERE RDB$SYSTEM_FLAG = 0 AND RDB$RELATION_TYPE = 0")) {
            while (rs.next()) {
                var name = rs.getString(1);
                if (name != null) tables.add(name.strip());
            }
        } catch (SQLException ignored) {
        }
        return tables;
    }
}
