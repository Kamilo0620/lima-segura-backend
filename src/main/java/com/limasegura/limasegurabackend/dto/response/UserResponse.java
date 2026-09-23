package com.limasegura.limasegurabackend.dto.response;

import com.limasegura.limasegurabackend.model.User;
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
