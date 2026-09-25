package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.request.ReportCreateRequest;
import com.limasegura.limasegurabackend.dto.response.ReportDetailResponse;
import com.limasegura.limasegurabackend.dto.response.ReportResponse;
import com.limasegura.limasegurabackend.event.ReportCreatedEvent;
import com.limasegura.limasegurabackend.event.ReportValidatedEvent;
import com.limasegura.limasegurabackend.exception.InvalidOperationException;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.*;
import com.limasegura.limasegurabackend.repository.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int CONFIRMATIONS_TO_VALIDATE = 3;

    private final ModelMapper modelMapper;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final CategoryRepository categoryRepository;
    private final ConfirmationRepository confirmationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ReportResponse create(ReportCreateRequest req) {
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + req.getUserId()));
        Zone zone = zoneRepository.findById(req.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Zona no encontrada con id: " + req.getZoneId()));
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada con id: " + req.getCategoryId()));

        Report report = new Report();
        report.setDescription(req.getDescription());
        report.setLatitude(req.getLatitude());
        report.setLongitude(req.getLongitude());
        report.setUser(user);
        report.setZone(zone);
        report.setCategory(category);
        report.setCreatedAt(LocalDateTime.now());

        Report savedReport = reportRepository.save(report);

        eventPublisher.publishEvent(new ReportCreatedEvent(savedReport));
        return modelMapper.map(savedReport, ReportResponse.class);
    }

    public ReportDetailResponse getById(Long id) {
        Report report=reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reporte no encontrado con id: " + id));
        return modelMapper.map(report,ReportDetailResponse.class);
    }

    public List<ReportResponse> getAll() {
        return reportRepository.findAll().stream()
                .map(report->modelMapper.map(report, ReportResponse.class)).toList();
    }

    public List<ReportResponse> getByZone(Long zoneId) {
        return reportRepository.findByZoneId(zoneId).stream()
                .map(report->modelMapper.map(report,ReportResponse.class)).toList();
    }

    public List<ReportResponse> getByUser(Long userId) {
        return reportRepository.findByUserId(userId).stream()
                .map(report->modelMapper.map(report,ReportResponse.class)).toList();
    }

    public void confirm(Long reportId, Long userId) {
        Report report=reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Reporte no encontrado con id: " + reportId));
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
        Report existing = reportRepository.findById(id)
                        .orElseThrow(()->new ResourceNotFoundException("Reporte no encontrado con id :"+id));
        reportRepository.delete(existing);
    }
}