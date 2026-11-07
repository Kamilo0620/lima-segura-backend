package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.ingestion.SourceItemResponse;
import com.limasegura.limasegurabackend.model.SourceItem;
import com.limasegura.limasegurabackend.model.SourceItemStatus;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SourceItemService {

    private final SourceItemRepository sourceItemRepository;

    public Page<SourceItemResponse> findAll(SourceItemStatus status, Pageable pageable) {
        Page<SourceItem> page;
        if (status != null) {
            page = sourceItemRepository.findByStatus(status, pageable);
        } else {
            page = sourceItemRepository.findAll(pageable);
        }
        return page.map(this::mapToResponse);
    }

    public void reprocess(Long id) {
        SourceItem item = sourceItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SourceItem not found"));
        item.setStatus(SourceItemStatus.DISCOVERED);
        item.setAttempts(0);
        sourceItemRepository.save(item);
    }

    private SourceItemResponse mapToResponse(SourceItem item) {
        return SourceItemResponse.builder()
                .id(item.getId())
                .newsSourceId(item.getNewsSource() != null ? item.getNewsSource().getId() : null)
                .newsSourceName(item.getNewsSource() != null ? item.getNewsSource().getName() : null)
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
