package br.com.schf.migration.source;

import java.util.Map;

public interface CheckpointStore {
    void save(String phase, Map<String, Object> state);
    Map<String, Object> load(String phase);
    boolean hasCompleted(String phase);
    void markCompleted(String phase);
    void clear();
}
