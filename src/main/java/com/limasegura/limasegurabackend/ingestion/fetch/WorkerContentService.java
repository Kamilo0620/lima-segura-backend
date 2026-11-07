package com.limasegura.limasegurabackend.ingestion.fetch;

import com.limasegura.limasegurabackend.dto.ingestion.SourceItemResponse;
import com.limasegura.limasegurabackend.dto.worker.WorkerContentRequest;
import com.limasegura.limasegurabackend.event.SourceItemContentReadyEvent;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.SourceItem;
import com.limasegura.limasegurabackend.model.SourceItemStatus;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerContentService {

    private final SourceItemRepository sourceItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<SourceItemResponse> getPendingVideos() {
        return sourceItemRepository.findPendingVideos().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void submitContent(Long sourceItemId, WorkerContentRequest request) {
        SourceItem item = sourceItemRepository.findById(sourceItemId)
                .orElseThrow(() -> new ResourceNotFoundException("SourceItem not found: " + sourceItemId));

        item.setContentText(request.getContentText());
        item.setStatus(SourceItemStatus.CONTENT_READY);
        sourceItemRepository.save(item);

        eventPublisher.publishEvent(new SourceItemContentReadyEvent(this, sourceItemId));
        log.info("Worker submitted content for source item {}", sourceItemId);
    }

    private SourceItemResponse toResponse(SourceItem item) {
        return SourceItemResponse.builder()
                .id(item.getId())
                .newsSourceId(item.getNewsSource().getId())
                .newsSourceName(item.getNewsSource().getName())
                .externalId(item.getExternalId())
                .url(item.getUrl())
                .title(item.getTitle())
                .publishedAt(item.getPublishedAt())
                .status(item.getStatus())
                .attempts(item.getAttempts())
                .error(item.getError())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
