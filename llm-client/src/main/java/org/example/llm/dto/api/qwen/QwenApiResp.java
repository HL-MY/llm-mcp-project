package org.example.llm.dto.api.qwen;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class QwenApiResp {
    private Output output;
    private Usage usage;
    @JsonProperty("request_id")
    private String requestId;

    @Data
    public static class Output {
        private String text;
        @JsonProperty("finish_reason")
        private String finishReason;
        private List<Choice> choices;
    }

    @Data
    public static class Choice {
        private QwenMessage message;
    }

    @Data
    public static class Usage {
        @JsonProperty("output_tokens")
        private int outputTokens;
        @JsonProperty("input_tokens")
        private int inputTokens;
        @JsonProperty("total_tokens")
        private int totalTokens;
    }

    // 【新增】用于接收工具调用响应
    @Data
    public static class ToolCall {
        private String id;
        private String type;
        private ToolCallFunction function;
    }

    @Data
    public static class ToolCallFunction {
        private String name;
        private String arguments;
    }
}