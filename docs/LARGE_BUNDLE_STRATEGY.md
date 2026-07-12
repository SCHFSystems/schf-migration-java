# Large Bundle Strategy

## Problem

The legacy SGH database is approximately 1.11 GB with millions of rows across key tables (13K+ payables, 278 suppliers, etc.). Loading all records into memory is not feasible.

## Solution: Streaming Extraction

The `FirebirdSourceAdapter` uses JDBC streaming with the following approach:

1. **Fetch size**: Configurable via `SCHF_MIGRATION_FETCH_SIZE` (default: 5000). Prevents the driver from loading all results into memory.

2. **Entity-level batching**: Records are processed one entity type at a time. Each entity's records are streamed, mapped, and written to the corresponding NDJSON file in the ZIP bundle.

3. **Per-entity NDJSON**: Each entity type (suppliers, categories, payables, etc.) gets its own NDJSON file. This allows independent streaming and checkpointing.

4. **Checkpoints**: After each batch (configurable via `SCHF_MIGRATION_BATCH_SIZE`, default: 500), the current progress is persisted. If the extraction is interrupted, it can resume from the last completed entity type.

5. **Deterministic external IDs**: External IDs are derived from `sourceInstanceId + entityType + legacyPK`. This guarantees reproducibility across extraction runs.

## Memory Limits

| Setting | Default | Description |
|---------|---------|-------------|
| `SCHF_MIGRATION_FETCH_SIZE` | 5000 | JDBC cursor fetch size |
| `SCHF_MIGRATION_BATCH_SIZE` | 500 | Records between checkpoint writes |
| `SCHF_MIGRATION_WORK_DIRECTORY` | tmpdir | Checkpoint and temp file location |

No single entity type should exceed available heap memory because each record is mapped, written to the NDJSON stream, and discarded.

## Cancellation

The `ProgressTracker.isCancelled()` method allows cooperative cancellation. The adapter checks this flag between records and stops processing if cancelled. Checkpoints preserve progress up to the last completed entity type.
