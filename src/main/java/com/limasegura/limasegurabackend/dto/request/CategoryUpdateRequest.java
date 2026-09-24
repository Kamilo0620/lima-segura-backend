package com.limasegura.limasegurabackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Setter @Getter
@Builder
public class CategoryUpdateRequest {
    @NotBlank(message = "El nombre de la categoria es obligatorio")
    @Size(max = 50,message = "El nombre no puede superar los 50 caracteres")
    private String name;
    @Size(max = 255,message = "La descripción no puede superar los 255 caracteres")
    private String description;
}
