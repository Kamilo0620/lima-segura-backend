package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.request.IncidentCreateRequest;
import com.limasegura.limasegurabackend.dto.response.IncidentResponse;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.Category;
import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.Zone;
import com.limasegura.limasegurabackend.repository.CategoryRepository;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import com.limasegura.limasegurabackend.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final ModelMapper modelMapper;
    private final ZoneRepository zoneRepository;
    private final CategoryRepository categoryRepository;

    public IncidentResponse create(IncidentCreateRequest request) {
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Zona no encontrada con id: "+request.getZoneId()));
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(()->new ResourceNotFoundException("Categoria no encontrada con id: "+request.getCategoryId()));

        Incident incident=modelMapper.map(request,Incident.class);
        incident.setZone(zone); incident.setCategory(category);
        Incident savedIncident=incidentRepository.save(incident);
        return modelMapper.map(savedIncident, IncidentResponse.class);
    }

    public IncidentResponse getById(Long id) {
        Incident incident=incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incidente no encontrado con id: " + id));
        return modelMapper.map(incident, IncidentResponse.class);
    }

    public List<IncidentResponse> getAll() {
        return incidentRepository.findAll().stream()
                .map(incident->modelMapper.map(incident, IncidentResponse.class)).toList();
    }

    public List<IncidentResponse> getByZone(Long zoneId) {
        return incidentRepository.findByZoneId(zoneId).stream()
                .map(incident->modelMapper.map(incident, IncidentResponse.class)).toList();
    }

    public List<IncidentResponse> getByDateRange(LocalDate from, LocalDate to) {
        return incidentRepository.findByDateBetween(from, to).stream()
                .map(incident->modelMapper.map(incident, IncidentResponse.class)).toList();
    }

    public void delete(Long id) {
        Incident existing = incidentRepository.findById(id)
                        .orElseThrow(()->new ResourceNotFoundException("Incidente no encontrado con id: "+id));
        incidentRepository.delete(existing);
    }
}