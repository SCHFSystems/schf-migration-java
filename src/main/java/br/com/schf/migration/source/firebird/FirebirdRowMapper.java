package br.com.schf.migration.source.firebird;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class FirebirdRowMapper {

    public Map<String, Object> mapRow(ResultSet rs) throws Exception {
        var meta = rs.getMetaData();
        var map = new LinkedHashMap<String, Object>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            map.put(meta.getColumnLabel(i).toLowerCase(), convert(rs, meta, i));
        }
        return map;
    }

    private Object convert(ResultSet rs, ResultSetMetaData meta, int i) throws Exception {
        var obj = rs.getObject(i);
        if (obj == null) return null;
        if (obj instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (obj instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        }
        if (obj instanceof Number num) {
            var str = num.toString();
            if (str.contains(".")) {
                var parts = str.split("\\.");
                if (parts[1].length() > 4) {
                    return String.format("%.2f", num.doubleValue());
                }
            }
            return str;
        }
        var str = obj.toString().strip();
        return str.isBlank() ? null : str;
    }
}
