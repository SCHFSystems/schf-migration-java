package br.com.schf.migration.source;

import java.util.Map;

@FunctionalInterface
public interface RecordHandler {
    void accept(String entityType, Map<String, Object> record) throws Exception;
}
