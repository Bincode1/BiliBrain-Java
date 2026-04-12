package com.bin.bilibrain.service.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentSplitter {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 100;

    public List<Document> splitTranscript(String bvid, String content) {
        // 暂时注释掉，等待Milvus依赖问题解决
        // List<Document> chunks = new ArrayList<>();
        // int start = 0;
        // int index = 0;
        // while (start < content.length()) {
        //     int end = Math.min(start + CHUNK_SIZE, content.length());
        //     String text = content.substring(start, end);

        //     Document doc = new Document(text, Map.of(
        //         "bvid", bvid,
        //         "chunk_index", String.valueOf(index)
        //     ));
        //     chunks.add(doc);

        //     start += CHUNK_SIZE - CHUNK_OVERLAP;
        //     index++;
        // }
        // return chunks;
        return null;
    }
}

