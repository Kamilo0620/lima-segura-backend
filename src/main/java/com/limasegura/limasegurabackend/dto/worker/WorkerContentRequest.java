package com.limasegura.limasegurabackend.dto.worker;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkerContentRequest {
    @NotBlank(message = "Content text is required")
    private String contentText;
}
