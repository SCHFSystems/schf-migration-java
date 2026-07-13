package br.com.schf.migration.source.firebird;

public enum SourceProfile {
    SYNTHETIC,
    SGH_FIREBIRD_25;

    public boolean isSgh() {
        return this == SGH_FIREBIRD_25;
    }
}
