package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.model.Confirmation;
import com.limasegura.limasegurabackend.repository.ConfirmationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfirmationService {

    private final ConfirmationRepository confirmationRepository;

    public List<Confirmation> getByReport(Long reportId) {
        return confirmationRepository.findByReportId(reportId);
    }

    public long countByReport(Long reportId) {
        return confirmationRepository.countByReportId(reportId);
    }
}