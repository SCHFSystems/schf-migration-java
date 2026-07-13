package br.com.schf.migration.bundle;

import br.com.schf.migration.contract.BundleContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
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
            var version = manifest.path("bundleFormatVersion").asText();
            if (!BundleContract.FORMAT_VERSION.equals(version) && !BundleContract.FORMAT_VERSION_1_1.equals(version)) {
                throw new IllegalArgumentException("Unsupported bundle version: " + version);
            }
            var dataFiles = BundleContract.dataFilesForVersion(version);
            var checksums = parse(new String(files.get("bundle/checksums.sha256")));
            for (String name : dataFiles) {
                var path = "bundle/data/" + name;
                var data = files.get(path);
                if (data == null && BundleContract.FORMAT_VERSION.equals(version)) continue;
                if (data == null) throw new IllegalArgumentException("Missing data file: " + path);
                if (!CanonicalBundleWriter.sha256(data).equals(checksums.get(path))) throw new IllegalArgumentException("Checksum mismatch for " + path);
            }
        } catch (RuntimeException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid canonical bundle", ex); }
    }
    private Map<String, String> parse(String text) { var map = new LinkedHashMap<String, String>();
        for (String line : text.split("\\R")) if (!line.isBlank()) { var p = line.split("  ", 2); map.put(p[1], p[0]); } return map; }
}
