# Credentials Guide

## Principles

Credentials must only come from environment variables or a local ignored configuration file. Never store credentials in Git.

## Environment Variables

| Variable | Purpose | Required |
|----------|---------|----------|
| `SCHF_FB_URL` | Firebird JDBC URL | Yes |
| `SCHF_FB_USER` | Firebird username | Yes |
| `SCHF_FB_PASSWORD` | Firebird password | Yes |
| `SCHF_SOURCE_INSTANCE_ID` | Unique source identifier | Yes |

Fallback names: `SCHF_FIREBIRD_URL`, `SCHF_FIREBIRD_USER`, `SCHF_FIREBIRD_PASSWORD`.

## Local Config File

Create `D:\SCHF_SECURE\config\firebird.env`:
```powershell
$env:SCHF_FB_URL="jdbc:firebirdsql://localhost:3050/D:\SCHF_SECURE\SGH.FDB"
$env:SCHF_FB_USER="readonly_user"
$env:SCHF_FB_PASSWORD="your_password_here"
```

Load before running:
```powershell
Get-Content D:\SCHF_SECURE\config\firebird.env | ForEach-Object {
  $parts = $_ -split '=', 2
  Set-Item -Path "env:$($parts[0])" -Value $parts[1]
}
```

## Prohibited

- Never pass credentials as CLI arguments.
- Never hardcode credentials in source code.
- Never commit credentials to Git.
- Never log credentials.
- Never include credentials in reports, vault notes, or documentation.
- Never save credentials in shell history.

## CLI Argument Protection

The `MigrationCli` does not accept username/password/URL as arguments. All connection configuration comes from environment variables.

## CI

CI uses synthetic credentials only. No real data or real credentials are used in CI:
```yaml
env:
  SCHF_FB_URL: jdbc:firebirdsql://localhost:3050/SCHF_TEST
  SCHF_FB_USER: sysdba
  SCHF_FB_PASSWORD: schf_test_pwd
```
