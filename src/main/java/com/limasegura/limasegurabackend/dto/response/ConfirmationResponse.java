package com.limasegura.limasegurabackend.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Setter @Getter
@Builder
public class ConfirmationResponse {
    private Long id;
    private Long userId;
    private Long reportId;
    private LocalDateTime createdAt;
}
