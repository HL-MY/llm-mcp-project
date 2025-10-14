package org.example.llm.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.llm.client.DoubaoClient;
import org.example.llm.dto.api.doubao.DoubaoApiReq;
import org.example.llm.dto.api.doubao.DoubaoApiResp;
import org.example.llm.dto.api.doubao.DoubaoMessage;
import org.example.llm.dto.llm.LlmMessage;
import org.example.llm.dto.llm.LlmResponse;
import org.example.llm.dto.llm.LlmToolCall;
import org.example.llm.dto.tool.ToolDefinition;
import org.example.llm.service.LlmService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DoubaoLlmServiceImpl(DoubaoClient doubaoClient, @Value("${doubao.api.key:}") String apiKey) {
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

        List<LlmMessage> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
        List<LlmMessage> messagesForApiCall = buildApiMessages(history, persona, openingMonologue);

        LlmMessage userMessage = LlmMessage.builder().role(LlmMessage.Role.USER).content(userContent).build();
        messagesForApiCall.add(userMessage);

        DoubaoApiReq request = buildDoubaoRequest(modelName, parameters, messagesForApiCall, tools);

        try {
            DoubaoApiResp response = doubaoClient.chatCompletions(this.apiKey, request);
            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new RuntimeException("豆包API返回结果格式不正确。");
            }

            DoubaoMessage assistantDoubaoMessage = response.getChoices().get(0).getMessage();
            LlmMessage assistantLlmMessage = convertDoubaoMessageToLlmMessage(assistantDoubaoMessage);

            history.add(userMessage);
            history.add(assistantLlmMessage);

            return parseDoubaoResponse(assistantDoubaoMessage);

        } catch (Exception e) {
            log.error("调用豆包大模型时发生错误", e);
            throw new RuntimeException("调用大模型时发生错误", e);
        }
    }

    @Override
    public LlmResponse chatWithToolResult(String sessionId, String modelName, Map<String, Object> parameters,
                                          List<ToolDefinition> tools, LlmMessage toolCallMessage, LlmMessage toolResultMessage) {
        List<LlmMessage> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(toolResultMessage); // 将工具结果添加到历史记录

        DoubaoApiReq request = buildDoubaoRequest(modelName, parameters, history, tools);

        try {
            DoubaoApiResp response = doubaoClient.chatCompletions(this.apiKey, request);
            log.info("携带工具结果成功调用豆包模型, RequestId: {}", response.getId());
            if (response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new RuntimeException("模型API返回结果格式不正确。");
            }

            DoubaoMessage finalAssistantDoubaoMessage = response.getChoices().get(0).getMessage();
            LlmMessage finalAssistantLlmMessage = convertDoubaoMessageToLlmMessage(finalAssistantDoubaoMessage);

            history.add(finalAssistantLlmMessage);

            return parseDoubaoResponse(finalAssistantDoubaoMessage);

        } catch (Exception e) {
            log.error("携带工具结果调用豆包模型失败", e);
            throw new RuntimeException("携带工具结果调用大模型时发生错误", e);
        }
    }

    private List<LlmMessage> buildApiMessages(List<LlmMessage> history, String persona, String openingMonologue) {
        List<LlmMessage> messagesForApiCall = new ArrayList<>();
        if (persona != null && !persona.isEmpty()) {
            messagesForApiCall.add(LlmMessage.builder().role(LlmMessage.Role.SYSTEM).content(persona).build());
        }
        if (history.isEmpty() && openingMonologue != null && !openingMonologue.isEmpty()) {
            history.add(LlmMessage.builder().role(LlmMessage.Role.ASSISTANT).content(openingMonologue).build());
        }
        messagesForApiCall.addAll(history);
        return messagesForApiCall;
    }

    private DoubaoApiReq buildDoubaoRequest(String modelName, Map<String, Object> parameters, List<LlmMessage> messages, List<ToolDefinition> tools) {
        List<DoubaoMessage> apiMessages = messages.stream()
                .map(this::convertLlmMessageToDoubaoMessage)
                .collect(Collectors.toList());

        DoubaoApiReq.DoubaoApiReqBuilder requestBuilder = DoubaoApiReq.builder()
                .model(modelName)
                .messages(apiMessages)
                .temperature((Double) parameters.get("temperature"))
                .topP((Double) parameters.get("top_p"));

        // 豆包/火山引擎的 tool 调用参数
        if (!CollectionUtils.isEmpty(tools)) {
            requestBuilder.tools(tools);
        }

        return requestBuilder.build();
    }

    private LlmResponse parseDoubaoResponse(DoubaoMessage doubaoMessage) {
        List<LlmToolCall> llmToolCalls = null;
        if (!CollectionUtils.isEmpty(doubaoMessage.getToolCalls())) {
            llmToolCalls = doubaoMessage.getToolCalls().stream().map(doubaoToolCall ->
                    LlmToolCall.builder()
                            .id(doubaoToolCall.getId())
                            .toolName(doubaoToolCall.getFunction().getName())
                            .arguments(doubaoToolCall.getFunction().getArguments())
                            .build()
            ).collect(Collectors.toList());
        }
        return LlmResponse.builder()
                .content(doubaoMessage.getContent())
                .toolCalls(llmToolCalls)
                .build();
    }

    private LlmMessage convertDoubaoMessageToLlmMessage(DoubaoMessage doubaoMessage) {
        String content = doubaoMessage.getContent();
        if (!CollectionUtils.isEmpty(doubaoMessage.getToolCalls())) {
            try {
                // 将 tool_calls 对象序列化为 JSON 字符串存入 content
                content = objectMapper.writeValueAsString(doubaoMessage.getToolCalls());
            } catch (JsonProcessingException e) {
                log.error("序列化豆包 tool_calls 失败", e);
            }
        }
        return LlmMessage.builder()
                .role(doubaoMessage.getRole())
                .content(content)
                .build();
    }

    private DoubaoMessage convertLlmMessageToDoubaoMessage(LlmMessage llmMessage) {
        // 当我们自己的 LlmMessage 是 assistant 角色且内容是 tool_calls 的 JSON 字符串时，
        // 需要反序列化并构建回 DoubaoMessage 的 tool_calls 结构
        if (LlmMessage.Role.ASSISTANT.equals(llmMessage.getRole()) && llmMessage.getContent() != null && llmMessage.getContent().contains("function")) {
            try {
                List<DoubaoApiResp.ToolCall> toolCalls = objectMapper.readValue(llmMessage.getContent(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                return DoubaoMessage.builder().role(llmMessage.getRole()).toolCalls(toolCalls).build();
            } catch (JsonProcessingException e) {
                log.warn("反序列化为豆包 tool_calls 失败，将作为纯文本处理: {}", e.getMessage());
            }
        }
        // 当消息是工具执行结果时
        if (LlmMessage.Role.TOOL.equals(llmMessage.getRole())) {
            // 注意：豆包的 tool role 消息结构可能需要 tool_call_id，这里需要根据其官方文档调整
            // 暂时简化处理，作为 content 发送
            return DoubaoMessage.builder().role(llmMessage.getRole()).content(llmMessage.getContent()).build();
        }
        return DoubaoMessage.builder().role(llmMessage.getRole()).content(llmMessage.getContent()).build();
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