package org.example.llm.dto.llm;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * LlmService.chat 方法的标准返回对象。
 * 它封装了所有可能的返回类型，包括文本回复和工具调用。
 */
@Data
@Builder
public class LlmResponse {
    private String content;
    private List<LlmToolCall> toolCalls;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}