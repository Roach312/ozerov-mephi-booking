package ru.mephi.hotelbooking.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class OpenApiDocumentationTest extends AbstractBookingTest {

    @Test
    void apiDocsArePublicAndDescribeTheEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("API сервиса бронирований"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.servers[0].url").value("/api/bookings"))
                .andExpect(jsonPath("$.paths['/user/register'].post").exists())
                .andExpect(jsonPath("$.paths['/user/auth'].post").exists())
                .andExpect(jsonPath("$.paths['/user'].post").exists())
                .andExpect(jsonPath("$.paths['/user'].patch").exists())
                .andExpect(jsonPath("$.paths['/user'].delete").exists())
                .andExpect(jsonPath("$.paths['/booking'].post").exists())
                .andExpect(jsonPath("$.paths['/bookings'].get").exists())
                .andExpect(jsonPath("$.paths['/booking/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/booking/{id}'].delete").exists());
    }

    @Test
    void swaggerUiIsReachableWithoutAToken() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }
}
