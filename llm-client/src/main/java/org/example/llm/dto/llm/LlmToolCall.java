package org.example.llm.dto.llm;

import lombok.Builder;
import lombok.Data;

/**
 * 内部统一的、与厂商无关的工具调用模型。
 */
@Data
@Builder
public class LlmToolCall {
    private String id;
    private String toolName;
    private String arguments;
}