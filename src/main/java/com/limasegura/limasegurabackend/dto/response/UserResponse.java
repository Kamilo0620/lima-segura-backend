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

    public static UserResponse fromEntity(User user) {
        if (user == null) {return null;}
        return UserResponse.builder().id(user.getId())
                .name(user.getName()).email(user.getEmail())
                .createdAt(user.getCreatedAt()).build();
    }
}
