package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.event.ReportCreatedEvent;
import com.limasegura.limasegurabackend.model.Report;
import com.limasegura.limasegurabackend.repository.CategoryRepository;
import com.limasegura.limasegurabackend.repository.ReportRepository;
import com.limasegura.limasegurabackend.repository.UserRepository;
import com.limasegura.limasegurabackend.repository.ZoneRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         ZoneRepository zoneRepository,
                         CategoryRepository categoryRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.zoneRepository = zoneRepository;
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
    }

    public Report create(Long userId, Long zoneId, Long categoryId, String description, Double latitude, Double longitude) {
        var user = userRepository.findById(userId).orElseThrow();
        var zone = zoneRepository.findById(zoneId).orElseThrow();
        var category = categoryRepository.findById(categoryId).orElseThrow();

        Report report = new Report();
        report.setUser(user);
        report.setZone(zone);
        report.setCategory(category);
        report.setDescription(description);
        report.setLatitude(latitude);
        report.setLongitude(longitude);
        report.setCreatedAt(LocalDateTime.now());

        Report savedReport = reportRepository.save(report);

        // Publicar el evento de forma asíncrona
        eventPublisher.publishEvent(new ReportCreatedEvent(savedReport));

        return savedReport;
    }

    public Report getById(Long id) {
        return reportRepository.findById(id).orElseThrow();
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

    public void confirm(Long id, Long userId) {
        // Lógica de confirmación existente
    }

    public void delete(Long id) {
        reportRepository.deleteById(id);
    }
}