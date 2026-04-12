package com.bin.bilibrain.catalog;

import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.mapper.TranscriptMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.vo.catalog.VideoTagsVO;
import com.bin.bilibrain.model.vo.catalog.VideoTranscriptVO;
import com.bin.bilibrain.service.catalog.VideoContentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoContentServiceTest {

    @Mock
    private VideoMapper videoMapper;

    @Mock
    private TranscriptMapper transcriptMapper;

    @InjectMocks
    private VideoContentService videoContentService;

    @Test
    void getTranscriptReturnsTranscriptPayload() {
        when(videoMapper.selectById("BV1unit00001")).thenReturn(Video.builder()
            .bvid("BV1unit00001")
            .title("单元测试视频")
            .build());
        when(transcriptMapper.findByBvid("BV1unit00001")).thenReturn(Transcript.builder()
            .bvid("BV1unit00001")
            .sourceModel("paraformer-v2")
            .segmentCount(2)
            .transcriptText("第一段\n第二段")
            .updatedAt(LocalDateTime.of(2026, 4, 12, 15, 0))
            .build());

        VideoTranscriptVO response = videoContentService.getTranscript("BV1unit00001");

        assertThat(response.transcriptSource()).isEqualTo("paraformer-v2");
        assertThat(response.segmentCount()).isEqualTo(2);
        assertThat(response.text()).isEqualTo("第一段\n第二段");
        assertThat(response.cached()).isTrue();
    }

    @Test
    void getTranscriptThrowsWhenTranscriptMissing() {
        when(videoMapper.selectById("BV1unit00002")).thenReturn(Video.builder()
            .bvid("BV1unit00002")
            .title("未转写视频")
            .build());
        when(transcriptMapper.findByBvid("BV1unit00002")).thenReturn(null);

        assertThatThrownBy(() -> videoContentService.getTranscript("BV1unit00002"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("这个视频还没有转写，请先开始处理。");
    }

    @Test
    void updateTagsDeduplicatesAndPersistsNormalizedTags() {
        when(videoMapper.selectById("BV1unit00003")).thenReturn(Video.builder()
            .bvid("BV1unit00003")
            .title("标签视频")
            .build());

        VideoTagsVO response = videoContentService.updateTags("BV1unit00003", List.of(" Spring AI ", "Java Agent", "Spring AI"));

        ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
        verify(videoMapper).updateById(videoCaptor.capture());
        assertThat(videoCaptor.getValue().getManualTags()).isEqualTo("Spring AI, Java Agent");
        assertThat(response.manualTags()).containsExactly("Spring AI", "Java Agent");
    }
}
