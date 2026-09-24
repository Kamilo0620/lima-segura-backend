package com.limasegura.limasegurabackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Setter @Getter
@Builder
public class ZoneUpdateRequest {
    @NotBlank(message = "El nombre de la zona es obligatorio")
    @Size(max = 100,message="El nombre de la zona no puede superar los 100 caracteres")
    private String name;
    @NotBlank(message = "El distrito es obligatorio")
    @Size(max = 100,message="El nombre del distrito no puede superar los 100 caracteres")
    private String district;
}
