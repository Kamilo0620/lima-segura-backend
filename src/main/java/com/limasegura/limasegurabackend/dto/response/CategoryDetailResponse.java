package com.limasegura.limasegurabackend.dto.response;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Setter @Getter
@Builder
public class CategoryDetailResponse {
    private Long id;
    private String name;
    private String description;
    private Integer reportsCount;
    private Integer incidentsCount;
}
