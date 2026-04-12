package com.bin.bilibrain.controller;

import com.bin.bilibrain.common.BaseResponse;
import com.bin.bilibrain.common.ResultUtils;
import com.bin.bilibrain.model.dto.catalog.VideoTagsUpdateRequest;
import com.bin.bilibrain.model.vo.catalog.VideoTagsVO;
import com.bin.bilibrain.model.vo.catalog.VideoTranscriptVO;
import com.bin.bilibrain.service.catalog.VideoContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoContentController {
    private final VideoContentService videoContentService;

    @GetMapping("/{bvid}/transcript")
    public BaseResponse<VideoTranscriptVO> getTranscript(@PathVariable String bvid) {
        return ResultUtils.success(videoContentService.getTranscript(bvid));
    }

    @PostMapping("/{bvid}/tags")
    public BaseResponse<VideoTagsVO> updateTags(
        @PathVariable String bvid,
        @Valid @RequestBody VideoTagsUpdateRequest request
    ) {
        return ResultUtils.success(videoContentService.updateTags(bvid, request.tags()));
    }
}
