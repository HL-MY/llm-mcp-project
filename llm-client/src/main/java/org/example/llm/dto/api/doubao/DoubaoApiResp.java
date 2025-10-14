package org.example.llm.dto.api.doubao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DoubaoApiResp {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    @Data
    public static class Choice {
        private int index;
        private DoubaoMessage message;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;
        @JsonProperty("completion_tokens")
        private int completionTokens;
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

    // 【新增】用于接收工具调用响应
    @Data
    public static class ToolCallFunction {
        private String name;
        private String arguments;
    }
}