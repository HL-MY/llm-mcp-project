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

    /**
     * 【新增】Token 使用情况统计
     */
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
}