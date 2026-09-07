package com.casemap.hierarchy.produce;

public record ImportFileMetadata(
        String fileName,
        String format,
        long size,
        String sha256,
        Integer rowCount,
        String sheetName,
        Integer sheetCount
) {
}
