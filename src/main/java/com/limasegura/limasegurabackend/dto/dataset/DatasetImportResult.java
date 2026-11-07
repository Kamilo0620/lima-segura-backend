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
    private Integer errorRows;
    private List<String> errors;
}
