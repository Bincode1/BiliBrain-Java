package com.bin.bilibrain.controller;

import com.bin.bilibrain.common.BaseResponse;
import com.bin.bilibrain.common.ResultUtils;
import com.bin.bilibrain.model.vo.summary.VideoSummaryVO;
import com.bin.bilibrain.service.summary.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoSummaryController {
    private final SummaryService summaryService;

    @GetMapping("/{bvid}/summary")
    public BaseResponse<VideoSummaryVO> getSummary(@PathVariable String bvid) {
        return ResultUtils.success(summaryService.getSummary(bvid));
    }

    @PostMapping("/{bvid}/summary")
    public BaseResponse<VideoSummaryVO> generateSummary(@PathVariable String bvid) {
        return ResultUtils.success(summaryService.generateSummary(bvid));
    }
}
