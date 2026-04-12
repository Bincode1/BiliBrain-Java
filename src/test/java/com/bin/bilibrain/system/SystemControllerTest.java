package com.bin.bilibrain.system;

import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SystemControllerTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("ok"));
    }

    @Test
    void settingsEndpointReturnsDefaultSettings() throws Exception {
        mockMvc.perform(get("/api/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.max_video_minutes").value(30));
    }

    @Test
    void settingsEndpointPersistsUpdates() throws Exception {
        mockMvc.perform(post("/api/settings")
                .contentType("application/json")
                .content("""
                    {"max_video_minutes":45}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.max_video_minutes").value(45));

        mockMvc.perform(get("/api/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.max_video_minutes").value(45));
    }
}
