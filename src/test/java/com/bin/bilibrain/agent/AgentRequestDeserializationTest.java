package com.bin.bilibrain.agent;

import com.bin.bilibrain.model.dto.agent.AgentResumeStreamRequest;
import com.bin.bilibrain.model.dto.agent.AgentStreamRequest;
import com.bin.bilibrain.model.dto.chat.AskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRequestDeserializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void streamRequestAcceptsSnakeCaseScopeMode() throws Exception {
        AgentStreamRequest request = objectMapper.readValue("""
            {"message":"当前你可以看到什么视频","folder_id":null,"video_bvid":null,"scope_mode":"global","conversation_id":"conv-old"}
            """, AgentStreamRequest.class);

        assertThat(request.conversationId()).isEqualTo("conv-old");
        assertThat(request.folderId()).isNull();
        assertThat(request.videoBvid()).isNull();
        assertThat(request.scopeMode()).isEqualTo("global");
    }

    @Test
    void resumeRequestAcceptsSnakeCaseScopeMode() throws Exception {
        AgentResumeStreamRequest request = objectMapper.readValue("""
            {"conversation_id":"conv-old","folder_id":null,"video_bvid":null,"scope_mode":"global","feedbacks":[{"tool_id":"tool-1","decision":"APPROVE"}]}
            """, AgentResumeStreamRequest.class);

        assertThat(request.conversationId()).isEqualTo("conv-old");
        assertThat(request.scopeMode()).isEqualTo("global");
    }

    @Test
    void askRequestAcceptsSnakeCaseScopeMode() throws Exception {
        AskRequest request = objectMapper.readValue("""
            {"query":"当前你可以看到什么视频","scope_mode":"global","conversation_id":"conv-old"}
            """, AskRequest.class);

        assertThat(request.conversationId()).isEqualTo("conv-old");
        assertThat(request.scopeMode()).isEqualTo("global");
        assertThat(request.message()).isEqualTo("当前你可以看到什么视频");
    }
}
