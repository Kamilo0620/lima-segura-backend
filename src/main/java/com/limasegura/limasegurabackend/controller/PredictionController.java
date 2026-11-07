package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.model.Prediction;
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
    public ResponseEntity<Prediction> generate(
            @RequestParam Long zoneId,
            @RequestParam DayOfWeek dayOfWeek,
            @RequestParam Integer hour,
            @RequestParam Double score) {
        Prediction created = predictionService.generate(zoneId, dayOfWeek, hour, score);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<Prediction> getByZoneDayHour(
            @RequestParam Long zoneId,
            @RequestParam DayOfWeek dayOfWeek,
            @RequestParam Integer hour) {
        return predictionService.getByZoneDayHour(zoneId, dayOfWeek, hour)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}