package com.limasegura.limasegurabackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class UserCreateRequest{
        @NotBlank(message="El nombre es obligatorio")
        @Size(max=100,message="El nombre no puede superar los 100 caracteres")
        String name;
        @NotBlank(message="El correo es obligatorio")
        @Email(message="El correo no tiene un formato válido")
        @Size(max=150,message="El correo no puede superar los 150 caracteres")
        String email;
        @NotBlank(message="La contraseña es obligatoria")
        @Size(min=12,message="La contraseña debe tener al menos 8 caracteres")
        String password;
}
