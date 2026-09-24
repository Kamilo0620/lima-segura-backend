package com.limasegura.limasegurabackend.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Setter @Getter
@Builder
public class UserResponse{
    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;
}
