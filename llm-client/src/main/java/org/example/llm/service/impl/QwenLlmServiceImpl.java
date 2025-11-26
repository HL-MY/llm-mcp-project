package org.example.llm.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class QwenLlmServiceImpl implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(QwenLlmServiceImpl.class);
    private final Map<String, List<LlmMessage>> conversationHistory = new ConcurrentHashMap<>();
    private final QianwenClient qianwenClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WebClient webClient;

    @Value("${alibaba.api.key}")
    private String apiKey;

    private static final String STREAM_DELIMITER = "[SEP]";
    private static final String STREAM_END_SENTINEL = "__END_OF_STREAM__";

    // 【修改】构造函数注入 WebClient.Builder
    public QwenLlmServiceImpl(QianwenClient qianwenClient, WebClient.Builder webClientBuilder) {
        this.qianwenClient = qianwenClient;
        // 构建 WebClient 实例
        this.webClient = webClientBuilder.build();
    }

    @Override
    public boolean supports(String modelName) {
        return modelName != null && modelName.toLowerCase().startsWith("qwen");
    }

    // ... [chat 方法保持不变] ...
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
        if (modelName == null) return false;
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
        return LlmMessage.builder().role(qwenMessage.getRole()).content(content).build();
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

    @Override
    public void addMessagesToHistory(String sessionId, LlmMessage userMessage, LlmMessage assistantMessage) {
        List<LlmMessage> history = conversationHistory.computeIfAbsent(sessionId, k -> new java.util.ArrayList<>());
        if (userMessage != null) history.add(userMessage);
        if (assistantMessage != null) history.add(assistantMessage);
    }

    /**
     * 【重构】实现真实的流式调用
     */
    @Override
    public void chatStream(String sessionId, String userContent, String modelName, String persona,
                           String openingMonologue, Map<String, Object> parameters, List<ToolDefinition> tools,
                           Consumer<String> sender, boolean isToolCallResultStream, LlmMessage toolResultMessage,
                           Consumer<List<LlmMessage>> finalPersister) {

        List<LlmMessage> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
        List<LlmMessage> messagesForApiCall = isToolCallResultStream ? new ArrayList<>(history) : buildApiMessages(history, persona, openingMonologue);

        if (isToolCallResultStream) {
            messagesForApiCall.add(toolResultMessage);
        } else {
            messagesForApiCall.add(LlmMessage.builder().role(LlmMessage.Role.USER).content(userContent).build());
        }

        QwenApiReq request = buildQwenRequest(modelName, parameters, messagesForApiCall, tools);
        StringBuilder sentenceBuffer = new StringBuilder();
        StringBuilder fullLlmResponse = new StringBuilder();

        AtomicReference<String> errorBuffer = new AtomicReference<>("");
        AtomicInteger tokenCount = new AtomicInteger(0);

        try {
            ObjectNode requestJson = objectMapper.valueToTree(request);
            if (requestJson.has("parameters")) {
                ((ObjectNode) requestJson.get("parameters")).put("incremental_output", true);
            }

            String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
            log.info(">>> 开始流式请求 Qwen: {}", url);
            long startTime = System.currentTimeMillis();

            Iterable<String> qwenTokenStream = webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-DashScope-SSE", "enable")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(chunk -> {
                        if (tokenCount.get() == 0 && !chunk.trim().startsWith("data:") && !chunk.trim().startsWith("{")) {
                            errorBuffer.accumulateAndGet(chunk, (acc, val) -> acc + val);
                        }
                    })
                    .flatMap(chunk -> Flux.fromArray(chunk.split("\\r?\\n")))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(line -> {
                        String jsonStr = line;
                        if (line.startsWith("data:")) {
                            jsonStr = line.substring(5).trim();
                        }
                        if ("[DONE]".equals(jsonStr)) return "";

                        if (jsonStr.startsWith("{")) {
                            try {
                                MappingIterator<JsonNode> it = objectMapper.readerFor(JsonNode.class).readValues(jsonStr);
                                StringBuilder combinedContent = new StringBuilder();
                                while (it.hasNext()) {
                                    JsonNode root = it.next();
                                    if (root.has("output") && root.get("output").has("choices")) {
                                        JsonNode choices = root.get("output").get("choices");
                                        if (choices.size() > 0 && choices.get(0).has("message")) {
                                            tokenCount.incrementAndGet();
                                            combinedContent.append(choices.get(0).get("message").path("content").asText(""));
                                        }
                                    } else if (root.has("code") && root.has("message")) {
                                        log.error("Qwen API 返回错误: {}", root.toPrettyString());
                                    }
                                }
                                return combinedContent.toString();
                            } catch (Exception e) { }
                        }
                        return "";
                    })
                    .filter(text -> !text.isEmpty())
                    .toIterable();

            for (String token : qwenTokenStream) {
                if (token == null) continue;

                sentenceBuffer.append(token);

                int sepIndex;
                while ((sepIndex = sentenceBuffer.indexOf(STREAM_DELIMITER)) != -1) {
                    String completeSentence = sentenceBuffer.substring(0, sepIndex).trim();
                    sentenceBuffer.delete(0, sepIndex + STREAM_DELIMITER.length());

                    if (!completeSentence.isEmpty()) {
                        sender.accept(completeSentence);
                        fullLlmResponse.append(completeSentence); // 不加 SEP
                        log.info(">>> [{}ms] 流式输出句子: {}", (System.currentTimeMillis() - startTime), completeSentence);
                    }
                }
            }

            log.info("<<< [{}ms] 流式请求处理完成，共接收 {} 个Token片段。", (System.currentTimeMillis() - startTime), tokenCount.get());

            if (tokenCount.get() == 0) {
                String rawError = errorBuffer.get();
                if (rawError != null && !rawError.isEmpty()) {
                    log.error("API调用严重错误: {}", rawError);
                    sender.accept("{\"error\": \"API调用错误\", \"details\": " + objectMapper.writeValueAsString(rawError) + "}");
                    return;
                }
            }

            if (sentenceBuffer.length() > 0) {
                String remaining = sentenceBuffer.toString().trim();
                if (!remaining.isEmpty()) {
                    sender.accept(remaining);
                    fullLlmResponse.append(remaining);
                    log.info(">>> [{}ms] 流式输出剩余: {}", (System.currentTimeMillis() - startTime), remaining);
                }
            }

            // 【关键修复】保存历史逻辑
            String finalResponseContent = fullLlmResponse.toString();
            if (tokenCount.get() > 0) {
                if (isToolCallResultStream) {
                    // 1. 如果是工具调用的第二步，User消息早已在第一步(Router)时加入历史了。
                    //    此时需要补上 Tool Result 消息，和 Assistant 最终回复。
                    history.add(toolResultMessage);
                } else {
                    // 2. 如果是普通对话，正常添加 User 消息。
                    history.add(LlmMessage.builder().role(LlmMessage.Role.USER).content(userContent).build());
                }
                // 3. 添加 Assistant 最终回复
                history.add(LlmMessage.builder().role(LlmMessage.Role.ASSISTANT).content(finalResponseContent).build());

                finalPersister.accept(history);
            }

            sender.accept(STREAM_END_SENTINEL);

        } catch (Exception e) {
            log.error("Qwen LLM 流式调用异常", e);
            sender.accept("{\"error\": \"LLM 流式调用失败\", \"details\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }
}