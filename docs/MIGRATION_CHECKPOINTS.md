# Migration Checkpoints

## Purpose

Checkpoints allow the extraction process to be interrupted and resumed without losing progress. This is critical for large databases where extraction may take hours.

## Implementation

Checkpoints are stored in a JSON file at `{workDirectory}/extraction-checkpoints.json`.

### Format

```json
{
  "organizations": { "completed": true, "state": {} },
  "users": { "completed": true, "state": {} },
  "suppliers": { "completed": true, "state": {} },
  "categories": { "completed": false, "state": { "lastCount": 150 } },
  "financial-accounts": { "completed": false, "state": {} },
  "payables": { "completed": false, "state": {} },
  "payments": { "completed": false, "state": {} }
}
```

### Behavior

1. Before extracting an entity type, the adapter checks `CheckpointStore.hasCompleted(type)`.
2. If completed, the entity type is skipped.
3. During extraction, state is saved every `batchSize` records.
4. After extraction, the phase is marked as completed.
5. On resume with `--resume`, the adapter skips all completed phases and continues from the first incomplete one.

## Commands

```bash
# Start fresh extraction
java -jar schf-migration-java.jar generate-bundle --source firebird --output bundle.schf

# Resume from last checkpoint
java -jar schf-migration-java.jar resume-generation --output bundle.schf

# Clear checkpoints by deleting the work directory
```

## Determinism

External IDs are deterministic (based on `sourceInstanceId + entityType + legacyPK`). This means:
- A fresh extraction produces the same external IDs as a resumed one.
- Bundle comparison between two extractions of the same source will report identical records.
