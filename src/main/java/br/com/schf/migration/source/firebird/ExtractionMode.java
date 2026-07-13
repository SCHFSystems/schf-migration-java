package br.com.schf.migration.source.firebird;

public enum ExtractionMode {
    LIMITED_VALIDATION,
    FULL_EXTRACTION;

    public boolean isLimited() {
        return this == LIMITED_VALIDATION;
    }
}
