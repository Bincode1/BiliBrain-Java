package com.bin.bilibrain.config;

import jakarta.validation.Valid;
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
    }

    @Data
    public static class Summary {
        @Min(500)
        private int directMaxCharacters = 4000;

        @Min(500)
        private int windowMaxCharacters = 3000;

        @Min(0)
        private int windowOverlapCharacters = 300;

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
}
