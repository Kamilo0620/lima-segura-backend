package com.limasegura.limasegurabackend.ingestion.connector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveredItem {
    private String externalId;
    private String url;
    private String title;
    private String contentText;
    private LocalDateTime publishedAt;
}
