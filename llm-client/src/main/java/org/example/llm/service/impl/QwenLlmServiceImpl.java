package org.example.llm.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.llm.client.QianwenClient;
import org.example.llm.dto.api.qwen.QwenApiReq;
import org.example.llm.dto.api.qwen.QwenApiResp;
import org.example.llm.dto.api.qwen.QwenMessage;
import org.example.llm.dto.llm.LlmMessage;
import org.example.llm.dto.llm.LlmResponse;
import org.example.llm.dto.llm.LlmToolCall;
import org.example.llm.dto.tool.ToolDefinition;
import org.example.llm.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class QwenLlmServiceImpl implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(QwenLlmServiceImpl.class);
    private final Map<String, List<LlmMessage>> conversationHistory = new ConcurrentHashMap<>();
    private final QianwenClient qianwenClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${alibaba.api.key}")
    private String apiKey;



    public QwenLlmServiceImpl(QianwenClient qianwenClient) {
        this.qianwenClient = qianwenClient;
    }

    @Override
    public boolean supports(String modelName) {
        return modelName != null && modelName.toLowerCase().startsWith("qwen");
    }

    @Override
    public LlmResponse chat(String sessionId, String userContent, String modelName, String persona,
                            String openingMonologue, Map<String, Object> parameters, List<ToolDefinition> tools) {

        List<LlmMessage> history = conversationHistory.computeIfAbsent(sessionId, k -> new java.util.ArrayList<>());
        List<LlmMessage> messagesForApiCall = buildApiMessages(history, persona, openingMonologue);

        LlmMessage userMessage = LlmMessage.builder().role(LlmMessage.Role.USER).content(userContent).build();
        messagesForApiCall.add(userMessage);

        QwenApiReq request = buildQwenRequest(modelName, parameters, messagesForApiCall, tools);

        try {
            log.info("【发送给大模型的完整请求】\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request));
            QwenApiResp response = qianwenClient.chatCompletions("Bearer " + apiKey, request);
            log.info("【通义千问 API 完整原始响应】\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
            log.info("成功调用通义千问模型, RequestId: {}", response.getRequestId());

            if (response.getOutput() == null || response.getOutput().getChoices() == null || response.getOutput().getChoices().isEmpty()) {
                throw new RuntimeException("模型API返回结果格式不正确。");
            }

            QwenMessage assistantQwenMessage = response.getOutput().getChoices().get(0).getMessage();
            LlmMessage assistantLlmMessage = convertQwenMessageToLlmMessage(assistantQwenMessage);

            history.add(userMessage);
            history.add(assistantLlmMessage);

            return parseQwenResponse(assistantQwenMessage);

        } catch (Exception e) {
            log.error("调用通义千问模型失败", e);
            throw new RuntimeException("调用大模型时发生错误", e);
        }
    }

    @Override
    public LlmResponse chatWithToolResult(String sessionId, String modelName, Map<String, Object> parameters,
                                          List<ToolDefinition> tools, LlmMessage toolResultMessage) {
        List<LlmMessage> history = conversationHistory.computeIfAbsent(sessionId, k -> new java.util.ArrayList<>());
        history.add(toolResultMessage);

        QwenApiReq request = buildQwenRequest(modelName, parameters, history, tools);

        try {
            QwenApiResp response = qianwenClient.chatCompletions("Bearer " + apiKey, request);
            log.info("【通义千问工具调用后 API 完整原始响应】\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
            log.info("携带工具结果成功调用通义千问模型, RequestId: {}", response.getRequestId());
            if (response.getOutput() == null || response.getOutput().getChoices() == null || response.getOutput().getChoices().isEmpty()) {
                throw new RuntimeException("模型API返回结果格式不正确。");
            }

            QwenMessage finalAssistantQwenMessage = response.getOutput().getChoices().get(0).getMessage();
            LlmMessage finalAssistantLlmMessage = convertQwenMessageToLlmMessage(finalAssistantQwenMessage);

            history.add(finalAssistantLlmMessage);

            return parseQwenResponse(finalAssistantQwenMessage);

        } catch (Exception e) {
            log.error("携带工具结果调用通义千问模型失败", e);
            throw new RuntimeException("携带工具结果调用大模型时发生错误", e);
        }
    }

    private List<LlmMessage> buildApiMessages(List<LlmMessage> history, String persona, String openingMonologue) {
        List<LlmMessage> messagesForApiCall = new java.util.ArrayList<>();
        if (persona != null && !persona.isEmpty()) {
            messagesForApiCall.add(LlmMessage.builder().role(LlmMessage.Role.SYSTEM).content(persona).build());
        }
        if (history.isEmpty() && openingMonologue != null && !openingMonologue.isEmpty()) {
            history.add(LlmMessage.builder().role(LlmMessage.Role.ASSISTANT).content(openingMonologue).build());
        }
        messagesForApiCall.addAll(history);
        return messagesForApiCall;
    }

    private QwenApiReq buildQwenRequest(String modelName, Map<String, Object> parameters, List<LlmMessage> messages, List<ToolDefinition> tools) {
        List<QwenMessage> qwenMessages = messages.stream()
                .map(this::convertLlmMessageToQwenMessage)
                .collect(Collectors.toList());

        QwenApiReq.Parameters.ParametersBuilder qwenParamsBuilder = QwenApiReq.Parameters.builder()
                .resultFormat("message")
                .temperature(((Double) parameters.getOrDefault("temperature", 0.7)).floatValue())
                .topP(((Double) parameters.getOrDefault("top_p", 0.8)).floatValue());

        if (parameters.containsKey("max_tokens")) {
            qwenParamsBuilder.maxTokens((Integer) parameters.get("max_tokens"));
        }
        if (parameters.containsKey("repetition_penalty")) {
            qwenParamsBuilder.repetitionPenalty(((Double) parameters.get("repetition_penalty")).floatValue());
        }

        if (isSpecialModel(modelName)) {
            qwenParamsBuilder.enableThinking(false);
            log.info("检测到特定模型 '{}'，已添加 'enable_thinking: false' 参数。", modelName);
        } else {
            log.info("检测到标准模型 '{}'，不添加 'enable_thinking' 参数。", modelName);
        }

        if (!CollectionUtils.isEmpty(tools)) {
            qwenParamsBuilder.tools(tools);
        }

        return QwenApiReq.builder()
                .model(modelName)
                .input(QwenApiReq.Input.builder().messages(qwenMessages).build())
                .parameters(qwenParamsBuilder.build())
                .build();
    }

    private boolean isSpecialModel(String modelName) {
        if (modelName == null) {
            return false;
        }
        // 【修改】更新为新的Qwen模型列表中常见的非指令/非优化模型
        // 这些模型可能不支持 'result_format: message' 或需要 'enable_thinking: false'
        return Set.of(
                "qwen3-0.6b", "qwen3-1.7b", "qwen3-8b", "qwen3-14b",
                "qwen3-30b-a3b", "qwen3-32b", "qwen3-235b-a22b",
                "qwen1.5-0.5b-chat", "qwen1.5-1.8b-chat", "qwen1.5-7b-chat",
                "qwen1.5-14b-chat", "qwen1.5-72b-chat"
        ).contains(modelName);
    }

    private LlmResponse parseQwenResponse(QwenMessage qwenMessage) {
        List<LlmToolCall> llmToolCalls = null;
        if (!CollectionUtils.isEmpty(qwenMessage.getToolCalls())) {
            llmToolCalls = qwenMessage.getToolCalls().stream().map(qwenToolCall ->
                    LlmToolCall.builder()
                            .id(qwenToolCall.getId())
                            .toolName(qwenToolCall.getFunction().getName())
                            .arguments(qwenToolCall.getFunction().getArguments())
                            .build()
            ).collect(Collectors.toList());
        }
        return LlmResponse.builder()
                .content(qwenMessage.getContent())
                .toolCalls(llmToolCalls)
                .build();
    }

    private LlmMessage convertQwenMessageToLlmMessage(QwenMessage qwenMessage) {
        String content = qwenMessage.getContent();
        if (!CollectionUtils.isEmpty(qwenMessage.getToolCalls())) {
            try {
                content = objectMapper.writeValueAsString(qwenMessage.getToolCalls());
            } catch (JsonProcessingException e) {
                log.error("序列化通义千问 tool_calls 失败", e);
            }
        }
        return LlmMessage.builder()
                .role(qwenMessage.getRole())
                .content(content)
                .build();
    }

    private QwenMessage convertLlmMessageToQwenMessage(LlmMessage llmMessage) {
        if (LlmMessage.Role.ASSISTANT.equals(llmMessage.getRole()) && llmMessage.getContent().contains("function")) {
            try {
                List<QwenApiResp.ToolCall> toolCalls = objectMapper.readValue(llmMessage.getContent(), new TypeReference<>() {});
                return QwenMessage.builder().role(llmMessage.getRole()).toolCalls(toolCalls).build();
            } catch (JsonProcessingException e) {
                log.error("反序列化通义千问 tool_calls 失败", e);
            }
        }
        if (LlmMessage.Role.TOOL.equals(llmMessage.getRole())) {
            return QwenMessage.builder().role(llmMessage.getRole()).content(llmMessage.getContent()).build();
        }
        return QwenMessage.builder().role(llmMessage.getRole()).content(llmMessage.getContent()).build();
    }

    @Override
    public List<LlmMessage> getConversationHistory(String sessionId) {
        return conversationHistory.getOrDefault(sessionId, Collections.emptyList());
    }

    @Override
    public List<LlmMessage> popConversationHistory(String sessionId) {
        return conversationHistory.remove(sessionId);
    }

    // 【新增】
    @Override
    public void addMessagesToHistory(String sessionId, LlmMessage userMessage, LlmMessage assistantMessage) {
        // 使用 computeIfAbsent 确保 history 列表存在且可变
        List<LlmMessage> history = conversationHistory.computeIfAbsent(sessionId, k -> new java.util.ArrayList<>());
        if (userMessage != null) {
            history.add(userMessage);
        }
        if (assistantMessage != null) {
            history.add(assistantMessage);
        }
    }
}