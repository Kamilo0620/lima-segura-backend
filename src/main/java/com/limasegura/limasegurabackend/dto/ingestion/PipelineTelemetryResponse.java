package com.limasegura.limasegurabackend.dto.ingestion;

import com.limasegura.limasegurabackend.ingestion.telemetry.PipelineActivityTracker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class PipelineTelemetryResponse {
    private boolean searchingInternetNow;
    private int activeOperationsCount;
    private String currentOperation;
    private LocalDateTime lastActivityAt;

    private long totalPublished;
    private long realNewspaperCount;
    private long realYoutubeCount;
    private long citizenCount;
    private long sampleBaselineCount;
    private long pendingReviewCount;

    private long sourceItemsQueued;
    private long sourceItemsExtracted;
    private long sourceItemsDiscarded;
    private long sourceItemsFailed;

    private List<RecentPublishedItem> recentPublished;
    private List<PipelineActivityTracker.LogEntry> recentLogs;

    @Data
    @Builder
    @AllArgsConstructor
    public static class RecentPublishedItem {
        private Long id;
        private String title;
        private String origin;
        private String districtName;
        private String geoPrecision;
        private String llmModel;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }
}
