package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.request.PredictionCreateRequest;
import com.limasegura.limasegurabackend.dto.response.PredictionResponse;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.Prediction;
import com.limasegura.limasegurabackend.model.RiskLevel;
import com.limasegura.limasegurabackend.model.Zone;
import com.limasegura.limasegurabackend.repository.PredictionRepository;
import com.limasegura.limasegurabackend.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final ModelMapper modelMapper;
    private final ZoneRepository zoneRepository;

    public PredictionResponse generate(PredictionCreateRequest request) {
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Zona no encontrada con id: "+request.getZoneId()));
        Prediction prediction=modelMapper.map(request,Prediction.class);
        prediction.setZone(zone);   prediction.setGeneratedAt(LocalDateTime.now());
        prediction.setRiskLevel(calculateRiskLevel(request.getScore()));
        Prediction savedPrediction=predictionRepository.save(prediction);
        return modelMapper.map(savedPrediction, PredictionResponse.class);
    }

    public Optional<PredictionResponse> getByZoneDayHour(Long zoneId, DayOfWeek dayOfWeek, Integer hour) {
        return predictionRepository.findByZoneIdAndDayOfWeekAndHour(zoneId, dayOfWeek, hour)
                .map(prediction -> modelMapper.map(prediction, PredictionResponse.class));
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