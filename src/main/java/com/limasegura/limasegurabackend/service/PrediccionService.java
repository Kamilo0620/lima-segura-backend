package com.limasegura.limasegurabackend.service;


import com.limasegura.limasegurabackend.model.NivelRiesgo;
import com.limasegura.limasegurabackend.model.Prediccion;
import com.limasegura.limasegurabackend.model.Zona;
import com.limasegura.limasegurabackend.repository.PrediccionRepository;
import com.limasegura.limasegurabackend.repository.ZonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PrediccionService {
    private final PrediccionRepository prediccionRepository;
    private final ZonaRepository zonaRepository;

    public Prediccion generar(Long zonaId, DayOfWeek diaSemana, Integer hora, Double score) {
        Zona zona = zonaRepository.findById(zonaId)
                .orElseThrow(() -> new NoSuchElementException("Zona no encontrada con id: " + zonaId));

        Prediccion prediccion = new Prediccion();
        prediccion.setZona(zona);
        prediccion.setDiaSemana(diaSemana);
        prediccion.setHora(hora);
        prediccion.setScore(score);
        prediccion.setNivelRiesgo(calcularNivelRiesgo(score));

        return prediccionRepository.save(prediccion);
    }

    public Optional<Prediccion> obtenerPorZonaDiaHora(Long zonaId, DayOfWeek diaSemana, Integer hora) {
        return prediccionRepository.findByZonaIdAndDiaSemanaAndHora(zonaId, diaSemana, hora);
    }

    private NivelRiesgo calcularNivelRiesgo(Double score) {
        if (score >= 0.7) {
            return NivelRiesgo.ALTO;
        } else if (score >= 0.4) {
            return NivelRiesgo.MEDIO;
        } else {
            return NivelRiesgo.BAJO;
        }
    }




}
