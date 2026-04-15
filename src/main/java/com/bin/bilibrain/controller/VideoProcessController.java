package com.bin.bilibrain.controller;

import com.bin.bilibrain.common.BaseResponse;
import com.bin.bilibrain.common.ResultUtils;
import com.bin.bilibrain.model.vo.ingestion.ProcessStatusResponse;
import com.bin.bilibrain.model.vo.ingestion.ResetAllVideosResponse;
import com.bin.bilibrain.service.ingestion.IngestionQueueService;
import com.bin.bilibrain.service.ingestion.PipelineStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoProcessController {
    private final PipelineStatusService pipelineStatusService;
    private final IngestionQueueService ingestionQueueService;

    @GetMapping("/{bvid}/process/status")
    public BaseResponse<ProcessStatusResponse> getProcessStatus(@PathVariable String bvid) {
        return ResultUtils.success(pipelineStatusService.getStatus(bvid));
    }

    @PostMapping("/{bvid}/process")
    public BaseResponse<ProcessStatusResponse> processVideo(@PathVariable String bvid) {
        boolean started = ingestionQueueService.enqueueProcessing(bvid);
        return ResultUtils.success(pipelineStatusService.getStatus(bvid).withStarted(started));
    }

    @PostMapping("/{bvid}/reindex")
    public BaseResponse<ProcessStatusResponse> reindexVideo(@PathVariable String bvid) {
        boolean started = ingestionQueueService.reindexVideoFromChunk(bvid);
        return ResultUtils.success(pipelineStatusService.getStatus(bvid).withStarted(started));
    }

    @PostMapping("/{bvid}/reset")
    public BaseResponse<ProcessStatusResponse> resetVideo(@PathVariable String bvid) {
        boolean started = ingestionQueueService.resetVideoProcessing(bvid);
        return ResultUtils.success(pipelineStatusService.getStatus(bvid)
            .withStarted(started)
            .withReset(true));
    }

    @PostMapping("/reset-all")
    public BaseResponse<ResetAllVideosResponse> resetAllVideos() {
        return ResultUtils.success(ingestionQueueService.resetAllVideoProcessing());
    }
}

