package com.limasegura.limasegurabackend.dto.response;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
@Builder
public class ZoneResponse {
    private Long id;
    private String name;
    private String district;
    private Double latitude;
    private Double longitude;

}
