package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.ingestion.IngestionRunResponse;
import com.limasegura.limasegurabackend.model.IngestionRun;
import com.limasegura.limasegurabackend.repository.IngestionRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IngestionRunService {

    private final IngestionRunRepository ingestionRunRepository;

    public Page<IngestionRunResponse> findAll(Long sourceId, Pageable pageable) {
        Page<IngestionRun> page;
        if (sourceId != null) {
            page = ingestionRunRepository.findByNewsSourceIdOrderByStartedAtDesc(sourceId, pageable);
        } else {
            page = ingestionRunRepository.findAllByOrderByStartedAtDesc(pageable);
        }
        return page.map(this::mapToResponse);
    }

    private IngestionRunResponse mapToResponse(IngestionRun run) {
        return IngestionRunResponse.builder()
                .id(run.getId())
                .newsSourceId(run.getNewsSource() != null ? run.getNewsSource().getId() : null)
                .newsSourceName(run.getNewsSource() != null ? run.getNewsSource().getName() : null)
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .itemsFound(run.getItemsFound())
                .itemsNew(run.getItemsNew())
                .errors(run.getErrors())
                .status(run.getStatus())
                .build();
    }
}
