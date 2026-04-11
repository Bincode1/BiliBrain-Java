package com.bin.bilibrain.ingestion;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.service.asr.AudioChunkPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudioChunkPlannerTest {

    @Test
    void buildPlanAlignsToSilencePointsAndAddsOverlap() {
        AppProperties appProperties = new AppProperties();
        appProperties.getProcessing().setAsrTargetChunkSeconds(90);
        appProperties.getProcessing().setAsrChunkSeconds(120);
        appProperties.getProcessing().setAsrChunkOverlapSeconds(10);
        AudioChunkPlanner planner = new AudioChunkPlanner(appProperties);

        List<AudioChunkPlanner.AudioChunkSpec> specs = planner.buildPlan(260, List.of(85.0, 170.0));

        assertThat(specs).hasSize(3);
        assertThat(specs.get(0).startSeconds()).isEqualTo(0.0);
        assertThat(specs.get(0).endSeconds()).isEqualTo(85.0);
        assertThat(specs.get(1).startSeconds()).isEqualTo(85.0);
        assertThat(specs.get(1).clipStartSeconds()).isEqualTo(75.0);
        assertThat(specs.get(1).endSeconds()).isEqualTo(170.0);
        assertThat(specs.get(2).startSeconds()).isEqualTo(170.0);
        assertThat(specs.get(2).clipStartSeconds()).isEqualTo(160.0);
        assertThat(specs.get(2).endSeconds()).isEqualTo(260.0);
    }

    @Test
    void buildPlanFallsBackToHardMaxWhenSilencePointIsUnavailable() {
        AppProperties appProperties = new AppProperties();
        appProperties.getProcessing().setAsrTargetChunkSeconds(60);
        appProperties.getProcessing().setAsrChunkSeconds(80);
        appProperties.getProcessing().setAsrChunkOverlapSeconds(5);
        AudioChunkPlanner planner = new AudioChunkPlanner(appProperties);

        List<AudioChunkPlanner.AudioChunkSpec> specs = planner.buildPlan(170, List.of());

        assertThat(specs).hasSize(3);
        assertThat(specs.get(0).endSeconds()).isEqualTo(80.0);
        assertThat(specs.get(1).startSeconds()).isEqualTo(80.0);
        assertThat(specs.get(1).clipStartSeconds()).isEqualTo(75.0);
        assertThat(specs.get(2).startSeconds()).isEqualTo(160.0);
        assertThat(specs.get(2).endSeconds()).isEqualTo(170.0);
    }

    @Test
    void trimRepeatedPrefixRemovesOverlapTextNoise() {
        AppProperties appProperties = new AppProperties();
        AudioChunkPlanner planner = new AudioChunkPlanner(appProperties);

        String trimmed = planner.trimRepeatedPrefix(
            "这是上一段结尾，重叠前缀内容很长",
            "重叠前缀内容很长，真正的新内容开始"
        );

        assertThat(trimmed).isEqualTo("真正的新内容开始");
    }
}
