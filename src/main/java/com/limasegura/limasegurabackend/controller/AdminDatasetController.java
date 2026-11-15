package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.dto.dataset.DatasetImportResult;
import com.limasegura.limasegurabackend.service.DatasetImportService;
import com.limasegura.limasegurabackend.service.OfficialSourcesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/datasets")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDatasetController {

    private final DatasetImportService datasetImportService;
    private final OfficialSourcesService officialSourcesService;

    @PostMapping("/import")
    public ResponseEntity<DatasetImportResult> importDataset(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(datasetImportService.importCsv(file));
    }

    @GetMapping("/official-sources/status")
    public ResponseEntity<OfficialSourcesService.OfficialSourcesStatusResponse> getOfficialSourcesStatus(
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(officialSourcesService.getOfficialSourcesStatus(refresh));
    }

    @PostMapping("/official-sources/sync-pnp")
    public ResponseEntity<DatasetImportResult> syncFilteredPnpBaseline(
            @RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(officialSourcesService.syncFilteredPnpBaseline(limit));
    }
}
