# Firebird Source Adapter

## Overview

The `FirebirdSourceAdapter` reads records from a Firebird 2.5+ database and produces canonical SCHF records for bundle generation. It implements `StreamingSourceAdapter` for on-disk streaming and `SourceAdapter` for analyze-only operations.

## Architecture

```
Firebird DB (SGH)
  → FirebirdSourceAdapter (read-only JDBC)
  → RowMapper + LegacyMapper per entity
  → RecordHandler (streaming NDJSON)
  → CanonicalBundleStreamingHandler
  → .schf bundle
```

## Configuration

All credentials come from environment variables or a local config file — never from Git, CLI arguments, or hardcoded values.

| Variable | Description |
|----------|-------------|
| `SCHF_FB_URL` | JDBC URL (e.g., `jdbc:firebirdsql://host:3050/path/to/SGH.FDB`) |
| `SCHF_FB_USER` | Database username |
| `SCHF_FB_PASSWORD` | Database password |
| `SCHF_SOURCE_ID` | Adapter identifier (default: `firebird-sgh`) |
| `SCHF_SOURCE_INSTANCE_ID` | Unique source instance ID for deterministic external IDs |
| `SCHF_MIGRATION_FETCH_SIZE` | JDBC fetch size (default: 5000) |
| `SCHF_MIGRATION_BATCH_SIZE` | Checkpoint batch size (default: 500) |
| `SCHF_MIGRATION_WORK_DIRECTORY` | Work directory for checkpoints (default: tmpdir) |
| `SCHF_MIGRATION_REPORT_DIRECTORY` | Report output directory (default: tmpdir) |

## Read-Only Guarantee

Connections are opened with `conn.setReadOnly(true)` and the Firebird transaction is set to `NO WRITE`. The adapter performs no INSERT, UPDATE, DELETE, ALTER, CREATE, or stored procedure execution that modifies data.

## Commands

```bash
# Validate connection
java -jar schf-migration-java.jar validate-connection

# Inspect schema
java -jar schf-migration-java.jar inspect-schema

# Analyze record counts
java -jar schf-migration-java.jar analyze-source

# Generate bundle (streaming)
java -jar schf-migration-java.jar generate-bundle --source firebird --output bundle.schf

# Resume from checkpoint
java -jar schf-migration-java.jar resume-generation --output bundle.schf
```
