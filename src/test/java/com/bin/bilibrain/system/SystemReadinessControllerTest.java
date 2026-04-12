package com.bin.bilibrain.system;

import com.bin.bilibrain.controller.SystemController;
import com.bin.bilibrain.exception.GlobalExceptionHandler;
import com.bin.bilibrain.model.vo.system.ProcessingSettingsVO;
import com.bin.bilibrain.model.vo.system.ReadinessCheckVO;
import com.bin.bilibrain.model.vo.system.SystemReadinessVO;
import com.bin.bilibrain.service.system.ProcessingSettingsService;
import com.bin.bilibrain.service.system.SystemReadinessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@Import(GlobalExceptionHandler.class)
class SystemReadinessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessingSettingsService processingSettingsService;

    @MockitoBean
    private SystemReadinessService systemReadinessService;

    @Test
    void readinessEndpointReturnsOperationalChecks() throws Exception {
        when(processingSettingsService.getSettings()).thenReturn(new ProcessingSettingsVO(30));
        when(systemReadinessService.getReadiness()).thenReturn(new SystemReadinessVO(
            "degraded",
            List.of(
                new ReadinessCheckVO("mysql_schema", "ready", "MySQL 核心表已就绪。"),
                new ReadinessCheckVO("milvus", "disabled", "向量检索未启用。")
            )
        ));

        mockMvc.perform(get("/api/system/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("degraded"))
            .andExpect(jsonPath("$.data.checks[0].key").value("mysql_schema"))
            .andExpect(jsonPath("$.data.checks[1].status").value("disabled"));
    }
}
