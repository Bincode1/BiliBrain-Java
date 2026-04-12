package com.bin.bilibrain.graph.summary;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.bin.bilibrain.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PrepareSummaryWindowsNode implements NodeAction {
    private final AppProperties appProperties;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String transcriptText = SummaryState.transcriptText(state).trim();
        if (!StringUtils.hasText(transcriptText)) {
            throw new IllegalStateException("摘要图缺少可用的 transcript 文本。");
        }

        Map<String, Object> updates = new HashMap<>();
        if (transcriptText.length() <= appProperties.getSummary().getDirectMaxCharacters()) {
            updates.put(SummaryState.SUMMARY_MODE, SummaryState.MODE_DIRECT);
            updates.put(SummaryState.WINDOWS, List.of(transcriptText));
            return updates;
        }

        updates.put(SummaryState.SUMMARY_MODE, SummaryState.MODE_WINDOWED);
        updates.put(SummaryState.WINDOWS, splitWindows(transcriptText));
        return updates;
    }

    private List<String> splitWindows(String transcriptText) {
        int windowSize = appProperties.getSummary().getWindowMaxCharacters();
        int overlap = Math.min(appProperties.getSummary().getWindowOverlapCharacters(), Math.max(windowSize - 1, 0));
        List<String> windows = new ArrayList<>();
        int start = 0;
        while (start < transcriptText.length()) {
            int end = Math.min(start + windowSize, transcriptText.length());
            int preferredEnd = findPreferredEnd(transcriptText, start, end);
            String chunk = transcriptText.substring(start, preferredEnd).trim();
            if (!chunk.isEmpty()) {
                windows.add(chunk);
            }
            if (preferredEnd >= transcriptText.length()) {
                break;
            }
            start = Math.max(preferredEnd - overlap, start + 1);
        }
        return windows.isEmpty() ? List.of(transcriptText) : windows;
    }

    private int findPreferredEnd(String transcriptText, int start, int fallbackEnd) {
        int minAcceptable = start + Math.max(appProperties.getSummary().getWindowMaxCharacters() / 2, 1);
        int newline = transcriptText.lastIndexOf('\n', fallbackEnd - 1);
        if (newline >= minAcceptable) {
            return newline;
        }
        int punctuation = Math.max(
            transcriptText.lastIndexOf('。', fallbackEnd - 1),
            transcriptText.lastIndexOf('！', fallbackEnd - 1)
        );
        punctuation = Math.max(punctuation, transcriptText.lastIndexOf('？', fallbackEnd - 1));
        punctuation = Math.max(punctuation, transcriptText.lastIndexOf('.', fallbackEnd - 1));
        if (punctuation >= minAcceptable) {
            return punctuation + 1;
        }
        return fallbackEnd;
    }
}
