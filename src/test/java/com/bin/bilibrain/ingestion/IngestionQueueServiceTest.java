package com.bin.bilibrain.ingestion;

import com.bin.bilibrain.model.entity.IngestionTask;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.mapper.IngestionTaskMapper;
import com.bin.bilibrain.mapper.VideoMapper;
import com.bin.bilibrain.service.ingestion.IngestionDispatcherService;
import com.bin.bilibrain.service.ingestion.IngestionQueueService;
import com.bin.bilibrain.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class IngestionQueueServiceTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private IngestionQueueService ingestionQueueService;

    @Autowired
    private IngestionTaskMapper ingestionTaskMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private IngestionDispatcherService ingestionDispatcherService;

    @Test
    void enqueueProcessingCreatesQueuedTaskAndPreventsDuplicates() {
        insertVideo("BV1queue11111", 600);

        boolean firstStarted = ingestionQueueService.enqueueProcessing("BV1queue11111");
        boolean secondStarted = ingestionQueueService.enqueueProcessing("BV1queue11111");

        assertThat(firstStarted).isTrue();
        assertThat(secondStarted).isFalse();
        assertThat(ingestionTaskMapper.findLatestActiveByBvid("BV1queue11111")).isNotNull();
        assertThat(ingestionTaskMapper.findLatestActiveByBvid("BV1queue11111").getStatus()).isEqualTo("queued");
        verify(ingestionDispatcherService).kick();
    }

    @Test
    void resetAllClearsPersistedQueueRows() {
        insertVideo("BV1queue22222", 600);
        ingestionTaskMapper.insert(IngestionTask.builder()
            .bvid("BV1queue22222")
            .operation("process")
            .status("queued")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());

        ingestionQueueService.resetAllVideoProcessing();

        assertThat(ingestionTaskMapper.selectCount(null)).isZero();
    }

    private void insertVideo(String bvid, int duration) {
        videoMapper.insert(Video.builder()
            .bvid(bvid)
            .folderId(2002L)
            .title("Queue Test")
            .upName("BinCode")
            .duration(duration)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .isInvalid(0)
            .build());
    }

    @TestConfiguration
    static class QueueServiceTestConfig {
        @Bean
        @Primary
        IngestionDispatcherService ingestionDispatcherService() {
            return mock(IngestionDispatcherService.class);
        }
    }
}

