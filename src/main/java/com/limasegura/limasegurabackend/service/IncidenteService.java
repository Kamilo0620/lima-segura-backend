package com.limasegura.limasegurabackend.service;


import com.limasegura.limasegurabackend.model.Categoria;
import com.limasegura.limasegurabackend.model.Incidente;
import com.limasegura.limasegurabackend.model.Zona;
import com.limasegura.limasegurabackend.repository.CategoriaRepository;
import com.limasegura.limasegurabackend.repository.IncidenteRepository;
import com.limasegura.limasegurabackend.repository.ZonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor

public class IncidenteService {
    private final IncidenteRepository incidenteRepository;
    private final ZonaRepository zonaRepository;
    private final CategoriaRepository categoriaRepository;

    public Incidente crear(Long zonaId, Long categoriaId, String fuente, LocalDate fecha, Double latitud, Double longitud) {
        Zona zona = zonaRepository.findById(zonaId)
                .orElseThrow(() -> new NoSuchElementException("Zona no encontrada con id: " + zonaId));
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new NoSuchElementException("Categoria no encontrada con id: " + categoriaId));

        Incidente incidente = new Incidente();
        incidente.setZona(zona);
        incidente.setCategoria(categoria);
        incidente.setFuente(fuente);
        incidente.setFecha(fecha);
        incidente.setLatitud(latitud);
        incidente.setLongitud(longitud);

        return incidenteRepository.save(incidente);
    }

    public Incidente obtenerPorId(Long id) {
        return incidenteRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Incidente no encontrado con id: " + id));
    }

    public List<Incidente> obtenerTodos() {
        return incidenteRepository.findAll();
    }

    public List<Incidente> obtenerPorZona(Long zonaId) {
        return incidenteRepository.findByZonaId(zonaId);
    }

    public List<Incidente> obtenerPorRangoFechas(LocalDate desde, LocalDate hasta) {
        return incidenteRepository.findByFechaBetween(desde, hasta);
    }

    public void eliminar(Long id) {
        Incidente existente = obtenerPorId(id);
        incidenteRepository.delete(existente);
    }




}
