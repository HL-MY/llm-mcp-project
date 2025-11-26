package org.example.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PreProcessingResult {

    private String emotion;
    private String intent;

    @JsonProperty("is_sensitive")
    private String isSensitive;

    // --- 【新增】直通车字段 ---
    @JsonProperty("tool_name")
    private String toolName; // 如果小模型觉得要调工具，直接填名字

    @JsonProperty("tool_args")
    private String toolArgs; // 工具参数 JSON 字符串

    public boolean isSensitive() {
        return "true".equalsIgnoreCase(isSensitive);
    }

    // 【新增】判断是否触发了快速工具调用
    public boolean hasDirectToolCall() {
        return toolName != null && !toolName.isEmpty() && !"null".equalsIgnoreCase(toolName);
    }
}