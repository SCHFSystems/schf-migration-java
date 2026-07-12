package br.com.schf.migration.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.schf.migration.source.SyntheticSourceAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundleComparerTest {

    @TempDir
    Path temp;

    @Test
    void identicalBundlesAreDetected() throws Exception {
        var left = temp.resolve("left.schf");
        var right = temp.resolve("right.schf");
        var dataset = new SyntheticSourceAdapter().readCanonical();
        new CanonicalBundleWriter().write(dataset, left);
        new CanonicalBundleWriter().write(dataset, right);
        var result = new BundleComparer().compare(left, right);
        assertThat(result.isIdentical()).isTrue();
    }

    @Test
    void differentBundlesShowChanges() throws Exception {
        var left = temp.resolve("left.schf");
        var right = temp.resolve("right.schf");
        new CanonicalBundleWriter().write(new SyntheticSourceAdapter().readCanonical(), left);
        var rightDataset = new SyntheticSourceAdapter().readCanonical();
        var mutable = new java.util.ArrayList<>(rightDataset.records().get("suppliers.ndjson"));
        mutable.clear();
        rightDataset.records().put("suppliers.ndjson", mutable);
        new CanonicalBundleWriter().write(rightDataset, right);
        var result = new BundleComparer().compare(left, right);
        assertThat(result.isIdentical()).isFalse();
        assertThat(result.addedCount()).isEqualTo(0);
        assertThat(result.removedCount()).isPositive();
    }

    @Test
    void compareReportsCounts() throws Exception {
        var left = temp.resolve("left.schf");
        var right = temp.resolve("right.schf");
        var dataset = new SyntheticSourceAdapter().readCanonical();
        new CanonicalBundleWriter().write(dataset, left);
        new CanonicalBundleWriter().write(dataset, right);
        var result = new BundleComparer().compare(left, right);
        assertThat(result.leftCounts()).containsKey("suppliers.ndjson");
        assertThat(result.rightCounts()).containsKey("suppliers.ndjson");
    }
}
