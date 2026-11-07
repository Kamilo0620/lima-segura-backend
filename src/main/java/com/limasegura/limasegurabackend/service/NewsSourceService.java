package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.source.SourceCreateRequest;
import com.limasegura.limasegurabackend.dto.source.SourceResponse;
import com.limasegura.limasegurabackend.dto.source.SourceUpdateRequest;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.ingestion.scheduler.IngestionScheduler;
import com.limasegura.limasegurabackend.model.NewsSource;
import com.limasegura.limasegurabackend.repository.NewsSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewsSourceService {

    private final NewsSourceRepository newsSourceRepository;
    private final IngestionScheduler ingestionScheduler;

    public SourceResponse create(SourceCreateRequest request) {
        NewsSource source = NewsSource.builder()
                .name(request.getName())
                .type(request.getType())
                .url(request.getUrl())
                .channelId(request.getChannelId())
                .config(request.getConfig())
                .active(true)
                .credibility(request.getCredibility() != null ? request.getCredibility() : 8.0)
                .pollIntervalMinutes(request.getPollIntervalMinutes() != null ? request.getPollIntervalMinutes() : 5)
                .build();
        return mapToResponse(newsSourceRepository.save(source));
    }

    public List<SourceResponse> getAll() {
        return newsSourceRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public SourceResponse getById(Long id) {
        NewsSource source = newsSourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NewsSource not found with id: " + id));
        return mapToResponse(source);
    }

    public SourceResponse update(Long id, SourceUpdateRequest request) {
        NewsSource source = newsSourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NewsSource not found with id: " + id));

        if (request.getName() != null) source.setName(request.getName());
        if (request.getUrl() != null) source.setUrl(request.getUrl());
        if (request.getChannelId() != null) source.setChannelId(request.getChannelId());
        if (request.getConfig() != null) source.setConfig(request.getConfig());
        if (request.getActive() != null) source.setActive(request.getActive());
        if (request.getCredibility() != null) source.setCredibility(request.getCredibility());
        if (request.getPollIntervalMinutes() != null) source.setPollIntervalMinutes(request.getPollIntervalMinutes());

        return mapToResponse(newsSourceRepository.save(source));
    }

    public void delete(Long id) {
        NewsSource source = newsSourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NewsSource not found with id: " + id));
        newsSourceRepository.delete(source);
    }

    public void triggerIngestion(Long id) {
        NewsSource source = newsSourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NewsSource not found with id: " + id));
        ingestionScheduler.processSource(source);
    }

    private SourceResponse mapToResponse(NewsSource source) {
        return SourceResponse.builder()
                .id(source.getId())
                .name(source.getName())
                .type(source.getType())
                .url(source.getUrl())
                .channelId(source.getChannelId())
                .config(source.getConfig())
                .active(source.getActive())
                .credibility(source.getCredibility() != null ? source.getCredibility() : 8.0)
                .pollIntervalMinutes(source.getPollIntervalMinutes())
                .lastPolledAt(source.getLastPolledAt())
                .consecutiveFailures(source.getConsecutiveFailures())
                .lastError(source.getLastError())
                .build();
    }
}
