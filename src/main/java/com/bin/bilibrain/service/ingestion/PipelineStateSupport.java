package com.bin.bilibrain.service.ingestion;

import com.bin.bilibrain.model.vo.catalog.VideoPipelineResponse;
import com.bin.bilibrain.model.vo.catalog.VideoPipelineStepResponse;
import com.bin.bilibrain.model.entity.Transcript;
import com.bin.bilibrain.model.entity.Video;
import com.bin.bilibrain.model.vo.ingestion.ProcessStepItemResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PipelineStateSupport {
    private static final String STEP_AUDIO = "audio";
    private static final String STEP_TRANSCRIPT = "transcript";
    private static final String STEP_INDEX = "index";
    private static final List<String> PIPELINE_STEPS = List.of(STEP_AUDIO, STEP_TRANSCRIPT, STEP_INDEX);
    private static final Map<String, String> STEP_LABELS = Map.of(
        STEP_AUDIO, "提取音频",
        STEP_TRANSCRIPT, "转写",
        STEP_INDEX, "建索引"
    );
    private static final Map<String, String> STATUS_LABELS = Map.of(
        "pending", "未开始",
        "running", "处理中",
        "done", "已完成",
        "failed", "失败"
    );

    private final ObjectMapper objectMapper;

    public Map<String, Map<String, Object>> defaultState() {
        Map<String, Map<String, Object>> state = new LinkedHashMap<>();
        state.put(STEP_AUDIO, defaultStep());
        state.put(STEP_TRANSCRIPT, defaultTranscriptStep());
        state.put(STEP_INDEX, defaultIndexStep());
        return state;
    }

    public Map<String, Map<String, Object>> readState(String stateJson) {
        Map<String, Map<String, Object>> state = defaultState();
        if (stateJson == null || stateJson.isBlank()) {
            return state;
        }
        try {
            Map<String, Map<String, Object>> raw = objectMapper.readValue(
                stateJson,
                new TypeReference<>() {
                }
            );
            for (String step : PIPELINE_STEPS) {
                Map<String, Object> existing = raw.get(step);
                if (existing == null) {
                    continue;
                }
                state.get(step).putAll(existing);
            }
            return state;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取 pipeline 状态失败", exception);
        }
    }

    public String writeState(Map<String, Map<String, Object>> state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("写入 pipeline 状态失败", exception);
        }
    }

    public void markAudioRunning(Map<String, Map<String, Object>> state) {
        markAudioRunning(state, "");
    }

    public void markAudioRunning(Map<String, Map<String, Object>> state, String substageLabel) {
        updateStep(state, STEP_AUDIO, "running", Map.of("substage_label", safeString(substageLabel)));
    }

    public void markAudioShellCompleted(Map<String, Map<String, Object>> state) {
        markAudioDone(state, "shell://audio", "");
    }

    public void markAudioFailed(Map<String, Map<String, Object>> state, String error) {
        updateStep(state, STEP_AUDIO, "failed", Map.of("error", safeString(error)));
    }

    public void markAudioDone(Map<String, Map<String, Object>> state, String path, String substageLabel) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("path", safeString(path));
        updates.put("substage_label", safeString(substageLabel));
        updateStep(state, STEP_AUDIO, "done", updates);
    }

    public void markTranscriptRunning(
        Map<String, Map<String, Object>> state,
        String sourceModel,
        String substageLabel
    ) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("source_model", safeString(sourceModel));
        updates.put("segment_count", 0);
        updates.put("substage_label", safeString(substageLabel));
        updateStep(state, STEP_TRANSCRIPT, "running", updates);
    }

    public void markTranscriptDone(Map<String, Map<String, Object>> state, String sourceModel, int segmentCount) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("source_model", safeString(sourceModel));
        updates.put("segment_count", Math.max(segmentCount, 0));
        updates.put("substage_label", "");
        updateStep(state, STEP_TRANSCRIPT, "done", updates);
    }

    public void markTranscriptPending(Map<String, Map<String, Object>> state) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("source_model", "");
        updates.put("segment_count", 0);
        updates.put("substage_label", "");
        updateStep(state, STEP_TRANSCRIPT, "pending", updates);
    }

    public void markTranscriptFailed(Map<String, Map<String, Object>> state, String error) {
        updateStep(state, STEP_TRANSCRIPT, "failed", Map.of("error", safeString(error)));
    }

    public void markIndexRunning(Map<String, Map<String, Object>> state, String substageLabel) {
        updateStep(state, STEP_INDEX, "running", Map.of("substage_label", safeString(substageLabel)));
    }

    public void markIndexDone(Map<String, Map<String, Object>> state, int chunkCount, String substageLabel) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("count", Math.max(chunkCount, 0));
        updates.put("substage_label", safeString(substageLabel));
        updateStep(state, STEP_INDEX, "done", updates);
    }

    public void markIndexPending(Map<String, Map<String, Object>> state, String substageLabel) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("count", 0);
        updates.put("substage_label", safeString(substageLabel));
        updateStep(state, STEP_INDEX, "pending", updates);
    }

    public void markIndexFailed(Map<String, Map<String, Object>> state, String error) {
        updateStep(state, STEP_INDEX, "failed", Map.of("error", safeString(error)));
    }

    public void markCurrentRunningStepFailed(Map<String, Map<String, Object>> state, String error) {
        for (String step : PIPELINE_STEPS) {
            if ("running".equals(state.get(step).get("status"))) {
                updateStep(state, step, "failed", Map.of("error", safeString(error)));
                return;
            }
        }
    }

    public void hydrateAudioStep(Video video, Map<String, Map<String, Object>> state) {
        if (video == null || isBlank(video.getAudioStorageProvider()) || isBlank(video.getAudioObjectKey())) {
            return;
        }
        Map<String, Object> audio = state.get(STEP_AUDIO);
        if ("pending".equals(audio.get("status"))) {
            updateStep(state, STEP_AUDIO, "done", Map.of("path", safeString(video.getAudioObjectKey())));
        }
    }

    public void hydrateTranscriptStep(Transcript transcript, Map<String, Map<String, Object>> state) {
        if (transcript == null || isBlank(transcript.getTranscriptText())) {
            return;
        }
        Map<String, Object> transcriptStep = state.get(STEP_TRANSCRIPT);
        if ("pending".equals(transcriptStep.get("status"))) {
            markTranscriptDone(
                state,
                transcript.getSourceModel(),
                transcript.getSegmentCount() == null ? 0 : transcript.getSegmentCount()
            );
        }
    }

    public String overallStatus(Map<String, Map<String, Object>> state) {
        boolean anyRunning = PIPELINE_STEPS.stream().anyMatch(step -> "running".equals(state.get(step).get("status")));
        if (anyRunning) {
            return "processing";
        }
        boolean anyFailed = PIPELINE_STEPS.stream().anyMatch(step -> "failed".equals(state.get(step).get("status")));
        if (anyFailed) {
            return "failed";
        }
        boolean allDone = PIPELINE_STEPS.stream().allMatch(step -> "done".equals(state.get(step).get("status")));
        if (allDone) {
            return "indexed";
        }
        boolean anyDone = PIPELINE_STEPS.stream().anyMatch(step -> "done".equals(state.get(step).get("status")));
        if (anyDone) {
            return "partial";
        }
        return "pending";
    }

    public String overallStatusLabel(String overallStatus) {
        return switch (safeString(overallStatus)) {
            case "processing" -> "正在处理中";
            case "failed" -> "处理失败";
            case "indexed" -> "已转写入库";
            case "partial" -> "已完成部分步骤";
            default -> "还没有开始处理";
        };
    }

    public String actionLabel(String effectiveOverallStatus, String queueStatus) {
        return switch (safeString(queueStatus)) {
            case "queued" -> "排队中";
            case "running" -> "处理中";
            default -> switch (safeString(effectiveOverallStatus)) {
                case "indexed" -> "已转写入库";
                case "failed" -> "重试处理";
                case "partial" -> "继续处理";
                case "processing" -> "处理中";
                default -> "开始处理";
            };
        };
    }

    public String pipelineErrorMessage(Map<String, Map<String, Object>> state) {
        for (String step : PIPELINE_STEPS) {
            String error = safeString(state.get(step).get("error"));
            if (!error.isBlank()) {
                return error;
            }
        }
        return "";
    }

    public List<ProcessStepItemResponse> stepItems(Map<String, Map<String, Object>> state) {
        return PIPELINE_STEPS.stream()
            .map(step -> {
                Map<String, Object> item = state.get(step);
                String status = safeString(item.get("status"));
                return new ProcessStepItemResponse(
                    step,
                    STEP_LABELS.get(step),
                    status,
                    STATUS_LABELS.getOrDefault(status, status),
                    safeString(item.get("updated_at")),
                    safeString(item.get("error")),
                    safeString(item.get("substage_label")),
                    intValue(item.get("count")),
                    intValue(item.get("segment_count"))
                );
            })
            .toList();
    }

    public VideoPipelineResponse toPipelineResponse(Map<String, Map<String, Object>> state) {
        return new VideoPipelineResponse(
            toPipelineStep(state.get(STEP_AUDIO)),
            toPipelineStep(state.get(STEP_TRANSCRIPT)),
            toPipelineStep(state.get(STEP_INDEX))
        );
    }

    public int chunkCount(Map<String, Map<String, Object>> state) {
        return intValue(state.get(STEP_INDEX).get("count"));
    }

    public String transcriptSource(Map<String, Map<String, Object>> state) {
        return safeString(state.get(STEP_TRANSCRIPT).get("source_model"));
    }

    public int transcriptSegmentCount(Map<String, Map<String, Object>> state) {
        return intValue(state.get(STEP_TRANSCRIPT).get("segment_count"));
    }

    public String transcriptUpdatedAt(Map<String, Map<String, Object>> state) {
        return safeString(state.get(STEP_TRANSCRIPT).get("updated_at"));
    }

    private void updateStep(Map<String, Map<String, Object>> state, String step, String status, Map<String, Object> updates) {
        Map<String, Object> stepState = state.get(step);
        stepState.put("status", status);
        stepState.put("updated_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (!"failed".equals(status)) {
            stepState.put("error", "");
        }
        updates.forEach(stepState::put);
    }

    private Map<String, Object> defaultStep() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("status", "pending");
        step.put("updated_at", "");
        step.put("error", "");
        step.put("path", "");
        step.put("substage_label", "");
        return step;
    }

    private Map<String, Object> defaultTranscriptStep() {
        Map<String, Object> step = defaultStep();
        step.put("source_model", "");
        step.put("segment_count", 0);
        return step;
    }

    private Map<String, Object> defaultIndexStep() {
        Map<String, Object> step = defaultStep();
        step.put("substage_label", "");
        step.put("count", 0);
        return step;
    }

    private VideoPipelineStepResponse toPipelineStep(Map<String, Object> step) {
        return new VideoPipelineStepResponse(
            safeString(step.get("status")),
            safeString(step.get("updated_at")),
            safeString(step.get("error")),
            safeString(step.get("substage_label")),
            intValue(step.get("count")),
            intValue(step.get("segment_count"))
        );
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}


