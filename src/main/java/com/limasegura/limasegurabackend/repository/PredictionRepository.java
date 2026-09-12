package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.Optional;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    Optional<Prediction> findByZoneIdAndDayOfWeekAndHour(Long zoneId, DayOfWeek dayOfWeek, Integer hour);
}