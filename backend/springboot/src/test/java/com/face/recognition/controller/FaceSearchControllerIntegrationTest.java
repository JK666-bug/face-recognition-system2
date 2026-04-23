package com.face.recognition.controller;

import com.face.recognition.dto.SearchMatchDTO;
import com.face.recognition.service.FaceSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice: multipart POST /api/face/search -> FaceSearchService -> JSON (no full Spring context).
 */
class FaceSearchControllerIntegrationTest {

    private MockMvc mockMvc;
    private FaceSearchService faceSearchService;

    @BeforeEach
    void setUp() {
        faceSearchService = mock(FaceSearchService.class);
        FaceSearchController controller = new FaceSearchController();
        ReflectionTestUtils.setField(controller, "faceSearchService", faceSearchService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void searchFace_returnsOkAndBody() throws Exception {
        when(faceSearchService.searchByImage(any(), anyInt(), isNull(), isNull())).thenReturn(List.of(
                SearchMatchDTO.builder()
                        .id("face-1")
                        .peopleId("people-1")
                        .score(0.91f)
                        .location("Gate-A")
                        .timestamp("2026-01-01 12:00:00")
                        .imageBase64("AAA")
                        .build()
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "probe.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/face/search")
                        .file(file)
                        .param("topK", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("face-1"))
                .andExpect(jsonPath("$[0].score").value(closeTo(0.91, 0.0001)));
    }
}
