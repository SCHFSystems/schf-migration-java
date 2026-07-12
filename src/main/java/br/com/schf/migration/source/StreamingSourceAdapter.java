package br.com.schf.migration.source;

public interface StreamingSourceAdapter extends SourceAdapter {
    void extractTo(RecordHandler handler, CheckpointStore checkpoints, ProgressTracker progress) throws Exception;
}
