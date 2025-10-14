package org.example.llm.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.llm.client.DoubaoClient;
import org.example.llm.dto.api.doubao.DoubaoApiReq;
import org.example.llm.dto.api.doubao.DoubaoApiResp;
import org.example.llm.dto.api.doubao.DoubaoMessage;
import org.example.llm.dto.llm.LlmMessage;
import org.example.llm.dto.llm.LlmResponse;
import org.example.llm.dto.tool.ToolDefinition;
import org.example.llm.service.LlmService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DoubaoLlmServiceImpl implements LlmService {

    private final DoubaoClient doubaoClient;
    private final String apiKey;
    private final Map<String, List<LlmMessage>> conversationHistory = new ConcurrentHashMap<>();

    public DoubaoLlmServiceImpl(DoubaoClient doubaoClient, @Value("${doubao.api.key}") String apiKey) {
        this.doubaoClient = doubaoClient;
        this.apiKey = "Bearer " + apiKey;
    }

    @Override
    public boolean supports(String modelName) {
        return modelName != null && (modelName.toLowerCase().startsWith("doubao") || modelName.toLowerCase().startsWith("ep-"));
    }

    @Override
    public LlmResponse chat(String sessionId, String userContent, String modelName, String persona,
                            String openingMonologue, Map<String, Object> parameters, List<ToolDefinition> tools) {
        log.warn("豆包/火山引擎的工具调用实现尚未完成，当前仅支持纯文本对话。");
        List<LlmMessage> history = conversationHistory.computeIfAbsent(sessionId, k -> new java.util.ArrayList<>());
        List<LlmMessage> messagesForApiCall = new java.util.ArrayList<>();
        if (persona != null && !persona.isEmpty()) {
            messagesForApiCall.add(LlmMessage.builder().role(LlmMessage.Role.SYSTEM).content(persona).build());
        }
        if (history.isEmpty() && openingMonologue != null && !openingMonologue.isEmpty()) {
            history.add(LlmMessage.builder().role(LlmMessage.Role.ASSISTANT).content(openingMonologue).build());
        }
        messagesForApiCall.addAll(history);
        LlmMessage userMessage = LlmMessage.builder().role(LlmMessage.Role.USER).content(userContent).build();
        messagesForApiCall.add(userMessage);

        List<DoubaoMessage> apiMessages = messagesForApiCall.stream()
                .map(msg -> DoubaoMessage.builder().role(msg.getRole()).content(msg.getContent()).build())
                .collect(Collectors.toList());

        DoubaoApiReq.DoubaoApiReqBuilder requestBuilder = DoubaoApiReq.builder()
                .model(modelName)
                .messages(apiMessages)
                .temperature((Double) parameters.get("temperature"))
                .topP((Double) parameters.get("top_p"));

        try {
            DoubaoApiResp response = doubaoClient.chatCompletions(this.apiKey, requestBuilder.build());

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new RuntimeException("API response is empty or invalid.");
            }

            String responseContent = response.getChoices().get(0).getMessage().getContent();
            history.add(userMessage);
            history.add(LlmMessage.builder().role(LlmMessage.Role.ASSISTANT).content(responseContent).build());

            return LlmResponse.builder().content(responseContent).build();

        } catch (Exception e) {
            log.error("调用豆包大模型时发生错误", e);
            history.remove(userMessage);
            throw new RuntimeException("调用大模型时发生错误", e);
        }
    }

    @Override
    public LlmResponse chatWithToolResult(String sessionId, String modelName, Map<String, Object> parameters,
                                          List<ToolDefinition> tools, LlmMessage toolCallMessages, LlmMessage toolResultMessages) {
        log.warn("chatWithToolResult 方法尚未在 DoubaoLlmServiceImpl 中完全实现");
        return LlmResponse.builder().content("工具调用后的逻辑尚未实现。").build();
    }

    @Override
    public List<LlmMessage> getConversationHistory(String sessionId) {
        return conversationHistory.getOrDefault(sessionId, Collections.emptyList());
    }

    @Override
    public List<LlmMessage> popConversationHistory(String sessionId) {
        return conversationHistory.remove(sessionId);
    }
}