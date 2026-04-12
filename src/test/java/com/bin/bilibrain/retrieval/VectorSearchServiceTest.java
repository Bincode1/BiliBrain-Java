package com.bin.bilibrain.retrieval;

import com.bin.bilibrain.service.retrieval.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorSearchServiceTest {

    @Test
    void deleteByBvidBuildsExpectedFilter() {
        VectorStore vectorStore = mock(VectorStore.class);
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        VectorSearchService service = new VectorSearchService(provider);

        service.deleteByBvid("BV1delete111");

        ArgumentCaptor<org.springframework.ai.vectorstore.filter.Filter.Expression> captor =
            ArgumentCaptor.forClass(org.springframework.ai.vectorstore.filter.Filter.Expression.class);
        verify(vectorStore).delete(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new FilterExpressionBuilder().eq("bvid", "BV1delete111").build());
    }

    @Test
    void similaritySearchBuildsFolderAndBvidFilter() {
        VectorStore vectorStore = mock(VectorStore.class);
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class))).thenReturn(List.of(
            new Document("match", "BV1query111#chunk-0", java.util.Map.of("bvid", "BV1query111"))
        ));
        VectorSearchService service = new VectorSearchService(provider);

        List<Document> result = service.similaritySearch("spring ai", 3, 9527L, "BV1query111");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getQuery()).isEqualTo("spring ai");
        assertThat(captor.getValue().getTopK()).isEqualTo(3);
        assertThat(captor.getValue().getFilterExpression()).isEqualTo(
            new FilterExpressionBuilder().and(
                new FilterExpressionBuilder().eq("folder_id", 9527L),
                new FilterExpressionBuilder().eq("bvid", "BV1query111")
            ).build()
        );
        assertThat(result).hasSize(1);
    }

    @Test
    void similaritySearchFailsWhenVectorStoreUnavailable() {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        VectorSearchService service = new VectorSearchService(provider);

        assertThatThrownBy(() -> service.similaritySearch("spring ai", 3, null, null))
            .hasMessageContaining("向量检索未启用");
    }
}
