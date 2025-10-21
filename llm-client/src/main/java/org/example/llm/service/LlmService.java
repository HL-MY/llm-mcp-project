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
    /**
     * 【新增】允许 ChatService 手动将消息对添加到会话历史中，
     * 用于处理不经过LLM调用的情况（例如 "空格" 导致的沉默回复）。
     * @param sessionId 会话ID
     * @param userMessage 用户的消息 (可为null)
     * @param assistantMessage 机器人的回复 (可为null)
     */
    void addMessagesToHistory(String sessionId, LlmMessage userMessage, LlmMessage assistantMessage);
}