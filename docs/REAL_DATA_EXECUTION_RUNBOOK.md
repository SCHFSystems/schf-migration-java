# Real Data Execution Runbook

## Authorization

**Do not execute any step in this document without explicit authorization from Joao.**

This runbook is for the authorized Phase 22H-B dry-run only.

## Prerequisites

1. Firebird 2.5+ running on the SGH server
2. Read-only access to `D:\SCHF_SECURE\SGH.FDB`
3. Environment variables configured on the local machine (never in Git)

## Step 1: Configure Environment

Create a local `.env` file in `D:\SCHF_SECURE\config\`:

```powershell
$env:SCHF_FB_URL="jdbc:firebirdsql://localhost:3050/D:\SCHF_SECURE\SGH.FDB"
$env:SCHF_FB_USER="READONLY_USER"
$env:SCHF_FB_PASSWORD="<password>"
$env:SCHF_SOURCE_INSTANCE_ID="sgh-prod-001"
$env:SCHF_MIGRATION_WORK_DIRECTORY="D:\SCHF\_runtime\migration-workbench"
$env:SCHF_MIGRATION_REPORT_DIRECTORY="D:\SCHF\_reports\local-only"
```

## Step 2: Validate Connection

```powershell
java -jar schf-migration-java.jar validate-connection
```

Expected: `Connection OK to jdbc:firebirdsql://...`

## Step 3: Inspect Schema

```powershell
java -jar schf-migration-java.jar inspect-schema > D:\SCHF\_reports\local-only\schema-inspection.json
```

## Step 4: Run Analysis

```powershell
java -jar schf-migration-java.jar analyze-source
```

## Step 5: Generate Bundle

```powershell
java -jar schf-migration-java.jar generate-bundle --source firebird --output D:\SCHF\_reports\bundle-output.schf
```

## Step 6: Validate Bundle

```powershell
java -jar schf-migration-java.jar validate-bundle --bundle D:\SCHF\_reports\bundle-output.schf
```

## Step 7: Print Summary

```powershell
java -jar schf-migration-java.jar print-summary --bundle D:\SCHF\_reports\bundle-output.schf
```

## Step 8: Compare with Previous Extraction

```powershell
java -jar schf-migration-java.jar compare-bundles --left D:\SCHF\_reports\bundle-v1.schf --right D:\SCHF\_reports\bundle-v2.schf
```

## Security Rules

- Never commit any output file from these steps.
- Output files stay in `D:\SCHF\_reports\local-only\`.
- Never expose credentials in shell history, logs, or reports.
- The bundle file must never leave the local machine.
- No data is sent to GitHub Actions, IA services, or external APIs.
- After the dry-run, the bundle must be deleted or moved to encrypted storage.
