package br.com.schf.migration.source;

import br.com.schf.migration.contract.BundleContract.Dataset;
import java.util.Map;

public interface SourceAdapter {
    String id();
    Map<String, Long> analyze();
    Dataset readCanonical();
}
