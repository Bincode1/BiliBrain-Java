package com.bin.bilibrain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {
    private Bilibili bilibili = new Bilibili();
    private Dashscope dashscope = new Dashscope();
    private Ollama ollama = new Ollama();
    private Chroma chroma = new Chroma();
    private DataConfig data = new DataConfig();

    @Data
    public static class Bilibili {
        private String sessdata;
        private String biliJct;
    }

    @Data
    public static class Dashscope {
        private String apiKey;
    }

    @Data
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String embeddingModel = "bge-m3";
    }

    @Data
    public static class Chroma {
        private String baseUrl = "http://localhost:8000";
        private String collectionName = "bilibrain";
    }

    @Data
    public static class DataConfig {
        private String dir = "./data";
    }
}
