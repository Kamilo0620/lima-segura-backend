package com.limasegura.limasegurabackend.ingestion.geo;

import com.limasegura.limasegurabackend.model.District;
import com.limasegura.limasegurabackend.repository.DistrictRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistrictResolverTest {

    @Mock
    private DistrictRepository districtRepository;

    @InjectMocks
    private DistrictResolver districtResolver;

    private District callao;
    private District santaRosa;
    private District sjl;
    private District lurigancho;

    @BeforeEach
    void setUp() {
        callao = District.builder().ubigeo("070101").name("Callao").province("Callao").department("Callao")
                .centroidLat(-12.0566).centroidLng(-77.1185).build();
        santaRosa = District.builder().ubigeo("150139").name("Santa Rosa").province("Lima").department("Lima")
                .centroidLat(-11.7955).centroidLng(-77.1649).build();
        sjl = District.builder().ubigeo("150132").name("San Juan de Lurigancho").province("Lima").department("Lima")
                .centroidLat(-12.0020).centroidLng(-77.0070).build();
        lurigancho = District.builder().ubigeo("150118").name("Lurigancho").province("Lima").department("Lima")
                .centroidLat(-11.9420).centroidLng(-76.7050).build();

        when(districtRepository.findAll()).thenReturn(new java.util.ArrayList<>(List.of(
                lurigancho, sjl, santaRosa, callao
        )));
    }

    @Test
    void doesNotMisclassifyCallaoHeadlineMentioningSantaRosaIntoNorthSantaRosa() {
        District resolved1 = districtResolver.resolveFromText(
                "¡Terror en el Callao! Balacera en fiesta por Santa Rosa deja varios heridos");
        assertNotNull(resolved1);
        assertEquals("070101", resolved1.getUbigeo());

        District resolved2 = districtResolver.resolveFromText(
                "Callao: heridos fueron trasladados cerca de Av. Santa Rosa tras ataque armado");
        assertNotNull(resolved2);
        assertEquals("070101", resolved2.getUbigeo());
    }

    @Test
    void prioritizesExplicitDistrictOfSantaRosaAndSanJuanDeLuriganchoOverShorterMatch() {
        District resolvedSantaRosa = districtResolver.resolveFromText(
                "Operativo policial en el distrito de Santa Rosa en Lima Norte");
        assertNotNull(resolvedSantaRosa);
        assertEquals("150139", resolvedSantaRosa.getUbigeo());

        District resolvedSjl = districtResolver.resolveFromText(
                "Asalto en San Juan de Lurigancho cerca del metro");
        assertNotNull(resolvedSjl);
        assertEquals("150132", resolvedSjl.getUbigeo());
    }
}
