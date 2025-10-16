package org.example.llm.service;

import org.example.llm.dto.llm.LlmMessage;
import org.example.llm.dto.llm.LlmResponse;
import org.example.llm.dto.tool.ToolDefinition;


import java.util.List;
import java.util.Map;

/**
 * 大模型服务的统一接口。
 * 定义了所有LLM服务实现类必须遵守的契约。
 */
public interface LlmService {
    boolean supports(String modelName);
    LlmResponse chat(String sessionId, String userContent, String modelName, String persona,
                     String openingMonologue, Map<String, Object> parameters, List<ToolDefinition> tools);
    LlmResponse chatWithToolResult(String sessionId, String modelName, Map<String, Object> parameters,
                                   List<ToolDefinition> tools, LlmMessage toolResultMessage);
    List<LlmMessage> getConversationHistory(String sessionId);
    List<LlmMessage> popConversationHistory(String sessionId);
}