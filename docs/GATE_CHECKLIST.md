# Sprint 22H-A Gate Checklist

## Required to Mark PASS

- [x] commits enviados
- [ ] GitHub Actions SUCCESS
- [ ] mvn verify PASS
- [ ] Testcontainers Firebird PASS
- [ ] skipped 0
- [ ] Gitleaks PASS
- [ ] Graphify/fronteira arquitetural PASS
- [ ] nenhum dado real no CI
- [ ] relatorio atualizado
- [ ] Vault local atualizado
- [ ] repos limpos ou pendências antigas classificadas

## Decision

When all items above are checked, the gate changes to:

**Sprint 22H-A = PASS**
**Real data gate = READY_FOR_REAL_READ_ONLY_DRY_RUN**

## Next Steps After Gate

1. Inform Joao of the READY status.
2. Wait for explicit authorization.
3. Do NOT proceed without authorization.
4. Before executing the dry-run, present:
   - Data source (FDB or FBK)
   - Firebird version (local)
   - Output directory
   - Exact commands
   - Read-only controls
   - Time/space estimate
   - Cancellation plan
   - Files to be produced
