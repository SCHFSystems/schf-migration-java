# Dry-Run Environment Setup

## Local Environment

Do not connect to the company SGH server directly unless authorized.

Use a local isolated environment:

| Component | Source | Location |
|-----------|--------|----------|
| Firebird database | Copy/backup from `D:\SCHF_SECURE\` | Local container or VM |
| Runtime data | — | `D:\SCHF\_runtime\migration-workbench\` |
| Reports | — | `D:\SCHF\_reports\local-only\` |

## Firebird Local Setup Options

1. **Docker container** (preferred):
   ```bash
   docker run -d --name sgh-local \
     -e FIREBIRD_DATABASE=SGH \
     -e FIREBIRD_DATABASE_PASSWORD=local_pwd \
     -v D:\SCHF_SECURE\SGH.FDB:/var/lib/firebird/data/SGH.FDB:ro \
     -p 3050:3050 \
     firebirdsql/firebird:5
   ```

2. **VM local**: Install Firebird 2.5+ in a local VM, copy `SGH.FDB`.

3. **Instalação local**: Install Firebird 2.5 directly (not recommended for isolation).

## Read-Only Mount

Mount the real database as read-only (`:ro` in Docker). The adapter also uses `conn.setReadOnly(true)` and `SET TRANSACTION NO WRITE`.

## Network Isolation

The Firebird container should not be exposed to the local network unless required. Bind to `127.0.0.1` only:
```bash
-p 127.0.0.1:3050:3050
```

## Access Control

- The local Firebird instance must not be accessible from other machines.
- Use a strong password even in local environment.
- Delete the database copy after the dry-run is complete.
