package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.model.Prediction;
import com.limasegura.limasegurabackend.model.RiskLevel;
import com.limasegura.limasegurabackend.model.Zone;
import com.limasegura.limasegurabackend.repository.PredictionRepository;
import com.limasegura.limasegurabackend.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final ZoneRepository zoneRepository;

    public Prediction generate(Long zoneId, DayOfWeek dayOfWeek, Integer hour, Double score) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NoSuchElementException("Zona no encontrada con id: " + zoneId));

        Prediction prediction = new Prediction();
        prediction.setZone(zone);
        prediction.setDayOfWeek(dayOfWeek);
        prediction.setHour(hour);
        prediction.setScore(score);
        prediction.setRiskLevel(calculateRiskLevel(score));

        return predictionRepository.save(prediction);
    }

    public Optional<Prediction> getByZoneDayHour(Long zoneId, DayOfWeek dayOfWeek, Integer hour) {
        return predictionRepository.findByZoneIdAndDayOfWeekAndHour(zoneId, dayOfWeek, hour);
    }

    private RiskLevel calculateRiskLevel(Double score) {
        if (score >= 0.7) {
            return RiskLevel.HIGH;
        } else if (score >= 0.4) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }
}