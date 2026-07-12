package br.com.schf.migration.bundle;

import br.com.schf.migration.contract.BundleContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

public class CanonicalBundleValidator {
    public void validate(Path bundle) {
        try (var zip = new ZipInputStream(Files.newInputStream(bundle))) {
            var files = new LinkedHashMap<String, byte[]>();
            var entry = zip.getNextEntry();
            while (entry != null) { files.put(entry.getName(), zip.readAllBytes()); entry = zip.getNextEntry(); }
            var mapper = new ObjectMapper();
            var manifest = mapper.readTree(files.get("bundle/manifest.json"));
            if (!"1.0".equals(manifest.path("bundleFormatVersion").asText())) throw new IllegalArgumentException("Unsupported bundle version");
            var checksums = parse(new String(files.get("bundle/checksums.sha256")));
            for (String name : BundleContract.DATA_FILES) {
                var path = "bundle/data/" + name;
                if (!CanonicalBundleWriter.sha256(files.get(path)).equals(checksums.get(path))) throw new IllegalArgumentException("Checksum mismatch");
            }
        } catch (RuntimeException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid canonical bundle", ex); }
    }
    private Map<String, String> parse(String text) { var map = new LinkedHashMap<String, String>();
        for (String line : text.split("\\R")) if (!line.isBlank()) { var p = line.split("  ", 2); map.put(p[1], p[0]); } return map; }
}
