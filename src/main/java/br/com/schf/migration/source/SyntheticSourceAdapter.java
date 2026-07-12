package br.com.schf.migration.source;

import br.com.schf.migration.contract.BundleContract.Dataset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SyntheticSourceAdapter implements SourceAdapter {
    private static final UUID ORG = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SUPPLIER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID PAYABLE = UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Override public String id() { return "synthetic"; }
    @Override public Map<String, Long> analyze() { return readCanonical().records().entrySet().stream()
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> (long) e.getValue().size())); }

    @Override public Dataset readCanonical() {
        var records = new LinkedHashMap<String, List<Map<String, Object>>>();
        records.put("organizations.ndjson", List.of(Map.of("externalId", ORG, "code", "SYNTH", "name", "Synthetic Organization")));
        records.put("users.ndjson", List.of(Map.of("externalId", UUID.fromString("20000000-0000-0000-0000-000000000001"), "username", "synthetic-user", "email", "synthetic@example.invalid", "displayName", "Synthetic User", "active", true, "roleCodes", List.of("VIEWER"))));
        records.put("suppliers.ndjson", List.of(Map.of("externalId", SUPPLIER, "name", "Synthetic Supplier", "document", "SYNTHETIC", "email", "supplier@example.invalid", "phone", "0000", "active", true)));
        records.put("categories.ndjson", List.of(Map.of("externalId", CATEGORY, "name", "Synthetic Category", "type", "EXPENSE", "active", true)));
        records.put("financial-accounts.ndjson", List.of(Map.of("externalId", ACCOUNT, "name", "Synthetic Account", "type", "BANK", "bankName", "Synthetic Bank", "agency", "0001", "accountNumber", "00001", "active", true)));
        records.put("payables.ndjson", List.of(Map.of("externalId", PAYABLE, "supplierExternalId", SUPPLIER, "categoryExternalId", CATEGORY, "financialAccountExternalId", ACCOUNT, "description", "Synthetic payable", "documentNumber", "SYN-001", "issueDate", "2026-01-01", "dueDate", "2026-01-31", "amount", "123.45", "status", "OPEN")));
        records.put("payments.ndjson", List.of());
        return new Dataset(ORG, "SYNTHETIC", UUID.fromString("80000000-0000-0000-0000-000000000001"), true, records);
    }
}
