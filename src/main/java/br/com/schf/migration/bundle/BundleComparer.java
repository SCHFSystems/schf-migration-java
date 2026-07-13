package br.com.schf.migration.bundle;

import br.com.schf.migration.contract.BundleContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

public class BundleComparer {

    private final ObjectMapper mapper = new ObjectMapper();

    public ComparisonResult compare(Path leftBundle, Path rightBundle) {
        try {
            var left = loadBundle(leftBundle);
            var right = loadBundle(rightBundle);
            var result = new ComparisonResult();

            var allEntityTypes = new LinkedHashSet<String>();
            allEntityTypes.addAll(left.keySet());
            allEntityTypes.addAll(right.keySet());

            for (String entityType : allEntityTypes) {
                var leftRecords = recordsByExternalId(left, entityType);
                var rightRecords = recordsByExternalId(right, entityType);
                for (var entry : leftRecords.entrySet()) {
                    if (!rightRecords.containsKey(entry.getKey())) {
                        result.removed(entityType, entry.getKey());
                    } else if (!semanticHash(entry.getValue()).equals(semanticHash(rightRecords.get(entry.getKey())))) {
                        result.changed(entityType, entry.getKey());
                    }
                }
                for (var entry : rightRecords.entrySet()) {
                    if (!leftRecords.containsKey(entry.getKey())) {
                        result.added(entityType, entry.getKey());
                    }
                }
            }
            result.setLeftCounts(countByType(left));
            result.setRightCounts(countByType(right));
            return result;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to compare bundles", ex);
        }
    }

    private static class LinkedHashSet<E> extends java.util.LinkedHashSet<E> {}

    private Map<String, List<Map<String, Object>>> loadBundle(Path bundle) throws Exception {
        var records = new LinkedHashMap<String, List<Map<String, Object>>>();
        try (var zip = new ZipInputStream(Files.newInputStream(bundle))) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                var name = entry.getName();
                if (name.startsWith("bundle/data/") && name.endsWith(".ndjson")) {
                    var entityType = name.substring("bundle/data/".length());
                    var list = new ArrayList<Map<String, Object>>();
                    var text = new String(zip.readAllBytes());
                    for (String line : text.split("\n")) {
                        if (!line.isBlank()) {
                            list.add(mapper.readValue(line, LinkedHashMap.class));
                        }
                    }
                    records.put(entityType, list);
                }
                entry = zip.getNextEntry();
            }
        }
        return records;
    }

    private Map<String, Map<String, Object>> recordsByExternalId(Map<String, List<Map<String, Object>>> bundle, String entityType) {
        var list = bundle.get(entityType);
        if (list == null) return Map.of();
        return list.stream().filter(r -> r.get("externalId") != null)
            .collect(Collectors.toMap(r -> r.get("externalId").toString(), r -> r,
                (a, b) -> { throw new IllegalArgumentException("Duplicate externalId in " + entityType); }));
    }

    private String semanticHash(Map<String, Object> record) {
        var filtered = new LinkedHashMap<String, Object>();
        record.forEach((k, v) -> {
            if (!"generatedAt".equals(k) && !"updatedAt".equals(k) && !"createdAt".equals(k)) {
                filtered.put(k, v);
            }
        });
        return CanonicalBundleWriter.sha256(toBytes(filtered));
    }

    private byte[] toBytes(Map<String, Object> map) {
        try { return mapper.writeValueAsBytes(map); } catch (Exception ex) { return new byte[0]; }
    }

    private Map<String, Long> countByType(Map<String, List<Map<String, Object>>> bundle) {
        var counts = new LinkedHashMap<String, Long>();
        bundle.forEach((k, v) -> counts.put(k, (long) v.size()));
        return counts;
    }

    public static class ComparisonResult {
        private final List<String> added = new ArrayList<>();
        private final List<String> removed = new ArrayList<>();
        private final List<String> changed = new ArrayList<>();
        private Map<String, Long> leftCounts;
        private Map<String, Long> rightCounts;

        void added(String type, String id) { added.add(type + ":" + id); }
        void removed(String type, String id) { removed.add(type + ":" + id); }
        void changed(String type, String id) { changed.add(type + ":" + id); }
        void setLeftCounts(Map<String, Long> c) { leftCounts = c; }
        void setRightCounts(Map<String, Long> c) { rightCounts = c; }

        public boolean isIdentical() { return added.isEmpty() && removed.isEmpty() && changed.isEmpty(); }
        public int addedCount() { return added.size(); }
        public int removedCount() { return removed.size(); }
        public int changedCount() { return changed.size(); }
        public Map<String, Long> leftCounts() { return leftCounts; }
        public Map<String, Long> rightCounts() { return rightCounts; }
    }
}
