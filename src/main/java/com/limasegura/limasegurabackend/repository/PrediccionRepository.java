package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Prediccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.Optional;

public interface PrediccionRepository extends JpaRepository<Prediccion,Long> {


    Optional<Prediccion> findByZonaIdAndDiaSemanaAndHora(Long zonaId, DayOfWeek diaSemana, Integer hora);

}
