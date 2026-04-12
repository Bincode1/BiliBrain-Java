package com.bin.bilibrain.model.vo.catalog;

public record VideoPipelineResponse(
    VideoPipelineStepResponse audio,
    VideoPipelineStepResponse transcript,
    VideoPipelineStepResponse index
) {
}

