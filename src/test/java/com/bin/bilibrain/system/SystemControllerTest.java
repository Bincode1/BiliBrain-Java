package com.bin.bilibrain.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"status":"ok"}
                """));
    }

    @Test
    void settingsEndpointReturnsDefaultSettings() throws Exception {
        mockMvc.perform(get("/api/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.max_video_minutes").value(30));
    }

    @Test
    void settingsEndpointPersistsUpdates() throws Exception {
        mockMvc.perform(post("/api/settings")
                .contentType("application/json")
                .content("""
                    {"max_video_minutes":45}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.max_video_minutes").value(45));

        mockMvc.perform(get("/api/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.max_video_minutes").value(45));
    }
}
