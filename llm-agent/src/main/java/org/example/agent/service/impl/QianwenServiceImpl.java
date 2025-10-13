package org.example.agent.service.impl;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.tools.ToolBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QianwenServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(QianwenServiceImpl.class);
    private final Map<String, List<Message>> conversationHistory = new ConcurrentHashMap<>();

    @Value("${llm.alibaba.api_key}")
    private String apiKey;

    public GenerationResult chat(String sessionId, String userContent, String modelName, String persona,
                                 String openingMonologue, Map<String, Object> parameters, List<ToolBase> tools) {

        List<Message> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
        List<Message> messagesForApiCall = new ArrayList<>();

        if (persona != null && !persona.isEmpty()) {
            messagesForApiCall.add(Message.builder().role(Role.SYSTEM.getValue()).content(persona).build());
        }

        if (history.isEmpty() && openingMonologue != null && !openingMonologue.isEmpty()) {
            Message monologueMessage = Message.builder().role(Role.ASSISTANT.getValue()).content(openingMonologue).build();
            history.add(monologueMessage);
        }

        messagesForApiCall.addAll(history);

        Message userMessage = Message.builder().role(Role.USER.getValue()).content(userContent).build();
        messagesForApiCall.add(userMessage);

        handleSpecialModelParameters(modelName, parameters);

        try {
            GenerationParam.GenerationParamBuilder<?, ?> paramBuilder = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .messages(messagesForApiCall)
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .parameters(parameters);

            if (tools != null && !tools.isEmpty()) {
                paramBuilder.tools(tools);
            }

            GenerationResult result = new Generation().call(paramBuilder.build());
            log.info("调用阿里大模型成功, RequestId: {}", result.getRequestId());

            history.add(userMessage);
            Message assistantMessage = result.getOutput().getChoices().get(0).getMessage();
            if (assistantMessage != null) {
                history.add(assistantMessage);
            }

            return result;

        } catch (NoApiKeyException | InputRequiredException e) {
            log.error("调用阿里大模型失败：API Key 或输入参数不正确。", e);
            throw new RuntimeException("模型服务配置异常", e);
        } catch (Exception e) {
            log.error("调用阿里大模型时发生未知错误, 会话ID: {}", sessionId, e);
            throw new RuntimeException("调用大模型时发生未知错误", e);
        }
    }

    public GenerationResult callWithToolResult(String sessionId, String modelName, Map<String, Object> parameters,
                                               List<ToolBase> tools, Message toolCallMessage, Message toolResultMessage) {

        List<Message> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(toolResultMessage);

        List<Message> messagesForApiCall = new ArrayList<>(history);
        handleSpecialModelParameters(modelName, parameters);

        try {
            GenerationParam param = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .messages(messagesForApiCall)
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .parameters(parameters)
                    .tools(tools)
                    .build();

            GenerationResult result = new Generation().call(param);
            log.info("携带工具结果调用大模型成功, RequestId: {}", result.getRequestId());

            Message finalAssistantMessage = result.getOutput().getChoices().get(0).getMessage();
            if (finalAssistantMessage != null) {
                history.add(finalAssistantMessage);
            }

            return result;
        } catch (Exception e) {
            log.error("携带工具结果调用大模型时发生未知错误, 会话ID: {}", sessionId, e);
            throw new RuntimeException("携带工具结果调用大模型时发生未知错误", e);
        }
    }

    private void handleSpecialModelParameters(String modelName, Map<String, Object> parameters) {
        if ("qwen3-30b-a3b".equals(modelName)
                || "qwen3-235b-a22b".equals(modelName)
                || "qwen3-32b".equals(modelName)
                || "qwen3-14b".equals(modelName)
                || "qwen3-8b".equals(modelName)
                || "qwen3-4b".equals(modelName)
                || "qwen3-1.7b".equals(modelName)
                || "qwen3-0.6b".equals(modelName)
        ) {
            parameters.put("enable_thinking", false);
            log.info("检测到特殊模型 '{}'，已添加 'enable_thinking: false' 参数。", modelName);
        } else {
            log.info("检测到标准模型 '{}'，使用标准参数。", modelName);
        }
    }

    @Deprecated
    public void addAssistantMessageToHistory(String sessionId, String content) {
        // This method is no longer used.
    }

    public List<Message> getConversationHistory(String sessionId) {
        return conversationHistory.getOrDefault(sessionId, Collections.emptyList());
    }

    public List<Message> popConversationHistory(String sessionId) {
        return (sessionId != null) ? conversationHistory.remove(sessionId) : Collections.emptyList();
    }
}