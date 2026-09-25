package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.dto.request.PredictionCreateRequest;
import com.limasegura.limasegurabackend.dto.response.PredictionResponse;
import com.limasegura.limasegurabackend.service.PredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;

@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;

    @PostMapping
    public ResponseEntity<PredictionResponse> generate(PredictionCreateRequest request) {
        PredictionResponse created = predictionService.generate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PredictionResponse> getByZoneDayHour(
            @RequestParam Long zoneId,
            @RequestParam DayOfWeek dayOfWeek,
            @RequestParam Integer hour) {
        return predictionService.getByZoneDayHour(zoneId, dayOfWeek, hour)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}