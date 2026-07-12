package br.com.schf.migration.source;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExtractionReport {
    private final Map<String, PhaseMetrics> phases = new LinkedHashMap<>();
    private long totalRead;
    private long totalNormalized;
    private long totalRejected;
    private long bundleSizeBytes;

    public void record(String phase, long read, long normalized, long rejected, long durationMs) {
        phases.put(phase, new PhaseMetrics(read, normalized, rejected, durationMs));
        totalRead += read;
        totalNormalized += normalized;
        totalRejected += rejected;
    }

    public void bundleSize(long bytes) { this.bundleSizeBytes = bytes; }
    public Map<String, PhaseMetrics> phases() { return phases; }
    public long totalRead() { return totalRead; }
    public long totalNormalized() { return totalNormalized; }
    public long totalRejected() { return totalRejected; }
    public long bundleSizeBytes() { return bundleSizeBytes; }

    public record PhaseMetrics(long read, long normalized, long rejected, long durationMs) {}

    public Map<String, Object> toSanitizedMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("totalRead", totalRead);
        map.put("totalNormalized", totalNormalized);
        map.put("totalRejected", totalRejected);
        map.put("bundleSizeBytes", bundleSizeBytes);
        var phaseMap = new LinkedHashMap<String, Object>();
        phases.forEach((k, v) -> phaseMap.put(k, Map.of("read", v.read(), "normalized", v.normalized(),
            "rejected", v.rejected(), "durationMs", v.durationMs())));
        map.put("phases", phaseMap);
        return map;
    }
}
