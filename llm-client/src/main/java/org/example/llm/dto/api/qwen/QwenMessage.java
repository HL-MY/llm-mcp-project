package org.example.llm.dto.api.qwen;

/**
 * @author hull
 * @since 2025/10/14 16:01
 */

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用于与阿里通义千问（Qwen）API 进行数据交换的独立 Message DTO。
 * 这个类从 QwenApiReq 中拆分出来，以实现更好的代码结构和复用性。
 */
@Data
@Builder
@NoArgsConstructor  // <-- Add this annotation
@AllArgsConstructor // <-- Add this annotation
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QwenMessage {
    private String role;
    private String content;

    @JsonProperty("tool_calls")
    private List<QwenApiResp.ToolCall> toolCalls;
}