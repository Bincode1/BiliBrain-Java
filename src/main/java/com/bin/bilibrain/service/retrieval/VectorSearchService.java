package com.bin.bilibrain.service.retrieval;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VectorSearchService {
    // 暂时注释掉，等待Milvus依赖问题解决
    // private final VectorStore vectorStore;

    public void addDocuments(List<Document> documents) {
        // vectorStore.add(documents);
    }

    public void deleteByBvid(String bvid) {
        // vectorStore.delete(FilterExpressionBuilder.builder()
        //     .eq("bvid", bvid).build());
    }

    public List<Document> similaritySearch(String query, int topK) {
        // return vectorStore.similaritySearch(
        //     SearchRequest.builder()
        //         .query(query)
        //         .topK(topK)
        //         .build()
        // );
        return null;
    }

    public List<Document> similaritySearchWithFilter(String query, String bvid, int topK) {
        // return vectorStore.similaritySearch(
        //     SearchRequest.builder()
        //         .query(query)
        //         .topK(topK)
        //         .filterExpression(new FilterExpressionBuilder()
        //             .eq("bvid", bvid).build())
        //         .build()
        // );
        return null;
    }
}

