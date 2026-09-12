package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.model.Report;
import com.limasegura.limasegurabackend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<Report> create(
            @RequestParam Long userId,
            @RequestParam Long zoneId,
            @RequestParam Long categoryId,
            @RequestParam String description,
            @RequestParam Double latitude,
            @RequestParam Double longitude) {
        Report created = reportService.create(userId, zoneId, categoryId, description, latitude, longitude);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Report> getById(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<Report>> getAll(
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Long userId) {
        if (zoneId != null) {
            return ResponseEntity.ok(reportService.getByZone(zoneId));
        }
        if (userId != null) {
            return ResponseEntity.ok(reportService.getByUser(userId));
        }
        return ResponseEntity.ok(reportService.getAll());
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long id, @RequestParam Long userId) {
        reportService.confirm(id, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reportService.delete(id);
        return ResponseEntity.noContent().build();
    }
}