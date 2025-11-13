package org.example.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于承载 "预处理模型" (小模型) 分析结果的 DTO。
 * 包含情绪、意图和敏感词检测结果。
 */
@Data
@NoArgsConstructor
public class PreProcessingResult {

    private String emotion;
    private String intent;

    @JsonProperty("is_sensitive")
    private String isSensitive; // 使用 String 兼容 "true" / "false"

    // 用于代码中设置默认值或回退
    public PreProcessingResult(String emotion, String intent, String isSensitive) {
        this.emotion = emotion;
        this.intent = intent;
        this.isSensitive = isSensitive;
    }

    public boolean isSensitive() {
        return "true".equalsIgnoreCase(isSensitive);
    }
}