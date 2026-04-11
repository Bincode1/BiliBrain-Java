package com.bin.bilibrain.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoProcessController {
    private final PipelineStatusService pipelineStatusService;
    private final IngestionQueueService ingestionQueueService;

    @GetMapping("/{bvid}/process/status")
    public ProcessStatusResponse getProcessStatus(@PathVariable String bvid) {
        return pipelineStatusService.getStatus(bvid);
    }

    @PostMapping("/{bvid}/process")
    public ProcessStatusResponse processVideo(@PathVariable String bvid) {
        boolean started = ingestionQueueService.enqueueProcessing(bvid);
        return pipelineStatusService.getStatus(bvid).withStarted(started);
    }

    @PostMapping("/{bvid}/reset")
    public ProcessStatusResponse resetVideo(@PathVariable String bvid) {
        boolean started = ingestionQueueService.resetVideoProcessing(bvid);
        return pipelineStatusService.getStatus(bvid)
            .withStarted(started)
            .withReset(true);
    }

    @PostMapping("/reset-all")
    public Map<String, Object> resetAllVideos() {
        return ingestionQueueService.resetAllVideoProcessing();
    }
}
