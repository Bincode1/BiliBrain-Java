package com.bin.bilibrain.service.retrieval;

import com.bin.bilibrain.config.AppProperties;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.BaseRanker;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class VectorSearchService {
    private static final int MAX_EMBEDDING_BATCH_SIZE = 10;
    private static final List<String> OUTPUT_FIELDS = List.of(
        MilvusCollectionAdminService.FIELD_BVID,
        MilvusCollectionAdminService.FIELD_FOLDER_ID,
        MilvusCollectionAdminService.FIELD_CHUNK_INDEX,
        MilvusCollectionAdminService.FIELD_START_SECONDS,
        MilvusCollectionAdminService.FIELD_END_SECONDS,
        MilvusCollectionAdminService.FIELD_VIDEO_TITLE,
        MilvusCollectionAdminService.FIELD_UP_NAME,
        MilvusCollectionAdminService.FIELD_TEXT
    );

    private final ObjectProvider<MilvusClientV2> milvusClientProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final MilvusCollectionAdminService collectionAdminService;
    private final AppProperties appProperties;

    public boolean isAvailable() {
        return appProperties.getRetrieval().isEnabled()
            && milvusClientProvider.getIfAvailable() != null
            && embeddingModelProvider.getIfAvailable() != null;
    }

    public void addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        collectionAdminService.ensureCollectionReady();
        EmbeddingModel embeddingModel = requireEmbeddingModel();
        List<String> contents = documents.stream()
            .map(Document::getText)
            .map(text -> text == null ? "" : text)
            .toList();
        List<float[]> embeddings = embedInBatches(embeddingModel, contents);
        List<JsonObject> rows = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            rows.add(toRow(documents.get(i), embeddings.get(i)));
        }
        requireMilvusClient().insert(
            InsertReq.builder()
                .collectionName(collectionAdminService.collectionName())
                .data(rows)
                .build()
        );
    }

    public void deleteByBvid(String bvid) {
        MilvusClientV2 client = milvusClientProvider.getIfAvailable();
        if (client == null || !StringUtils.hasText(bvid)) {
            return;
        }
        collectionAdminService.ensureCollectionReady();
        client.delete(
            DeleteReq.builder()
                .collectionName(collectionAdminService.collectionName())
                .filter("bvid == \"" + escape(bvid.trim()) + "\"")
                .build()
        );
    }

    public List<Document> similaritySearch(String query, int topK, Long folderId, String bvid) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        collectionAdminService.ensureCollectionReady();
        return switch (resolveSearchMode()) {
            case "dense" -> denseSearch(query.trim(), topK, folderId, bvid);
            case "hybrid" -> hybridSearch(query.trim(), topK, folderId, bvid);
            default -> denseSearch(query.trim(), topK, folderId, bvid);
        };
    }

    private List<Document> denseSearch(String query, int topK, Long folderId, String bvid) {
        float[] queryEmbedding = requireEmbeddingModel().embed(query);
        String filter = buildFilter(folderId, bvid);
        SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
            .collectionName(collectionAdminService.collectionName())
            .annsField(MilvusCollectionAdminService.FIELD_DENSE)
            .metricType(resolveDenseMetricType())
            .topK(Math.max(topK, appProperties.getRetrieval().getDenseTopK()))
            .limit(Math.max(topK, appProperties.getRetrieval().getDenseTopK()))
            .outputFields(OUTPUT_FIELDS)
            .data(List.of(new FloatVec(queryEmbedding)))
            .searchParams(Map.of("nprobe", appProperties.getRetrieval().getDenseNprobe()));
        if (StringUtils.hasText(filter)) {
            builder.filter(filter);
        }
        SearchResp response = requireMilvusClient().search(builder.build());
        double threshold = appProperties.getRetrieval().getDenseSimilarityThreshold();
        return extractDocuments(response).stream()
            .filter(hit -> hit.score() == null || hit.score() >= threshold)
            .limit(topK)
            .map(SearchHit::document)
            .toList();
    }

    private List<Document> hybridSearch(String query, int topK, Long folderId, String bvid) {
        float[] queryEmbedding = requireEmbeddingModel().embed(query);
        String filter = buildFilter(folderId, bvid);
        AnnSearchReq.AnnSearchReqBuilder<?, ?> denseBuilder = AnnSearchReq.builder()
            .vectorFieldName(MilvusCollectionAdminService.FIELD_DENSE)
            .metricType(resolveDenseMetricType())
            .topK(appProperties.getRetrieval().getDenseTopK())
            .params("{\"nprobe\":" + appProperties.getRetrieval().getDenseNprobe() + "}")
            .vectors(List.of(new FloatVec(queryEmbedding)));
        AnnSearchReq.AnnSearchReqBuilder<?, ?> sparseBuilder = AnnSearchReq.builder()
            .vectorFieldName(MilvusCollectionAdminService.FIELD_SPARSE)
            .metricType(IndexParam.MetricType.BM25)
            .topK(appProperties.getRetrieval().getSparseTopK())
            .vectors(List.of(new EmbeddedText(query)));
        if (StringUtils.hasText(filter)) {
            denseBuilder.expr(filter);
            sparseBuilder.expr(filter);
        }
        SearchResp response = requireMilvusClient().hybridSearch(
            HybridSearchReq.builder()
                .collectionName(collectionAdminService.collectionName())
                .searchRequests(List.of(
                    denseBuilder.build(),
                    sparseBuilder.build()
                ))
                .ranker(resolveRanker())
                .topK(topK)
                .outFields(OUTPUT_FIELDS)
                .build()
        );
        return extractDocuments(response).stream()
            .limit(topK)
            .map(SearchHit::document)
            .toList();
    }

    private List<SearchHit> extractDocuments(SearchResp response) {
        if (response == null || response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
            return List.of();
        }
        List<SearchResp.SearchResult> results = response.getSearchResults().getFirst();
        List<SearchHit> hits = new ArrayList<>(results.size());
        for (SearchResp.SearchResult result : results) {
            Map<String, Object> entity = result.getEntity() == null ? Map.of() : result.getEntity();
            Map<String, Object> metadata = new LinkedHashMap<>();
            putIfPresent(metadata, MilvusCollectionAdminService.FIELD_BVID, entity.get(MilvusCollectionAdminService.FIELD_BVID));
            putIfPresent(metadata, MilvusCollectionAdminService.FIELD_FOLDER_ID, entity.get(MilvusCollectionAdminService.FIELD_FOLDER_ID));
            putIfPresent(metadata, MilvusCollectionAdminService.FIELD_CHUNK_INDEX, entity.get(MilvusCollectionAdminService.FIELD_CHUNK_INDEX));
            putIfPresent(metadata, MilvusCollectionAdminService.FIELD_START_SECONDS, entity.get(MilvusCollectionAdminService.FIELD_START_SECONDS));
            putIfPresent(metadata, MilvusCollectionAdminService.FIELD_END_SECONDS, entity.get(MilvusCollectionAdminService.FIELD_END_SECONDS));
            putIfPresent(metadata, MilvusCollectionAdminService.FIELD_VIDEO_TITLE, entity.get(MilvusCollectionAdminService.FIELD_VIDEO_TITLE));
            putIfPresent(metadata, MilvusCollectionAdminService.FIELD_UP_NAME, entity.get(MilvusCollectionAdminService.FIELD_UP_NAME));
            Object id = result.getId() == null ? entity.get(MilvusCollectionAdminService.FIELD_ID) : result.getId();
            String content = String.valueOf(entity.getOrDefault(MilvusCollectionAdminService.FIELD_TEXT, ""));
            hits.add(new SearchHit(
                new Document(String.valueOf(id == null ? "" : id), content, metadata),
                result.getScore()
            ));
        }
        return hits;
    }

    private JsonObject toRow(Document document, float[] embedding) {
        JsonObject row = new JsonObject();
        row.addProperty(MilvusCollectionAdminService.FIELD_ID, document.getId());
        row.addProperty(MilvusCollectionAdminService.FIELD_TEXT, valueOrEmpty(document.getText()));
        row.addProperty(MilvusCollectionAdminService.FIELD_BVID, stringMetadata(document, MilvusCollectionAdminService.FIELD_BVID));
        addLong(row, MilvusCollectionAdminService.FIELD_FOLDER_ID, longMetadata(document, MilvusCollectionAdminService.FIELD_FOLDER_ID));
        addLong(row, MilvusCollectionAdminService.FIELD_CHUNK_INDEX, longMetadata(document, MilvusCollectionAdminService.FIELD_CHUNK_INDEX));
        addDouble(row, MilvusCollectionAdminService.FIELD_START_SECONDS, doubleMetadata(document, MilvusCollectionAdminService.FIELD_START_SECONDS));
        addDouble(row, MilvusCollectionAdminService.FIELD_END_SECONDS, doubleMetadata(document, MilvusCollectionAdminService.FIELD_END_SECONDS));
        row.addProperty(MilvusCollectionAdminService.FIELD_VIDEO_TITLE, stringMetadata(document, MilvusCollectionAdminService.FIELD_VIDEO_TITLE));
        row.addProperty(MilvusCollectionAdminService.FIELD_UP_NAME, stringMetadata(document, MilvusCollectionAdminService.FIELD_UP_NAME));
        row.add(MilvusCollectionAdminService.FIELD_DENSE, gsonArray(embedding));
        return row;
    }

    private com.google.gson.JsonArray gsonArray(float[] embedding) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray(embedding.length);
        for (float value : embedding) {
            array.add(value);
        }
        return array;
    }

    private void addLong(JsonObject row, String key, Long value) {
        if (value != null) {
            row.addProperty(key, value);
        }
    }

    private void addDouble(JsonObject row, String key, Double value) {
        if (value != null) {
            row.addProperty(key, value);
        }
    }

    private String stringMetadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private Long longMetadata(Document document, String key) {
        return toLong(document.getMetadata().get(key));
    }

    private Double doubleMetadata(Document document, String key) {
        return toDouble(document.getMetadata().get(key));
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String resolveSearchMode() {
        String mode = appProperties.getRetrieval().getSearchMode();
        return mode == null ? "dense" : mode.trim().toLowerCase(Locale.ROOT);
    }

    private BaseRanker resolveRanker() {
        AppProperties.Milvus milvus = appProperties.getRetrieval().getMilvus();
        if ("weighted".equalsIgnoreCase(milvus.getHybridRanker())) {
            return new WeightedRanker(List.of(0.7f, 0.3f));
        }
        return new RRFRanker(milvus.getHybridRrfK());
    }

    private String buildFilter(Long folderId, String bvid) {
        List<String> clauses = new ArrayList<>(2);
        if (folderId != null) {
            clauses.add(MilvusCollectionAdminService.FIELD_FOLDER_ID + " == " + folderId);
        }
        if (StringUtils.hasText(bvid)) {
            clauses.add(MilvusCollectionAdminService.FIELD_BVID + " == \"" + escape(bvid.trim()) + "\"");
        }
        return clauses.isEmpty() ? "" : String.join(" && ", clauses);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private IndexParam.MetricType resolveDenseMetricType() {
        return IndexParam.MetricType.valueOf(
            appProperties.getRetrieval().getMilvus().getDenseMetricType().toUpperCase(Locale.ROOT)
        );
    }

    private MilvusClientV2 requireMilvusClient() {
        MilvusClientV2 client = milvusClientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("向量检索未启用，请先配置 Milvus。");
        }
        return client;
    }

    private EmbeddingModel requireEmbeddingModel() {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new IllegalStateException("Embedding 模型未启用，请先配置 embedding 模型。");
        }
        return embeddingModel;
    }

    private List<float[]> embedInBatches(EmbeddingModel embeddingModel, List<String> contents) {
        if (contents.isEmpty()) {
            return List.of();
        }
        List<float[]> results = new ArrayList<>(contents.size());
        for (int start = 0; start < contents.size(); start += MAX_EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + MAX_EMBEDDING_BATCH_SIZE, contents.size());
            List<String> batch = contents.subList(start, end);
            List<float[]> batchEmbeddings = embeddingModel.embed(batch);
            if (batchEmbeddings.size() != batch.size()) {
                throw new IllegalStateException("Embedding 返回数量与输入数量不一致。");
            }
            results.addAll(batchEmbeddings);
        }
        return results;
    }

    private record SearchHit(Document document, Float score) {
    }
}
