package com.limasegura.limasegurabackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "incidents", indexes = {
    @Index(name = "idx_incident_occurred_at", columnList = "occurred_at"),
    @Index(name = "idx_incident_type_occurred", columnList = "incident_type, occurred_at"),
    @Index(name = "idx_incident_district", columnList = "district_ubigeo")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 30)
    private IncidentType incidentType;

    @Size(max = 200)
    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "occurred_at_precision", length = 15)
    @Builder.Default
    private OccurredAtPrecision occurredAtPrecision = OccurredAtPrecision.UNKNOWN;

    @Size(max = 500)
    @Column(length = 500)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_ubigeo")
    private District district;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    @Enumerated(EnumType.STRING)
    @Column(name = "geo_precision", length = 15)
    private GeoPrecision geoPrecision;

    @Column(nullable = false)
    @Builder.Default
    private Double confidence = 0.0;

    @Column(name = "incident_count", nullable = false)
    @Builder.Default
    private Integer incidentCount = 1;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IncidentStatus status = IncidentStatus.PENDING_REVIEW;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentOrigin origin;

    @Size(max = 100)
    @Column(name = "llm_model", length = 100)
    private String llmModel;

    @Column(name = "victims_count")
    private Integer victimsCount;

    @Size(max = 300)
    @Column(name = "evidence_excerpt", length = 300)
    private String evidenceExcerpt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToMany
    @JoinTable(
        name = "incident_sources",
        joinColumns = @JoinColumn(name = "incident_id"),
        inverseJoinColumns = @JoinColumn(name = "source_item_id")
    )
    @Builder.Default
    private Set<SourceItem> sourceItems = new HashSet<>();
}