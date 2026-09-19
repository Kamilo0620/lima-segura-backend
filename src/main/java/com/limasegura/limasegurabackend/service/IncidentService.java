package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.Category;
import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.Zone;
import com.limasegura.limasegurabackend.repository.CategoryRepository;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import com.limasegura.limasegurabackend.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final ZoneRepository zoneRepository;
    private final CategoryRepository categoryRepository;

    public Incident create(Long zoneId, Long categoryId, String source, LocalDate date, Double latitude, Double longitude) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zona no encontrada con id: " + zoneId));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada con id: " + categoryId));

        Incident incident = new Incident();
        incident.setZone(zone);
        incident.setCategory(category);
        incident.setSource(source);
        incident.setDate(date);
        incident.setLatitude(latitude);
        incident.setLongitude(longitude);

        return incidentRepository.save(incident);
    }

    public Incident getById(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incidente no encontrado con id: " + id));
    }

    public List<Incident> getAll() {
        return incidentRepository.findAll();
    }

    public List<Incident> getByZone(Long zoneId) {
        return incidentRepository.findByZoneId(zoneId);
    }

    public List<Incident> getByDateRange(LocalDate from, LocalDate to) {
        return incidentRepository.findByDateBetween(from, to);
    }

    public void delete(Long id) {
        Incident existing = getById(id);
        incidentRepository.delete(existing);
    }
}