# Privacy and Logging

## Principle

The SCHF migration pipeline must never expose real data (PII, financial records, credentials, or tokens) in logs, reports, version control, CI, or external services.

## Logged Information

Allowed in logs and reports:

| Field | Example | Notes |
|-------|---------|-------|
| entityType | `suppliers` | Entity type name |
| externalId hash | `a1b2c3d4...` | First 8 chars of SHA-256 of externalId |
| line number | `42` | Record position in stream |
| error code | `MISSING_REFERENCE` | Error classification code |
| quantity | `278` | Record count |
| duration | `1234ms` | Phase duration |
| status | `COMPLETED` | Phase status |
| correlationId | `uuid` | Extraction correlation ID |

## Prohibited

Never log or commit:

- Full record contents
- Full names
- CPF/CNPJ numbers
- Email addresses
- Complete financial descriptions
- Account numbers
- Bank names with account details
- Passwords or tokens
- Connection strings with credentials
- Source database file paths
- Real organization names
- Real user names

## Report Sanitization

The `ExtractionReport.toSanitizedMap()` method produces only:

- Total counts per entity type
- Rejected record counts (no content)
- Phase durations
- Bundle size
- Error counts (no record content)

## Log Format

Use structured logging with correlation IDs. Never log credential values.

## Enforcement

- Gitleaks runs on every commit.
- Reports must be reviewed before committing.
- Real data execution output stays on `D:\SCHF\_reports\local-only\`.
- The sprint report in `schf-workspace/audit/` contains only sanitized metadata.
