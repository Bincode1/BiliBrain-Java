package com.bin.bilibrain.service.retrieval;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VectorSearchService {
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    public boolean isAvailable() {
        return vectorStoreProvider.getIfAvailable() != null;
    }

    public void addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        requireVectorStore().add(documents);
    }

    public void deleteByBvid(String bvid) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || bvid == null || bvid.isBlank()) {
            return;
        }
        vectorStore.delete(new FilterExpressionBuilder().eq("bvid", bvid).build());
    }

    public List<Document> similaritySearch(String query, int topK, Long folderId, String bvid) {
        SearchRequest.Builder builder = SearchRequest.builder()
            .query(query)
            .topK(topK);

        var filterBuilder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op filter = null;
        if (folderId != null) {
            filter = filterBuilder.eq("folder_id", folderId);
        }
        if (bvid != null && !bvid.isBlank()) {
            FilterExpressionBuilder.Op bvidFilter = filterBuilder.eq("bvid", bvid);
            filter = filter == null ? bvidFilter : filterBuilder.and(filter, bvidFilter);
        }
        if (filter != null) {
            builder.filterExpression(filter.build());
        }

        return requireVectorStore().similaritySearch(builder.build());
    }

    private VectorStore requireVectorStore() {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new IllegalStateException("向量检索未启用，请先配置 Milvus 与 embedding 模型。");
        }
        return vectorStore;
    }
}

