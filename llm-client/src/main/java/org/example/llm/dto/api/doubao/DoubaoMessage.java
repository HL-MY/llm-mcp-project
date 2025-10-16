package org.example.llm.dto.api.doubao;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor  // <-- Add this annotation
@AllArgsConstructor // <-- Add this annotation
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DoubaoMessage {

    private String role;
    private String content;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    // 【新增】用于发送和接收工具调用信息
    @JsonProperty("tool_calls")
    private List<DoubaoApiResp.ToolCall> toolCalls;
}