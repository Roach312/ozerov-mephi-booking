package ru.mephi.hotelbooking.hotel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class OpenApiDocumentationTest extends AbstractHotelTest {

    @Test
    void apiDocsArePublicAndDescribeTheSecuredEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("API сервиса управления отелями"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/hotels'].post").exists())
                .andExpect(jsonPath("$.paths['/api/rooms/recommend'].get").exists())
                .andExpect(jsonPath("$.paths['/api/rooms/stats'].get").exists())
                .andExpect(jsonPath("$.paths['/api/rooms/{id}/confirm-availability'].post").exists())
                .andExpect(jsonPath("$.paths['/api/rooms/{id}/release'].post").exists());
    }

    @Test
    void swaggerUiIsReachableWithoutAToken() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }
}
