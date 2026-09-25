package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.dto.request.ZoneCreateRequest;
import com.limasegura.limasegurabackend.dto.request.ZoneUpdateRequest;
import com.limasegura.limasegurabackend.dto.response.ZoneDetailResponse;
import com.limasegura.limasegurabackend.dto.response.ZoneResponse;
import com.limasegura.limasegurabackend.service.ZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    @PostMapping
    public ResponseEntity<ZoneResponse> create(@Valid @RequestBody ZoneCreateRequest request) {
        ZoneResponse created = zoneService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZoneDetailResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(zoneService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<ZoneResponse>> getAll(@RequestParam(required = false) String district) {
        if (district != null) {
            return ResponseEntity.ok(zoneService.getByDistrict(district));
        }
        return ResponseEntity.ok(zoneService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ZoneResponse> update(@PathVariable Long id, @Valid @RequestBody ZoneUpdateRequest request) {
        return ResponseEntity.ok(zoneService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        zoneService.delete(id);
        return ResponseEntity.noContent().build();
    }
}