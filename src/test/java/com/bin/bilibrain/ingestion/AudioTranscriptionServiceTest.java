package com.bin.bilibrain.ingestion;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.service.asr.AudioChunkPlanner;
import com.bin.bilibrain.service.asr.AudioTranscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AudioTranscriptionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void transcribeBuildsSegmentsAndDeduplicatesChunkOverlap() throws Exception {
        AppProperties appProperties = new AppProperties();
        appProperties.getProcessing().setAsrChunkConcurrency(2);
        appProperties.getProcessing().setAsrLanguageHints(List.of("zh"));

        AudioChunkPlanner planner = Mockito.spy(new AudioChunkPlanner(appProperties));
        Path inputAudio = tempDir.resolve("input.m4a");
        Files.writeString(inputAudio, "fake-audio");

        List<AudioChunkPlanner.AudioChunkSpec> chunkSpecs = List.of(
            new AudioChunkPlanner.AudioChunkSpec(0, 0.0, 60.0, 0.0, 60.0),
            new AudioChunkPlanner.AudioChunkSpec(1, 60.0, 120.0, 52.0, 120.0)
        );
        doReturn(chunkSpecs).when(planner).plan(any(Path.class));
        doAnswer(invocation -> {
            Path outputPath = invocation.getArgument(2);
            Files.writeString(outputPath, "chunk-" + invocation.<AudioChunkPlanner.AudioChunkSpec>getArgument(1).index());
            return null;
        }).when(planner).extractChunk(any(Path.class), any(AudioChunkPlanner.AudioChunkSpec.class), any(Path.class));

        TranscriptionModel transcriptionModel = mock(TranscriptionModel.class);
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenAnswer(invocation -> {
            AudioTranscriptionPrompt prompt = invocation.getArgument(0);
            String fileName = ((FileSystemResource) prompt.getInstructions()).getFile().getName();
            String text = fileName.contains("chunk-000")
                ? "第一段内容扩展说明"
                : "第一段内容扩展说明，第二段继续";
            return new AudioTranscriptionResponse(new AudioTranscription(text));
        });

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("transcriptionModel", transcriptionModel);
        ObjectProvider<TranscriptionModel> provider = beanFactory.getBeanProvider(TranscriptionModel.class);
        Environment environment = new MockEnvironment()
            .withProperty("spring.ai.dashscope.audio.transcription.options.model", "paraformer-v2");

        AudioTranscriptionService service = new AudioTranscriptionService(provider, planner, appProperties, environment);
        List<AudioTranscriptionService.AudioTranscriptionProgress> progressEvents = new CopyOnWriteArrayList<>();

        AudioTranscriptionService.AudioTranscriptionResult result = service.transcribe(inputAudio, progressEvents::add);

        assertThat(result.model()).isEqualTo("dashscope/paraformer-v2");
        assertThat(result.chunkCount()).isEqualTo(2);
        assertThat(result.segmentCount()).isEqualTo(2);
        assertThat(result.text()).isEqualTo("第一段内容扩展说明\n\n第二段继续");
        assertThat(result.segments().get(1).content()).isEqualTo("第二段继续");
        assertThat(progressEvents).extracting(AudioTranscriptionService.AudioTranscriptionProgress::stage)
            .containsExactly("chunking", "transcribing", "transcribing", "transcribing");
    }
}
