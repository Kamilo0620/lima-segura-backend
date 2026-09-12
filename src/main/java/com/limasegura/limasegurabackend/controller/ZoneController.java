package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.model.Zone;
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
    public ResponseEntity<Zone> create(@Valid @RequestBody Zone zone) {
        Zone created = zoneService.create(zone);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Zone> getById(@PathVariable Long id) {
        return ResponseEntity.ok(zoneService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<Zone>> getAll(@RequestParam(required = false) String district) {
        if (district != null) {
            return ResponseEntity.ok(zoneService.getByDistrict(district));
        }
        return ResponseEntity.ok(zoneService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Zone> update(@PathVariable Long id, @Valid @RequestBody Zone zone) {
        return ResponseEntity.ok(zoneService.update(id, zone));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        zoneService.delete(id);
        return ResponseEntity.noContent().build();
    }
}