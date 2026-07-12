package br.com.schf.migration.source;

import java.time.Duration;

public interface ProgressTracker {
    void phaseStarted(String phase, long totalEstimate);
    void recordsProcessed(String phase, long count);
    void phaseCompleted(String phase);
    void reportError(String phase, String entityType, String externalId, String errorCode, String details);
    boolean isCancelled();
    Duration elapsed();
    long processedCount(String phase);
}
