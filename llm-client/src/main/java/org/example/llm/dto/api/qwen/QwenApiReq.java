package org.example.llm.dto.api.qwen;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import org.example.llm.dto.tool.ToolDefinition;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QwenApiReq {
    private String model;
    private Input input;
    private Parameters parameters;

    @Data
    @Builder
    public static class Input {
        private List<QwenMessage> messages;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Parameters {
        @JsonProperty("result_format")
        private String resultFormat;
        private Float temperature;
        @JsonProperty("top_p")
        private Float topP;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        @JsonProperty("repetition_penalty")
        private Float repetitionPenalty;
        @JsonProperty("enable_thinking")
        private Boolean enableThinking;

        private List<ToolDefinition> tools;
    }
}