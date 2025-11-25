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
import org.springframework.web.socket.handler.TextWebSocketHandler; // 【关键】改为继承 MVC 的 TextWebSocketHandler

import java.io.IOException;

@Component
public class DirectChatWebSocketHandler extends TextWebSocketHandler { // 【关键修改】继承 TextWebSocketHandler

    private static final Logger log = LoggerFactory.getLogger(DirectChatWebSocketHandler.class);
    private final DirectLlmService directLlmService;
    private final ObjectMapper objectMapper;

    public DirectChatWebSocketHandler(DirectLlmService directLlmService, ObjectMapper objectMapper) {
        this.directLlmService = directLlmService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ChatRequest chatRequest = null;
        String sessionId = null;
        String responseJson;

        try {
            // 1. 解析 JSON 消息 (使用 MVC 的同步方式)
            String payload = message.getPayload(); // 获取 String 内容
            chatRequest = objectMapper.readValue(payload, ChatRequest.class);
            sessionId = chatRequest.getSessionId();
            String userMessage = chatRequest.getMessage();

            // 2. 核心校验和 sessionId 生成
            if (userMessage == null || userMessage.trim().isEmpty()) {
                DirectChatResponse errorDto = new DirectChatResponse("输入消息不能为空", sessionId);
                responseJson = objectMapper.writeValueAsString(errorDto);
                session.sendMessage(new TextMessage(responseJson));
                return;
            }

            if (sessionId == null || sessionId.trim().isEmpty()) {
                sessionId = java.util.UUID.randomUUID().toString();
            }

            // 3. 调用核心服务获取回复 (与 HTTP 接口共用此逻辑)
            String llmReply = directLlmService.getLlmReply(sessionId, userMessage);
            if (llmReply == "关闭") {
                llmReply = "退出";
                sessionId = null;
            }
            // 4. 构造响应 DTO
            DirectChatResponse responseDto = new DirectChatResponse(llmReply, sessionId);
            responseJson = objectMapper.writeValueAsString(responseDto);

            // 5. 发送回复 (使用 MVC 的同步发送)
            session.sendMessage(new TextMessage(responseJson));

        } catch (Exception e) {
            log.error("WebSocket消息处理或LLM服务调用失败", e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            String currentSessionId = (chatRequest != null) ? chatRequest.getSessionId() : null;

            try {
                DirectChatResponse errorDto = new DirectChatResponse("服务处理失败: " + errorMsg, currentSessionId);
                responseJson = objectMapper.writeValueAsString(errorDto);
            } catch (IOException ignored) {
                responseJson = "{\"reply\": \"内部服务调用失败\", \"sessionId\": null}";
            }
            session.sendMessage(new TextMessage(responseJson));
        }
    }
}