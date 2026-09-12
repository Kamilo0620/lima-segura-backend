package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    @PostMapping
    public ResponseEntity<Incident> create(
            @RequestParam Long zoneId,
            @RequestParam Long categoryId,
            @RequestParam String source,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Double latitude,
            @RequestParam Double longitude) {
        Incident created = incidentService.create(zoneId, categoryId, source, date, latitude, longitude);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Incident> getById(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<Incident>> getAll(
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (zoneId != null) {
            return ResponseEntity.ok(incidentService.getByZone(zoneId));
        }
        if (from != null && to != null) {
            return ResponseEntity.ok(incidentService.getByDateRange(from, to));
        }
        return ResponseEntity.ok(incidentService.getAll());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        incidentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}