package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.response.ConfirmationResponse;
import com.limasegura.limasegurabackend.repository.ConfirmationRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfirmationService {

    private final ConfirmationRepository confirmationRepository;
    private final ModelMapper modelMapper;

    public List<ConfirmationResponse> getByReport(Long reportId) {
        return confirmationRepository.findByReportId(reportId).stream()
                .map(confirmation->modelMapper.map(confirmation, ConfirmationResponse.class)).toList();
    }

    public long countByReport(Long reportId) {
        return confirmationRepository.countByReportId(reportId);
    }
}