package com.limasegura.limasegurabackend.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Datos que un usuario puede cambiar de su perfil. El rol NO se puede cambiar por aquí. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Formato de email invalido")
    private String email;

    /** Opcional: si viene vacío, se mantiene la contraseña actual. */
    @Size(min = 6, message = "La contrasena debe tener al menos 6 caracteres")
    private String password;
}
