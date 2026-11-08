package com.limasegura.limasegurabackend.dto.user;

import com.limasegura.limasegurabackend.model.Role;
import com.limasegura.limasegurabackend.model.User;

import java.time.LocalDateTime;

/** Respuesta pública de un usuario: nunca expone la contraseña. */
public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
