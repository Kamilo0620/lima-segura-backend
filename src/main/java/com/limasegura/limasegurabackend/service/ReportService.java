package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.event.ReportCreatedEvent;
import com.limasegura.limasegurabackend.event.ReportValidatedEvent;
import com.limasegura.limasegurabackend.exception.InvalidOperationException;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.*;
import com.limasegura.limasegurabackend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int CONFIRMATIONS_TO_VALIDATE = 3;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final CategoryRepository categoryRepository;
    private final ConfirmationRepository confirmationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Report create(Long userId, Long zoneId, Long categoryId, String description, Double latitude, Double longitude) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + userId));
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zona no encontrada con id: " + zoneId));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada con id: " + categoryId));

        Report report = new Report();
        report.setUser(user);
        report.setZone(zone);
        report.setCategory(category);
        report.setDescription(description);
        report.setLatitude(latitude);
        report.setLongitude(longitude);

        Report savedReport = reportRepository.save(report);

        eventPublisher.publishEvent(new ReportCreatedEvent(savedReport));

        return savedReport;
    }

    public Report getById(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reporte no encontrado con id: " + id));
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
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + userId));

        boolean alreadyConfirmed = confirmationRepository.findByUserIdAndReportId(userId, reportId).isPresent();
        if (alreadyConfirmed) {
            throw new InvalidOperationException("Este usuario ya confirmo este reporte");
        }

        Confirmation confirmation = new Confirmation();
        confirmation.setUser(user);
        confirmation.setReport(report);
        confirmationRepository.save(confirmation);

        long totalConfirmations = confirmationRepository.countByReportId(reportId);
        if (totalConfirmations >= CONFIRMATIONS_TO_VALIDATE) {
            report.setStatus(ReportStatus.VALIDATED);
            reportRepository.save(report);
            eventPublisher.publishEvent(new ReportValidatedEvent(report));
        }
    }

    public void delete(Long id) {
        Report existing = getById(id);
        reportRepository.delete(existing);
    }
}