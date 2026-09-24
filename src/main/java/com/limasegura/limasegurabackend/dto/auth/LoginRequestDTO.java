package com.limasegura.limasegurabackend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {
    @NotBlank(message = "El email no puede estar vacio")
    @Email(message = "Formato de email invalido")
    private String email;

    @NotBlank(message = "La contrasena no puede estar vacia")
    private String password;
}
