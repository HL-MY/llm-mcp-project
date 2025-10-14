package org.example.llm.dto.api.qwen;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import org.example.llm.dto.tool.ToolDefinition;

import java.util.List;

/**
 * 用于构建发送给阿里 Dashscope API 的请求体。
 * 它现在引用了独立的 QwenMessage 类。
 */
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
    public static class Parameters {
        @JsonProperty("result_format")
        private String resultFormat;
        private Float temperature;
        @JsonProperty("top_p")
        private Float topP;
        private Long seed;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        @JsonProperty("repetition_penalty")
        private Float repetitionPenalty;
        private List<String> stop;
        @JsonProperty("enable_search")
        private Boolean enableSearch;
        @JsonProperty("enable_thinking")
        private Boolean enableThinking;

        // 【新增】用于传递工具定义
        private List<ToolDefinition> tools;
    }
}