package com.bin.bilibrain.service.retrieval;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.model.vo.chat.ChatSourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseSearchService {
    private final VectorSearchService vectorSearchService;
    private final AppProperties appProperties;

    public List<ChatSourceVO> searchKnowledgeBase(String query, Long folderId, String bvid) {
        if (!StringUtils.hasText(query) || !vectorSearchService.isAvailable()) {
            return List.of();
        }
        return vectorSearchService.similaritySearch(
                query.trim(),
                appProperties.getRetrieval().getSearchTopK(),
                folderId,
                bvid
            ).stream()
            .map(this::toSource)
            .toList();
    }

    private ChatSourceVO toSource(Document document) {
        return new ChatSourceVO(
            "chunk",
            stringMetadata(document, "bvid"),
            longMetadata(document, "folder_id"),
            stringMetadata(document, "video_title"),
            stringMetadata(document, "up_name"),
            doubleMetadata(document, "start_seconds"),
            doubleMetadata(document, "end_seconds"),
            excerpt(document.getText())
        );
    }

    private String stringMetadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private Long longMetadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
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

    private Double doubleMetadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
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

    private String excerpt(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 220 ? normalized : normalized.substring(0, 220);
    }
}
