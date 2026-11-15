package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.dataset.DatasetImportResult;
import com.limasegura.limasegurabackend.ingestion.geo.DistrictResolver;
import com.limasegura.limasegurabackend.model.District;
import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.IncidentType;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DatasetImportServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private DistrictResolver districtResolver;

    @InjectMocks
    private DatasetImportService datasetImportService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void importsValidLimaStreetCrimesAndFiltersDomesticViolenceAndNonLimaRows() {
        District sjl = District.builder()
                .ubigeo("150132").name("San Juan de Lurigancho").province("Lima").department("Lima")
                .centroidLat(-12.002).centroidLng(-77.001)
                .build();
        when(districtResolver.resolve(eq("SAN JUAN DE LURIGANCHO"), any(), any())).thenReturn(sjl);
        when(districtResolver.resolve(eq("TRUJILLO"), any(), any())).thenReturn(null);

        String csv = String.join("\n",
                "departamento_hecho,provincia_hecho,distrito_hecho,tipo_hecho,subtipo_hecho,modalidad_hecho,direccion_hecho,fecha_hora_hecho,lat_hecho,long_hecho",
                "LIMA,LIMA,SAN JUAN DE LURIGANCHO,DELITO,ROBO AGRAVADO,ROBO A MANO ARMADA,\"AV. PROCERES CDRA 15, SJL\",2025-06-30 21:15:00,-12.005,-77.002",
                "LIMA,LIMA,SAN JUAN DE LURIGANCHO,DELITO,LEY NRO 30364 VIOLENCIA CONTRA LA MUJER,VIOLENCIA PSICOLOGICA,JR. X,2025-06-30 20:00:00,-12.004,-77.001",
                "LIMA,LIMA,SAN JUAN DE LURIGANCHO,OPERATIVO,SERVICIO POLICIAL EFECTUADO,CONDUCCION EN ESTADO DE EBRIEDAD,AV. W,2025-06-30 19:00:00,-12.003,-77.001",
                "LA LIBERTAD,TRUJILLO,TRUJILLO,DELITO,ROBO AGRAVADO,ASALTO,AV. ESPANA,2025-06-30 18:00:00,-8.111,-79.028"
        );

        MockMultipartFile file = new MockMultipartFile(
                "file", "sidpol_sample.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        DatasetImportResult result = datasetImportService.importCsv(file);

        assertEquals(4, result.getTotalRows());
        assertEquals(1, result.getImportedRows());
        assertEquals(1, result.getImportedCount());
        assertEquals(3, result.getFilteredOutRows());
        assertEquals(3, result.getSkippedCount());
        verify(incidentRepository, times(1)).save(argThat(inc ->
                inc.getIncidentType() == IncidentType.ARMED_ROBBERY
                        && inc.getAddress().contains("AV. PROCERES")));
    }
}
