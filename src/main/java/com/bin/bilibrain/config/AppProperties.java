package com.bin.bilibrain.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
@Validated
@Data
public class AppProperties {
    @Valid
    private Cors cors = new Cors();

    @Valid
    private Catalog catalog = new Catalog();

    @Valid
    private Bilibili bilibili = new Bilibili();

    @Valid
    private Processing processing = new Processing();

    @Valid
    private Storage storage = new Storage();

    @Valid
    private Retrieval retrieval = new Retrieval();

    @Valid
    private Summary summary = new Summary();

    @Valid
    private Chat chat = new Chat();

    @Valid
    private Publishing publishing = new Publishing();

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:4173",
            "http://127.0.0.1:4173"
        ));
    }

    @Data
    public static class Catalog {
        @Min(1)
        private int folderListCacheTtlSeconds = 300;

        @Min(1)
        private int folderVideosCacheTtlSeconds = 300;
    }

    @Data
    public static class Bilibili {
        private Long uid = 0L;
        private String sessdata = "";
        private String biliJct = "";
        private String dedeUserId = "";
        @Min(1)
        private int sessionCacheTtlSeconds = 30;
        @Min(1)
        private int httpTimeoutSeconds = 15;
        @Min(0)
        private int httpRetries = 2;
        @Min(0)
        private long httpRetryBackoffMillis = 300;
    }

    @Data
    public static class Processing {
        @Min(1)
        @Max(300)
        private int maxVideoMinutes = 30;

        @Min(1)
        private int ingestionMaxConcurrency = 3;

        @Min(1)
        private int ingestionPollIntervalSeconds = 2;

        @Min(30)
        private int ingestionTaskStaleAfterSeconds = 1800;

        @Min(1)
        private int asrChunkConcurrency = 2;

        @Min(15)
        private int asrTargetChunkSeconds = 180;

        @Min(30)
        private int asrChunkSeconds = 240;

        @Min(0)
        private int asrChunkOverlapSeconds = 8;

        private int asrSilenceNoiseDb = -35;

        @Min(0)
        private double asrSilenceMinSeconds = 0.6;

        private List<String> asrLanguageHints = new ArrayList<>(List.of("zh"));

        @NotBlank
        private String asrApiBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

        @NotBlank
        private String asrApiModel = "qwen3-asr-flash";

        private boolean asrEnableItn = false;

        @Min(5)
        private int asrApiTimeoutSeconds = 90;
        @Min(0)
        private int asrApiRetries = 2;
        @Min(0)
        private long asrApiRetryBackoffMillis = 1000;

        @NotBlank
        private String ffmpegCommand = "ffmpeg";

        @NotBlank
        private String ffprobeCommand = "ffprobe";
    }

    @Data
    public static class Storage {
        private Path dataDir = Path.of("./data");
        private Path audioDir = Path.of("./data/audio");
        private Path vectorDbDir = Path.of("./data/vector_db");
        private Path uploadDir = Path.of("./uploads");
        private Path toolsWorkspaceRoot = Path.of("./data/tool_workspaces");
        private Path skillsRoot = Path.of("./skills");
    }

    @Data
    public static class Retrieval {
        private boolean enabled = false;

        @Min(100)
        private int chunkSize = 500;

        @Min(0)
        private int chunkOverlap = 100;

        @Min(1)
        @Max(20)
        private int searchTopK = 5;

        @Min(1)
        @Max(100)
        private int denseTopK = 20;

        @Min(1)
        @Max(100)
        private int sparseTopK = 20;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double denseSimilarityThreshold = 0.7;

        @NotBlank
        private String searchMode = "hybrid";

        @Min(1)
        private int denseNprobe = 16;

        @Min(1)
        private int embeddingDimension = 1024;

        @Valid
        private Milvus milvus = new Milvus();
    }

    @Data
    public static class Milvus {
        private String host = "localhost";
        private int port = 19530;
        private String uri = "";
        private String token = "";
        private String username = "";
        private String password = "";
        private boolean secure = false;
        private long connectTimeoutMs = 10000;
        private long keepAliveTimeMs = 55000;
        private long keepAliveTimeoutMs = 20000;
        private long idleTimeoutMs = 0;
        private long rpcDeadlineMs = 0;
        private String database = "default";
        private String collection = "bilibrain_transcript_chunks";
        private String denseIndexType = "IVF_FLAT";
        private String denseMetricType = "COSINE";
        private int denseIndexNlist = 1024;
        private String sparseIndexType = "SPARSE_INVERTED_INDEX";
        private String hybridRanker = "rrf";
        private int hybridRrfK = 60;
    }

    @Data
    public static class Summary {
        @Min(500)
        private int directMaxCharacters = 4000;

        @Min(500)
        private int windowMaxCharacters = 3000;

        @Min(0)
        private int windowOverlapCharacters = 300;

        @Min(1)
        @Max(8)
        private int windowConcurrency = 4;

        private double temperature = 0.2;
    }

    @Data
    public static class Chat {
        @Min(100)
        private int compactionTokenThreshold = 1200;

        @Min(2)
        @Max(20)
        private int recentMessageLimit = 6;

        @Min(200)
        private int memoryMaxCharacters = 4000;

        @Min(20)
        private int memoryLineMaxCharacters = 180;
    }

    @Data
    public static class Publishing {
        private Path vaultRoot = Path.of("./data/knowledge_vault");
        private String videoNotesDir = "学习笔记/视频";
        private String folderGuidesDir = "学习笔记/收藏夹";
        private String reviewPlansDir = "学习笔记/复习计划";
    }
}
