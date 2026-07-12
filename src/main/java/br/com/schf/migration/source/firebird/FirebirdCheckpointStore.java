package br.com.schf.migration.source.firebird;

import br.com.schf.migration.source.CheckpointStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class FirebirdCheckpointStore implements CheckpointStore {
    private final File file;
    private final ObjectMapper mapper;
    private final Map<String, PhaseState> store = new LinkedHashMap<>();

    record PhaseState(boolean completed, Map<String, Object> state) {}

    public FirebirdCheckpointStore(Path workDirectory) {
        this.file = workDirectory.resolve("extraction-checkpoints.json").toFile();
        this.mapper = new ObjectMapper();
        loadFromDisk();
    }

    @Override
    public synchronized void save(String phase, Map<String, Object> state) {
        store.put(phase, new PhaseState(false, new LinkedHashMap<>(state == null ? Collections.emptyMap() : state)));
        writeToDisk();
    }

    @Override
    public synchronized Map<String, Object> load(String phase) {
        var ps = store.get(phase);
        return ps == null ? null : Collections.unmodifiableMap(ps.state());
    }

    @Override
    public synchronized boolean hasCompleted(String phase) {
        var ps = store.get(phase);
        return ps != null && ps.completed();
    }

    @Override
    public synchronized void clear() {
        store.clear();
        writeToDisk();
    }

    public synchronized void markCompleted(String phase) {
        store.put(phase, new PhaseState(true, Collections.emptyMap()));
        writeToDisk();
    }

    @SuppressWarnings("unchecked")
    private void loadFromDisk() {
        if (!file.exists()) return;
        try {
            var data = (LinkedHashMap<String, Object>) mapper.readValue(file, LinkedHashMap.class);
            for (Entry<String, Object> rawEntry : data.entrySet()) {
                var key = rawEntry.getKey();
                var val = (Map<String, Object>) rawEntry.getValue();
                var completed = Boolean.TRUE.equals(val.get("completed"));
                var state = (Map<String, Object>) val.get("state");
                store.put(key, new PhaseState(completed, state == null ? Collections.emptyMap() : state));
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void writeToDisk() {
        try {
            file.getParentFile().mkdirs();
            var data = new LinkedHashMap<String, Object>();
            store.forEach((k, v) -> {
                var entry = new LinkedHashMap<String, Object>();
                entry.put("completed", v.completed());
                entry.put("state", v.state());
                data.put(k, entry);
            });
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        } catch (Exception ignored) {
        }
    }

    public boolean exists() {
        return file.exists();
    }
}
