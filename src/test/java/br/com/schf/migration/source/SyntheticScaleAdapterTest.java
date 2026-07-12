package br.com.schf.migration.source;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.schf.migration.bundle.CanonicalBundleValidator;
import br.com.schf.migration.bundle.CanonicalBundleWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticScaleAdapterTest {

    @TempDir
    Path temp;

    @Test
    void smallScaleGeneratesCorrectCounts() {
        var adapter = new SyntheticScaleAdapter(100, 50, 10, 500, 20);
        var analysis = adapter.analyze();
        assertThat(analysis.get("suppliers")).isEqualTo(100);
        assertThat(analysis.get("categories")).isEqualTo(50);
        assertThat(analysis.get("financial-accounts")).isEqualTo(10);
        assertThat(analysis.get("payables")).isEqualTo(500);
        assertThat(analysis.get("users")).isEqualTo(20);
    }

    @Test
    void smallScaleBundleIsValidAndWithinSize() throws Exception {
        var output = temp.resolve("scale-100.schf");
        var adapter = new SyntheticScaleAdapter(100, 50, 20, 500, 30);
        new CanonicalBundleWriter().write(adapter.readCanonical(), output);
        new CanonicalBundleValidator().validate(output);
        var size = Files.size(output);
        assertThat(size).isPositive();
        assertThat(size).isLessThan(10_000_000);
    }

    @Test
    void mediumScaleBundle10000Records() throws Exception {
        var output = temp.resolve("scale-10k.schf");
        var adapter = new SyntheticScaleAdapter(1000, 500, 100, 5000, 200);
        new CanonicalBundleWriter().write(adapter.readCanonical(), output);
        new CanonicalBundleValidator().validate(output);
        var size = Files.size(output);
        assertThat(size).isPositive();
        assertThat(size).isLessThan(100_000_000);
    }

    @Test
    void externalIdsAreDeterministic() {
        var a1 = new SyntheticScaleAdapter(100, 50, 10, 500, 20);
        var a2 = new SyntheticScaleAdapter(100, 50, 10, 500, 20);
        var d1 = a1.readCanonical();
        var d2 = a2.readCanonical();
        var s1 = d1.records().get("suppliers.ndjson");
        var s2 = d2.records().get("suppliers.ndjson");
        for (int i = 0; i < s1.size(); i++) {
            assertThat(s1.get(i).get("externalId")).isEqualTo(s2.get(i).get("externalId"));
        }
    }

    @Test
    void largeScale100kRecordsDoesNotCrash() throws Exception {
        var output = temp.resolve("scale-100k.schf");
        var adapter = new SyntheticScaleAdapter(5000, 2000, 500, 50000, 1000);
        new CanonicalBundleWriter().write(adapter.readCanonical(), output);
        new CanonicalBundleValidator().validate(output);
        var size = Files.size(output);
        assertThat(size).isPositive();
    }
}
