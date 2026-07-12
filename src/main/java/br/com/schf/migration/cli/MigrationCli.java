package br.com.schf.migration.cli;

import br.com.schf.migration.bundle.CanonicalBundleValidator;
import br.com.schf.migration.bundle.CanonicalBundleWriter;
import br.com.schf.migration.source.SourceAdapter;
import br.com.schf.migration.source.SyntheticSourceAdapter;
import java.nio.file.Path;

public final class MigrationCli {
    private MigrationCli() {}
    public static void main(String[] args) {
        if (args.length == 0) { usage(); return; }
        try {
            switch (args[0]) {
                case "analyze" -> System.out.println(adapter(value(args, "--source")).analyze());
                case "generate-bundle" -> new CanonicalBundleWriter().write(
                    adapter(value(args, "--source")).readCanonical(), Path.of(value(args, "--output")));
                case "validate-bundle" -> new CanonicalBundleValidator().validate(Path.of(value(args, "--bundle")));
                default -> usage();
            }
        } catch (RuntimeException ex) { System.err.println("Command failed with sanitized code MIGRATION_COMMAND_FAILED"); System.exit(2); }
    }
    private static SourceAdapter adapter(String id) { if ("synthetic".equals(id)) return new SyntheticSourceAdapter(); throw new IllegalArgumentException("Adapter unavailable"); }
    private static String value(String[] args, String option) { for (int i=0;i<args.length-1;i++) if (option.equals(args[i])) return args[i+1]; throw new IllegalArgumentException("Missing option"); }
    private static void usage() { System.out.println("Commands: analyze | generate-bundle | validate-bundle (synthetic adapter available)"); }
}
