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
import com.limasegura.limasegurabackend.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    public PageResponse<IncidentResponse> getAll(Long zoneId, LocalDate from, LocalDate to, Pageable pageable) {
        Page<Incident> page;
        if (zoneId != null) {
            page = incidentRepository.findByZoneId(zoneId, pageable);
        } else if (from != null && to != null) {
            page = incidentRepository.findByDateBetween(from, to, pageable);
        } else {
            page = incidentRepository.findAll(pageable);
        }
        return PageResponse.from(page.map(incident -> modelMapper.map(incident, IncidentResponse.class)));
    }

    public void delete(Long id) {
        Incident existing = incidentRepository.findById(id)
                        .orElseThrow(()->new ResourceNotFoundException("Incidente no encontrado con id: "+id));
        incidentRepository.delete(existing);
    }
}