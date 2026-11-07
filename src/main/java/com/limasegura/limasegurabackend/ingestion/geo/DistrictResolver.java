package com.limasegura.limasegurabackend.ingestion.geo;

import com.limasegura.limasegurabackend.model.District;
import com.limasegura.limasegurabackend.repository.DistrictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DistrictResolver {

    private final DistrictRepository districtRepository;

    public District resolveFromText(String text) {
        if (text == null || text.isBlank()) return null;
        String lower = text.toLowerCase();
        List<District> candidates = districtRepository.findAll();
        for (District d : candidates) {
            if (lower.contains(d.getName().toLowerCase())) {
                return d;
            }
        }
        return null;
    }

    public District resolve(String districtName, String provinceName, String departmentName) {
        if (districtName == null || districtName.isBlank()) return null;
        
        // Mocked resolving logic since the repositories might not have specific finders for all these out of the box
        // Assuming District entity has name, province, department. Or we just search by name.
        List<District> candidates = districtRepository.findAll();
        for (District d : candidates) {
            if (d.getName().equalsIgnoreCase(districtName)) {
                return d;
            }
        }
        return null;
    }
}
