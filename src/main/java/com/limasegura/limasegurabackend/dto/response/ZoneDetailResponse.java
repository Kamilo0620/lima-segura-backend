package com.limasegura.limasegurabackend.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Setter @Getter
@Builder
public class ZoneDetailResponse {
    private Long id;
    private String name;
    private String district;
    private Double latitude;
    private Double longitude;
    private int incidentsCount;
}
