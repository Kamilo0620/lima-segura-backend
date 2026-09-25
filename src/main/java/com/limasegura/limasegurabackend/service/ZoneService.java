package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.request.ZoneCreateRequest;
import com.limasegura.limasegurabackend.dto.request.ZoneUpdateRequest;
import com.limasegura.limasegurabackend.dto.response.ZoneDetailResponse;
import com.limasegura.limasegurabackend.dto.response.ZoneResponse;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.Zone;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import com.limasegura.limasegurabackend.repository.PredictionRepository;
import com.limasegura.limasegurabackend.repository.ReportRepository;
import com.limasegura.limasegurabackend.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final ModelMapper modelMapper;
    private final IncidentRepository incidentRepository;
    private final ReportRepository reportRepository;
    private final PredictionRepository predictionRepository;

    public ZoneResponse create(ZoneCreateRequest request) {
        Zone zone = modelMapper.map(request, Zone.class);
        Zone savedZone = zoneRepository.save(zone);
        return modelMapper.map(savedZone, ZoneResponse.class);
    }

    public ZoneDetailResponse getById(Long id) {
        Zone zone=zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zona no encontrada con id: " + id));
        ZoneDetailResponse response=modelMapper.map(zone,ZoneDetailResponse.class);
        response.setIncidentsCount(incidentRepository.countByZoneId(id));
        response.setReportsCount(reportRepository.countByZoneId(id));
        response.setPredictionsCount(predictionRepository.countByZoneId(id));
        return response;
    }

    public List<ZoneResponse> getAll() {
        return zoneRepository.findAll().stream()
                .map(zone->modelMapper.map(zone,ZoneResponse.class)).toList();
    }

    public List<ZoneResponse> getByDistrict(String district) {
        return zoneRepository.findByDistrict(district).stream()
                .map(zone->modelMapper.map(zone, ZoneResponse.class)).toList();
    }

    public ZoneResponse update(Long id, ZoneUpdateRequest request) {
        Zone existing = zoneRepository.findById(id)
                        .orElseThrow(()->new ResourceNotFoundException("Zona no encontrada con id: "+id));
        modelMapper.map(request,existing);
        Zone updatedZone = zoneRepository.save(existing);
        return modelMapper.map(updatedZone,ZoneResponse.class);
    }

    public void delete(Long id) {
        Zone existing =zoneRepository.findById(id)
                        .orElseThrow(()->new ResourceNotFoundException("Zona no encontrada con id: "+id));
        zoneRepository.delete(existing);
    }
}