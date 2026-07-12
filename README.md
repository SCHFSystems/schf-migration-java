# SCHF Migration Java

Framework-independent Java 21 pipeline that converts source-adapter output into the canonical SCHF bundle. It does not connect to the SCHF database and contains no source-system-specific connector.

`SourceAdapter -> canonical records -> .schf bundle -> schf-core-java importer`

Build with `mvn verify`.

```bash
java -jar target/schf-migration-java.jar analyze --source synthetic
java -jar target/schf-migration-java.jar generate-bundle --source synthetic --output synthetic.schf
java -jar target/schf-migration-java.jar validate-bundle --bundle synthetic.schf
```

Real source adapters must live in connector modules. No real source is executed in Sprint 22F.
