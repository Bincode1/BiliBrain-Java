package com.bin.bilibrain.service.retrieval;

import com.bin.bilibrain.config.AppProperties;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class MilvusCollectionAdminService {
    public static final String FIELD_ID = "id";
    public static final String FIELD_BVID = "bvid";
    public static final String FIELD_FOLDER_ID = "folder_id";
    public static final String FIELD_CHUNK_INDEX = "chunk_index";
    public static final String FIELD_START_SECONDS = "start_seconds";
    public static final String FIELD_END_SECONDS = "end_seconds";
    public static final String FIELD_VIDEO_TITLE = "video_title";
    public static final String FIELD_UP_NAME = "up_name";
    public static final String FIELD_TEXT = "text";
    public static final String FIELD_DENSE = "dense";
    public static final String FIELD_SPARSE = "sparse";

    private final ObjectProvider<MilvusClientV2> milvusClientProvider;
    private final AppProperties appProperties;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public void ensureCollectionReady() {
        if (ready.get() || !appProperties.getRetrieval().isEnabled()) {
            return;
        }
        synchronized (ready) {
            if (ready.get()) {
                return;
            }
            MilvusClientV2 client = requireClient();
            String collectionName = appProperties.getRetrieval().getMilvus().getCollection();
            boolean exists = Boolean.TRUE.equals(client.hasCollection(
                HasCollectionReq.builder().collectionName(collectionName).build()
            ));
            if (!exists) {
                client.createCollection(buildCreateCollectionReq());
            }
            client.loadCollection(
                LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .sync(true)
                    .build()
            );
            ready.set(true);
        }
    }

    public String collectionName() {
        return appProperties.getRetrieval().getMilvus().getCollection();
    }

    public String databaseName() {
        return appProperties.getRetrieval().getMilvus().getDatabase();
    }

    private CreateCollectionReq buildCreateCollectionReq() {
        AppProperties.Retrieval retrieval = appProperties.getRetrieval();
        AppProperties.Milvus milvus = retrieval.getMilvus();

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
            .enableDynamicField(false)
            .build()
            .addField(varcharField(FIELD_ID, true, 128, "chunk 主键"))
            .addField(varcharField(FIELD_BVID, false, 64, "视频 bvid"))
            .addField(longField(FIELD_FOLDER_ID, "收藏夹 ID"))
            .addField(longField(FIELD_CHUNK_INDEX, "chunk 序号"))
            .addField(doubleField(FIELD_START_SECONDS, "起始秒"))
            .addField(doubleField(FIELD_END_SECONDS, "结束秒"))
            .addField(varcharField(FIELD_VIDEO_TITLE, false, 512, "视频标题"))
            .addField(varcharField(FIELD_UP_NAME, false, 256, "UP 主"))
            .addField(
                AddFieldReq.builder()
                    .fieldName(FIELD_TEXT)
                    .description("chunk 原文")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .enableAnalyzer(true)
                    .enableMatch(true)
                    .build()
            )
            .addField(
                AddFieldReq.builder()
                    .fieldName(FIELD_DENSE)
                    .description("dense embedding")
                    .dataType(DataType.FloatVector)
                    .dimension(retrieval.getEmbeddingDimension())
                    .build()
            )
            .addField(
                AddFieldReq.builder()
                    .fieldName(FIELD_SPARSE)
                    .description("BM25 sparse vector")
                    .dataType(DataType.SparseFloatVector)
                    .build()
            )
            .addFunction(
                CreateCollectionReq.Function.builder()
                    .name("text_bm25")
                    .description("将原文映射到 BM25 sparse 向量")
                    .functionType(FunctionType.BM25)
                    .inputFieldNames(List.of(FIELD_TEXT))
                    .outputFieldNames(List.of(FIELD_SPARSE))
                    .build()
            );

        return CreateCollectionReq.builder()
            .collectionName(milvus.getCollection())
            .databaseName(milvus.getDatabase())
            .description("Bilibrain transcript chunk hybrid retrieval collection")
            .collectionSchema(schema)
            .indexParams(buildIndexParams())
            .build();
    }

    private List<IndexParam> buildIndexParams() {
        AppProperties.Milvus milvus = appProperties.getRetrieval().getMilvus();
        List<IndexParam> indexParams = new ArrayList<>();
        indexParams.add(IndexParam.builder()
            .fieldName(FIELD_DENSE)
            .indexName("idx_dense")
            .indexType(IndexParam.IndexType.valueOf(milvus.getDenseIndexType()))
            .metricType(IndexParam.MetricType.valueOf(milvus.getDenseMetricType()))
            .extraParams(Map.of("nlist", milvus.getDenseIndexNlist()))
            .build());
        indexParams.add(IndexParam.builder()
            .fieldName(FIELD_SPARSE)
            .indexName("idx_sparse")
            .indexType(IndexParam.IndexType.valueOf(milvus.getSparseIndexType()))
            .metricType(IndexParam.MetricType.BM25)
            .build());
        return indexParams;
    }

    private AddFieldReq varcharField(String name, boolean primaryKey, int maxLength, String description) {
        return AddFieldReq.builder()
            .fieldName(name)
            .description(description)
            .dataType(DataType.VarChar)
            .maxLength(maxLength)
            .isPrimaryKey(primaryKey)
            .autoID(false)
            .build();
    }

    private AddFieldReq longField(String name, String description) {
        return AddFieldReq.builder()
            .fieldName(name)
            .description(description)
            .dataType(DataType.Int64)
            .build();
    }

    private AddFieldReq doubleField(String name, String description) {
        return AddFieldReq.builder()
            .fieldName(name)
            .description(description)
            .dataType(DataType.Double)
            .build();
    }

    private MilvusClientV2 requireClient() {
        MilvusClientV2 client = milvusClientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Milvus 客户端未启用，请先检查检索配置。");
        }
        return client;
    }
}
