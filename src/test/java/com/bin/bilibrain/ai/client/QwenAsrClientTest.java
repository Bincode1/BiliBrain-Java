package com.bin.bilibrain.ai.client;

import com.bin.bilibrain.config.AppProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class QwenAsrClientTest {

    @TempDir
    Path tempDir;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void transcribePostsBase64AudioAndParsesTranscript() throws Exception {
        AtomicReference<String> requestBodyRef = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "这是转写结果。"
                      }
                    }
                  ]
                }
                """);
        });
        server.start();

        Path audioPath = tempDir.resolve("chunk-000.mp3");
        Files.write(audioPath, "fake-audio".getBytes(StandardCharsets.UTF_8));

        AppProperties appProperties = new AppProperties();
        appProperties.getProcessing().setAsrApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        appProperties.getProcessing().setAsrApiModel("qwen3-asr-flash");
        appProperties.getProcessing().setAsrEnableItn(false);
        Environment environment = new MockEnvironment().withProperty("spring.ai.dashscope.api-key", "test-key");

        QwenAsrClient client = createClient(appProperties, environment);
        String transcript = client.transcribe(audioPath);

        assertThat(transcript).isEqualTo("这是转写结果。");
        assertThat(requestBodyRef.get()).contains("\"model\":\"qwen3-asr-flash\"");
        assertThat(requestBodyRef.get()).contains("\"input_audio\":{\"data\":\"data:audio/mpeg;base64,");
        assertThat(requestBodyRef.get()).contains("\"asr_options\":{\"language\":\"zh\",\"enable_itn\":false}");
    }

    @Test
    void transcribeRetriesRetryableServerErrors() throws Exception {
        AtomicInteger requestCount = new AtomicInteger(0);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            int current = requestCount.incrementAndGet();
            if (current == 1) {
                writeRaw(exchange, 500, "{\"message\":\"temporary failure\"}");
                return;
            }
            writeJson(exchange, """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "重试后成功。"
                      }
                    }
                  ]
                }
                """);
        });
        server.start();

        Path audioPath = tempDir.resolve("chunk-retry.mp3");
        Files.write(audioPath, "retry-audio".getBytes(StandardCharsets.UTF_8));

        AppProperties appProperties = new AppProperties();
        appProperties.getProcessing().setAsrApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        appProperties.getProcessing().setAsrApiRetries(1);
        appProperties.getProcessing().setAsrApiRetryBackoffMillis(10);
        Environment environment = new MockEnvironment().withProperty("spring.ai.dashscope.api-key", "test-key");

        QwenAsrClient client = createClient(appProperties, environment);
        String transcript = client.transcribe(audioPath);

        assertThat(transcript).isEqualTo("重试后成功。");
        assertThat(requestCount.get()).isEqualTo(2);
    }

    private QwenAsrClient createClient(AppProperties appProperties, Environment environment) {
        return new QwenAsrClient(
            WebClient.builder()
                .baseUrl(appProperties.getProcessing().getAsrApiBaseUrl())
                .build(),
            appProperties,
            environment
        );
    }

    private void writeJson(HttpExchange exchange, String body) throws IOException {
        writeRaw(exchange, 200, body);
    }

    private void writeRaw(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
