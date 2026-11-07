package com.limasegura.limasegurabackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limasegura.limasegurabackend.dto.ingestion.SourceItemResponse;
import com.limasegura.limasegurabackend.dto.worker.WorkerContentRequest;
import com.limasegura.limasegurabackend.ingestion.fetch.WorkerContentService;
import com.limasegura.limasegurabackend.model.SourceItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkerControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private WorkerContentService workerContentService;

    @InjectMocks
    private WorkerController workerController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(workerController).build();
    }

    @Test
    void testGetPendingVideos_ReturnsDiscoveredYouTubeVideos() throws Exception {
        SourceItemResponse video = SourceItemResponse.builder()
                .id(101L)
                .newsSourceId(1L)
                .newsSourceName("América Noticias")
                .externalId("dQw4w9WgXcQ")
                .url("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                .title("SJL: Balacera deja dos heridos en paradero")
                .status(SourceItemStatus.DISCOVERED)
                .createdAt(LocalDateTime.now())
                .build();

        when(workerContentService.getPendingVideos()).thenReturn(List.of(video));

        mockMvc.perform(get("/api/v1/internal/worker/pending-videos"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].externalId").value("dQw4w9WgXcQ"))
                .andExpect(jsonPath("$[0].title").value("SJL: Balacera deja dos heridos en paradero"));
    }

    @Test
    void testSubmitContent_ReceivesTranscribedAudioFromWorker() throws Exception {
        WorkerContentRequest req = new WorkerContentRequest();
        req.setContentText("Transcripción del noticiero: Reportan balacera y asalto en paradero 5 de Canto Grande en San Juan de Lurigancho...");

        mockMvc.perform(post("/api/v1/internal/worker/source-items/101/content")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(workerContentService).submitContent(eq(101L), any(WorkerContentRequest.class));
    }
}
