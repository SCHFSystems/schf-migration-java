package br.com.schf.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.schf.migration.bundle.CanonicalBundleValidator;
import br.com.schf.migration.bundle.CanonicalBundleWriter;
import br.com.schf.migration.source.SyntheticSourceAdapter;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticPipelineTest {
    @TempDir java.nio.file.Path temp;
    @Test void generatesAndValidatesSyntheticBundle() throws Exception {
        var output = temp.resolve("synthetic.schf");
        new CanonicalBundleWriter().write(new SyntheticSourceAdapter().readCanonical(), output);
        new CanonicalBundleValidator().validate(output);
        assertThat(Files.size(output)).isPositive();
    }
    @Test void syntheticAnalysisContainsCanonicalCounts() {
        assertThat(new SyntheticSourceAdapter().analyze()).containsEntry("suppliers.ndjson", 1L);
    }
    @Test void invalidBundleFailsCleanly() throws Exception {
        var output = temp.resolve("invalid.schf"); Files.writeString(output, "not-a-bundle");
        assertThatThrownBy(() -> new CanonicalBundleValidator().validate(output)).isInstanceOf(IllegalArgumentException.class);
    }
}
