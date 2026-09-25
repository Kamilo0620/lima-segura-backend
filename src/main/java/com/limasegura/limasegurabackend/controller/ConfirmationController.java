package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.dto.response.ConfirmationResponse;
import com.limasegura.limasegurabackend.service.ConfirmationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/confirmations")
@RequiredArgsConstructor
public class ConfirmationController {

    private final ConfirmationService confirmationService;

    @GetMapping
    public ResponseEntity<List<ConfirmationResponse>> getByReport(@RequestParam Long reportId) {
        return ResponseEntity.ok(confirmationService.getByReport(reportId));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> countByReport(@RequestParam Long reportId) {
        long count = confirmationService.countByReport(reportId);
        return ResponseEntity.ok(Map.of("reportId", reportId, "count", count));
    }
}