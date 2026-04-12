package com.bin.bilibrain.summary;

import com.bin.bilibrain.controller.VideoSummaryController;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.exception.ErrorCode;
import com.bin.bilibrain.exception.GlobalExceptionHandler;
import com.bin.bilibrain.model.vo.summary.VideoSummaryVO;
import com.bin.bilibrain.service.summary.SummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VideoSummaryController.class)
@Import(GlobalExceptionHandler.class)
class SummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SummaryService summaryService;

    @Test
    void getSummaryReturnsEmptyPayloadWhenSummaryDoesNotExist() throws Exception {
        when(summaryService.getSummary("BV1summary333")).thenReturn(
            new VideoSummaryVO("BV1summary333", false, false, false, "", "", "")
        );

        mockMvc.perform(get("/api/videos/BV1summary333/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.available").value(false))
            .andExpect(jsonPath("$.data.up_to_date").value(false))
            .andExpect(jsonPath("$.data.summary_text").value(""));
    }

    @Test
    void postSummaryReturnsGeneratedPayload() throws Exception {
        when(summaryService.generateSummary("BV1summary444")).thenReturn(
            new VideoSummaryVO(
                "BV1summary444",
                true,
                true,
                true,
                "hash-123",
                "控制器摘要结果",
                "2026-04-12T11:30:00"
            )
        );

        mockMvc.perform(post("/api/videos/BV1summary444/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.available").value(true))
            .andExpect(jsonPath("$.data.generated").value(true))
            .andExpect(jsonPath("$.data.summary_text").value("控制器摘要结果"));
    }

    @Test
    void postSummaryPropagatesBusinessException() throws Exception {
        when(summaryService.generateSummary("BV1summary555")).thenThrow(
            new BusinessException(
                ErrorCode.OPERATION_ERROR,
                "当前视频还没有转写结果，暂时无法生成摘要。",
                HttpStatus.CONFLICT
            )
        );

        mockMvc.perform(post("/api/videos/BV1summary555/summary"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(ErrorCode.OPERATION_ERROR.getCode()))
            .andExpect(jsonPath("$.message").value("当前视频还没有转写结果，暂时无法生成摘要。"));
    }
}
