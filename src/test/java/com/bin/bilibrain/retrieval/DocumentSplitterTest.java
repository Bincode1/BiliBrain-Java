package com.bin.bilibrain.retrieval;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.service.retrieval.DocumentSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSplitterTest {

    @Test
    void splitSegmentsKeepsSegmentOverlapAndTimeMetadata() {
        AppProperties appProperties = new AppProperties();
        appProperties.getRetrieval().setChunkSize(12);
        appProperties.getRetrieval().setChunkOverlap(5);
        DocumentSplitter splitter = new DocumentSplitter(appProperties);

        List<DocumentSplitter.TranscriptChunk> chunks = splitter.splitSegments(List.of(
            new DocumentSplitter.TranscriptSegment(0.0, 10.0, "alpha"),
            new DocumentSplitter.TranscriptSegment(10.0, 20.0, "bravo"),
            new DocumentSplitter.TranscriptSegment(20.0, 30.0, "charlie")
        ));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text()).isEqualTo("alpha\n\nbravo");
        assertThat(chunks.get(0).startSeconds()).isEqualTo(0.0);
        assertThat(chunks.get(0).endSeconds()).isEqualTo(20.0);
        assertThat(chunks.get(1).text()).isEqualTo("bravo\n\ncharlie");
        assertThat(chunks.get(1).startSeconds()).isEqualTo(10.0);
        assertThat(chunks.get(1).endSeconds()).isEqualTo(30.0);
    }
}
