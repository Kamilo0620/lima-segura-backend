package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.model.*;
import com.limasegura.limasegurabackend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int CONFIRMATIONS_TO_VALIDATE = 3;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final CategoryRepository categoryRepository;
    private final ConfirmationRepository confirmationRepository;

    public Report create(Long userId, Long zoneId, Long categoryId, String description, Double latitude, Double longitude) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + userId));
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NoSuchElementException("Zona no encontrada con id: " + zoneId));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NoSuchElementException("Categoria no encontrada con id: " + categoryId));

        Report report = new Report();
        report.setUser(user);
        report.setZone(zone);
        report.setCategory(category);
        report.setDescription(description);
        report.setLatitude(latitude);
        report.setLongitude(longitude);

        return reportRepository.save(report);
    }

    public Report getById(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Reporte no encontrado con id: " + id));
    }

    public List<Report> getAll() {
        return reportRepository.findAll();
    }

    public List<Report> getByZone(Long zoneId) {
        return reportRepository.findByZoneId(zoneId);
    }

    public List<Report> getByUser(Long userId) {
        return reportRepository.findByUserId(userId);
    }

    public void confirm(Long reportId, Long userId) {
        Report report = getById(reportId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con id: " + userId));

        boolean alreadyConfirmed = confirmationRepository.findByUserIdAndReportId(userId, reportId).isPresent();
        if (alreadyConfirmed) {
            throw new IllegalStateException("Este usuario ya confirmo este reporte");
        }

        Confirmation confirmation = new Confirmation();
        confirmation.setUser(user);
        confirmation.setReport(report);
        confirmationRepository.save(confirmation);

        long totalConfirmations = confirmationRepository.countByReportId(reportId);
        if (totalConfirmations >= CONFIRMATIONS_TO_VALIDATE) {
            report.setStatus(ReportStatus.VALIDATED);
            reportRepository.save(report);
        }
    }

    public void delete(Long id) {
        Report existing = getById(id);
        reportRepository.delete(existing);
    }
}