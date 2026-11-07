package com.limasegura.limasegurabackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "source_items", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"news_source_id", "external_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_source_id", nullable = false)
    @NotNull
    private NewsSource newsSource;

    @NotBlank
    @Size(max = 500)
    @Column(name = "external_id", nullable = false, length = 500)
    private String externalId;

    @Size(max = 1000)
    @Column(length = 1000)
    private String url;

    @Size(max = 500)
    @Column(length = 500)
    private String title;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Size(max = 64)
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SourceItemStatus status = SourceItemStatus.DISCOVERED;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Size(max = 2000)
    @Column(length = 2000)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToMany(mappedBy = "sourceItems")
    @Builder.Default
    private Set<Incident> incidents = new HashSet<>();
}
