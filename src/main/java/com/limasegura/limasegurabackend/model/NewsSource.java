package com.limasegura.limasegurabackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "news_sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SourceType type;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String url;

    @Size(max = 50)
    @Column(name = "channel_id", length = 50)
    private String channelId;

    @Column(columnDefinition = "TEXT")
    private String config;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Double credibility = 8.0;

    @Column(name = "poll_interval_minutes", nullable = false)
    @Builder.Default
    private Integer pollIntervalMinutes = 5;

    @Column(name = "last_polled_at")
    private LocalDateTime lastPolledAt;

    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private Integer consecutiveFailures = 0;

    @Size(max = 1000)
    @Column(name = "last_error", length = 1000)
    private String lastError;

    @OneToMany(mappedBy = "newsSource", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SourceItem> sourceItems = new ArrayList<>();

    @OneToMany(mappedBy = "newsSource", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<IngestionRun> ingestionRuns = new ArrayList<>();
}
