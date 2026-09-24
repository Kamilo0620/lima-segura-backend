package com.limasegura.limasegurabackend.dto.response;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Setter @Getter
@Builder
public class CategoryResponse {
    private Long id;
    private String name;
}
