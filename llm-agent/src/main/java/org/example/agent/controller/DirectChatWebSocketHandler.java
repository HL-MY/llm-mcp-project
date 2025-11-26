package org.example.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agent.dto.ChatRequest;
import org.example.agent.dto.DirectChatResponse;
import org.example.agent.service.DirectLlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor; // 【新增导入】

import java.io.IOException;
import java.util.function.Consumer; // 【新增导入】

@Component
public class DirectChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DirectChatWebSocketHandler.class);
    private final DirectLlmService directLlmService;
    private final ObjectMapper objectMapper;
    private final TaskExecutor llmTaskExecutor;
    final String STREAM_END_SENTINEL = "__END_OF_STREAM__";

    // 注入 llmTaskExecutor (确保 ExecutorConfig.java 已创建)
    public DirectChatWebSocketHandler(DirectLlmService directLlmService,
                                      ObjectMapper objectMapper,
                                      @Qualifier("llmTaskExecutor") TaskExecutor llmTaskExecutor) {
        this.directLlmService = directLlmService;
        this.objectMapper = objectMapper;
        this.llmTaskExecutor = llmTaskExecutor;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ChatRequest chatRequest = null;
        String sessionId = null;
        final String payload = message.getPayload();

        try {
            // 1. 同步解析请求，快速释放当前线程
            chatRequest = objectMapper.readValue(payload, ChatRequest.class);
            sessionId = chatRequest.getSessionId();
            String userMessage = chatRequest.getMessage();

            if (userMessage == null || userMessage.trim().isEmpty()) {
                DirectChatResponse errorDto = new DirectChatResponse("输入消息不能为空", sessionId);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorDto)));
                return;
            }

            // 2. 准备异步执行和流式发送
            final String finalSessionId = (sessionId == null || sessionId.trim().isEmpty()) ?
                    java.util.UUID.randomUUID().toString() : sessionId;
            final String finalUserMessage = userMessage;

            // Sender function: 接收并发送流式文本块 (句子)
            Consumer<String> sender = (chunk) -> {
                try {
                    DirectChatResponse chunkResponse;

                    if (chunk.equals(STREAM_END_SENTINEL)) {
                        // 【关键拦截】收到流结束信标，发送 CLEAN END DTO
                        chunkResponse = new DirectChatResponse(null, finalSessionId, "END");
                    } else if (chunk.equals("关闭")) {
                        // 收到业务退出信号
                        chunkResponse = new DirectChatResponse("退出", null, null); // 退出时 streamStatus 也是 END
                    } else if (chunk.startsWith("{\"error\":")) {
                        // 错误 JSON
                        chunkResponse = new DirectChatResponse(chunk, finalSessionId, "ERROR");
                    } else {
                        // 正常句子块
                        chunkResponse = new DirectChatResponse(chunk, finalSessionId, null);
                    }

                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(chunkResponse)));
                    }

                } catch (IOException e) {
                    log.error("发送流式消息失败", e);
                }
            };


            // 3. 将 LLM 任务提交到线程池 (关键的异步执行)
            llmTaskExecutor.execute(() -> {
                try {
                    // 4. 调用流式服务
                    directLlmService.getLlmReplyStream(finalSessionId, finalUserMessage, sender);

                } catch (Exception e) {
                    log.error("LLM流式任务执行失败", e);
                    // 异步任务失败时，通过 sender 发送错误信息
                    sender.accept("{\"error\": \"大模型流式处理失败，请重试。\", \"sessionId\": \"" + finalSessionId + "\"}");
                }
            });


        } catch (Exception e) {
            log.error("WebSocket消息解析失败", e);
            // 同步解析错误，快速返回
            try {
                DirectChatResponse errorDto = new DirectChatResponse("请求 JSON 格式错误。", sessionId);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorDto)));
            } catch (IOException ignored) {}
        }
    }
}