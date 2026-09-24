package com.limasegura.limasegurabackend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.limasegura.limasegurabackend.model.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDTO {
    @NotBlank(message = "El nombre no puede estar vacio")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "El email no puede estar vacio")
    @Email(message = "Formato de email invalido")
    private String email;

    @NotBlank(message = "La contrasena no puede estar vacia")
    @Size(min = 6, message = "La contrasena debe tener al menos 6 caracteres")
    private String password;

    private Role role; // Optional, can default to USER in service
}
