package com.limasegura.limasegurabackend.dto.response;


import lombok.*;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Setter @Getter
@Builder
public class IncidentResponse {
    private Long id;
    private Long zoneId;
    private Long categoryId;
    private String source;
    private LocalDate date;
    private Double latitude;
    private Double longitude;
}
