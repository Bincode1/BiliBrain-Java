package com.bin.bilibrain.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    private Bilibili bilibili = new Bilibili();

    @Valid
    private Processing processing = new Processing();

    @Valid
    private Storage storage = new Storage();

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    }

    @Data
    public static class Bilibili {
        private Long uid = 0L;
        private String sessdata = "";
        private String biliJct = "";
        private String dedeUserId = "";
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
}
