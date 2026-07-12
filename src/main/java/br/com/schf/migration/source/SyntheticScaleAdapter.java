package br.com.schf.migration.source;

import br.com.schf.migration.contract.BundleContract.Dataset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SyntheticScaleAdapter implements SourceAdapter {
    private final long supplierCount;
    private final long categoryCount;
    private final long accountCount;
    private final long payableCount;
    private final long userCount;
    private final UUID orgExternalId;
    private final UUID sourceInstanceId;

    public SyntheticScaleAdapter(long supplierCount, long categoryCount, long accountCount,
                                  long payableCount, long userCount) {
        this.supplierCount = supplierCount;
        this.categoryCount = categoryCount;
        this.accountCount = accountCount;
        this.payableCount = payableCount;
        this.userCount = userCount;
        this.orgExternalId = UUID.randomUUID();
        this.sourceInstanceId = UUID.randomUUID();
    }

    @Override
    public String id() { return "synthetic-scale"; }

    @Override
    public Map<String, Long> analyze() {
        var result = new LinkedHashMap<String, Long>();
        result.put("organizations", 1L);
        result.put("suppliers", supplierCount);
        result.put("categories", categoryCount);
        result.put("financial-accounts", accountCount);
        result.put("payables", payableCount);
        result.put("payments", 0L);
        result.put("users", userCount);
        return result;
    }

    @Override
    public Dataset readCanonical() {
        var records = new LinkedHashMap<String, List<Map<String, Object>>>();
        records.put("organizations.ndjson", List.of(org()));
        records.put("suppliers.ndjson", generate("S", supplierCount, this::supplier));
        records.put("categories.ndjson", generate("C", categoryCount, this::category));
        records.put("financial-accounts.ndjson", generate("A", accountCount, this::account));
        records.put("payables.ndjson", generate("P", payableCount, this::payable));
        records.put("payments.ndjson", List.of());
        records.put("users.ndjson", generate("U", userCount, this::user));
        return new Dataset(orgExternalId, "synthetic-scale", sourceInstanceId, true, records);
    }

    private Map<String, Object> org() {
        return Map.of("externalId", orgExternalId, "code", "SCALE", "name", "Scale Test Organization");
    }

    private Map<String, Object> supplier(long i) {
        return Map.of("externalId", ext("S", i), "name", "Supplier " + i, "document", String.format("%014d", i),
            "email", "supplier" + i + "@example.invalid", "active", true);
    }

    private Map<String, Object> category(long i) {
        return Map.of("externalId", ext("C", i), "name", "Category " + i, "type", "EXPENSE", "active", true);
    }

    private Map<String, Object> account(long i) {
        return Map.of("externalId", ext("A", i), "name", "Account " + i, "type", "BANK",
            "bankName", "Bank " + i, "active", true);
    }

    private Map<String, Object> payable(long i) {
        var supplierIdx = (i % Math.max(supplierCount, 1)) + 1;
        var catIdx = (i % Math.max(categoryCount, 1)) + 1;
        var accIdx = (i % Math.max(accountCount, 1)) + 1;
        var amount = String.format("%.2f", 100.0 + (i * 1.5) % 10000);
        var m = new LinkedHashMap<String, Object>();
        m.put("externalId", ext("P", i));
        m.put("supplierExternalId", ext("S", supplierIdx));
        m.put("categoryExternalId", ext("C", catIdx));
        m.put("financialAccountExternalId", ext("A", accIdx));
        m.put("description", "Scale payable " + i);
        m.put("documentNumber", "DOC-" + i);
        m.put("issueDate", "2026-01-01");
        m.put("dueDate", "2026-02-01");
        m.put("amount", amount);
        m.put("status", "OPEN");
        return m;
    }

    private Map<String, Object> user(long i) {
        return Map.of("externalId", ext("U", i), "username", "user" + i,
            "email", "user" + i + "@example.invalid", "displayName", "User " + i, "active", true, "roleCodes", List.of("VIEWER"));
    }

    private String ext(String prefix, long i) {
        return UUID.nameUUIDFromBytes((prefix + i).getBytes()).toString();
    }

    private List<Map<String, Object>> generate(String prefix, long count, java.util.function.LongFunction<Map<String, Object>> fn) {
        var list = new ArrayList<Map<String, Object>>();
        for (long i = 1; i <= count; i++) list.add(fn.apply(i));
        return list;
    }
}
