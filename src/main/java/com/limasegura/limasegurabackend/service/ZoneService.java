package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.model.Zone;
import com.limasegura.limasegurabackend.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    public Zone create(Zone zone) {
        return zoneRepository.save(zone);
    }

    public Zone getById(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Zona no encontrada con id: " + id));
    }

    public List<Zone> getAll() {
        return zoneRepository.findAll();
    }

    public List<Zone> getByDistrict(String district) {
        return zoneRepository.findByDistrict(district);
    }

    public Zone update(Long id, Zone zone) {
        Zone existing = getById(id);
        existing.setName(zone.getName());
        existing.setDistrict(zone.getDistrict());
        existing.setLatitude(zone.getLatitude());
        existing.setLongitude(zone.getLongitude());
        return zoneRepository.save(existing);
    }

    public void delete(Long id) {
        Zone existing = getById(id);
        zoneRepository.delete(existing);
    }
}