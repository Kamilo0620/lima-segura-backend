package com.limasegura.limasegurabackend.dto.dataset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetImportResult {
    private Integer totalRows;
    private Integer importedRows;
    private Integer filteredOutRows;
    private Integer errorRows;
    private Integer importedCount;
    private Integer skippedCount;
    private String message;
    private List<String> errors;
}
