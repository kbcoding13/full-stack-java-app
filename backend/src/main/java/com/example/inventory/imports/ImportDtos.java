package com.example.inventory.imports;

import java.util.List;

public final class ImportDtos {

    private ImportDtos() {}

    /**
     * Outcome of a CSV import. Rows are validated independently: a bad row is reported and
     * skipped rather than failing the whole file, so a 500-row upload with two typos still
     * lands 498 products.
     */
    public record ImportResult(
            String objectKey,
            int totalRows,
            int created,
            int updated,
            int skipped,
            List<RowError> errors) {}

    /** One-based line number as it appears in the file, so it matches what the user sees. */
    public record RowError(int line, String sku, String message) {}

    public record ExportResult(String objectKey, String downloadUrl, int rowCount, long expiresInSeconds) {}
}
