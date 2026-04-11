package com.bin.bilibrain.catalog;

public record VideoPipelineResponse(
    VideoPipelineStepResponse audio,
    VideoPipelineStepResponse transcript,
    VideoPipelineStepResponse index
) {
}
