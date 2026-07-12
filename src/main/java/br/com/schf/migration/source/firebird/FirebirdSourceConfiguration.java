package br.com.schf.migration.source.firebird;

import java.nio.file.Path;

public record FirebirdSourceConfiguration(
    String connectionUrl,
    String username,
    String password,
    String sourceId,
    String sourceInstanceId,
    int fetchSize,
    int batchSize,
    Path workDirectory,
    Path reportDirectory
) {
    public static FirebirdSourceConfiguration fromEnvironment() {
        var url = env("SCHF_FB_URL");
        var user = env("SCHF_FB_USER");
        var pass = env("SCHF_FB_PASSWORD");
        if (url == null || user == null || pass == null) {
            url = env("SCHF_FIREBIRD_URL");
            user = env("SCHF_FIREBIRD_USER");
            pass = env("SCHF_FIREBIRD_PASSWORD");
        }
        if (url == null || user == null || pass == null) {
            throw new IllegalStateException("Firebird credentials not configured. Set SCHF_FB_URL, SCHF_FB_USER, SCHF_FB_PASSWORD or use a local config file.");
        }
        return new FirebirdSourceConfiguration(
            url, user, pass,
            envOrDefault("SCHF_SOURCE_ID", "firebird-sgh"),
            envOrDefault("SCHF_SOURCE_INSTANCE_ID", "unknown"),
            Integer.parseInt(envOrDefault("SCHF_MIGRATION_FETCH_SIZE", "5000")),
            Integer.parseInt(envOrDefault("SCHF_MIGRATION_BATCH_SIZE", "500")),
            Path.of(envOrDefault("SCHF_MIGRATION_WORK_DIRECTORY",
                System.getProperty("java.io.tmpdir") + "/schf-migration-workbench")),
            Path.of(envOrDefault("SCHF_MIGRATION_REPORT_DIRECTORY",
                System.getProperty("java.io.tmpdir") + "/schf-reports"))
        );
    }

    public boolean isValid() {
        return connectionUrl != null && !connectionUrl.isBlank()
            && username != null && !username.isBlank()
            && password != null && !password.isBlank()
            && sourceId != null && !sourceId.isBlank();
    }

    private static String env(String key) {
        var val = System.getenv(key);
        return val != null && !val.isBlank() ? val.trim() : null;
    }

    private static String envOrDefault(String key, String def) {
        var val = System.getenv(key);
        return val != null && !val.isBlank() ? val.trim() : def;
    }
}
