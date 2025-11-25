package org.example.agent.controller;

import org.example.agent.dto.ChatRequest;
import org.example.agent.dto.ChatResponse;
import org.example.agent.dto.DirectChatResponse;
import org.example.agent.dto.UiState;
import org.example.agent.service.ChatService;
import org.example.agent.service.ChatService.ChatCompletion;
import org.example.agent.service.DirectLlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 【重构】
 * 1. 这个 Controller 只负责会话 (Chat) 和页面加载 (index)。
 * 2. 所有的配置保存功能 ( /api/configure ) 已被删除，
 * 转移到新的 ConfigAdminController。
 */
@Controller
public class WebController {
    private static final Logger log = LoggerFactory.getLogger(WebController.class);
    private final ChatService chatService;
    private final DirectLlmService directLlmService;
    public WebController(ChatService chatService, DirectLlmService directLlmService) {
        this.chatService = chatService;
        this.directLlmService = directLlmService;
    }

    @GetMapping("/")
    public String index(Model model) {
        // 【修改】获取初始的会话状态 (流程, 开场白, 预览人设)
        // 真正的配置将在 index.html 的 javascript 中通过 /api/config/* 异步加载
        UiState initialState = chatService.getInitialUiState();
        model.addAttribute("initialState", initialState);
        return "index";
    }

    @PostMapping("/api/chat")
    @ResponseBody
    public ResponseEntity<?> handleChat(@RequestBody ChatRequest chatRequest) {
        try {
            ChatCompletion completion = chatService.processUserMessage(chatRequest.getMessage());

            // 【修改】只获取会话相关的UI状态 (左侧栏)
            UiState updatedState = chatService.getCurrentUiState(completion.personaUsed());

            ChatResponse response = new ChatResponse(
                    completion.reply(),
                    updatedState,
                    completion.toolCallInfo(),
                    completion.decisionProcessInfo()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("处理聊天请求时出错", e);
            UiState errorState = chatService.getCurrentUiState("错误");
            ChatResponse errorResponse = new ChatResponse(
                    "处理您的请求时出错: " + e.getMessage(),
                    errorState,
                    null,
                    null
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }


    @PostMapping("/api/reset")
    @ResponseBody
    public ResponseEntity<UiState> resetState() {
        chatService.resetProcessesAndSaveHistory();
        // 【修改】重置后，只返回会话状态
        return ResponseEntity.ok(chatService.getInitialUiState());
    }

    // 【删除】/api/configure 接口

    @PostMapping("/api/save-on-exit")
    public ResponseEntity<Void> saveOnExit() {
        chatService.saveHistoryOnExit();
        return ResponseEntity.ok().build();
    }


    @PostMapping("/api/directChat")
    @ResponseBody
    public ResponseEntity<DirectChatResponse> handleDirectChat(
            // 【保留】HTTP POST 接口
            @RequestBody ChatRequest chatRequest) {

        String sessionId = chatRequest.getSessionId(); // 从 DTO 获取 sessionId
        String userMessage = chatRequest.getMessage(); // 从 DTO 获取 userMessage

        if (userMessage == null || userMessage.trim().isEmpty()) {
            // 如果消息为空，返回错误 DTO，携带传入的 sessionId
            return ResponseEntity.badRequest().body(new DirectChatResponse("输入消息不能为空", sessionId));
        }

        // **【恢复/保留】如果客户端未提供 SessionId，则生成一个新的 UUID**
        // 这样客户端可以使用返回的 ID 在下次调用中实现上下文
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

//         **【恢复】调用服务逻辑**
        String llmReply = directLlmService.getLlmReply(sessionId, userMessage);

//         **【恢复】错误处理逻辑**
        if (llmReply.contains("{\"error\":")) {
            // 如果服务返回了错误 JSON 字符串，将其作为 reply 字段内容返回，状态码 500
            DirectChatResponse errorResponse = new DirectChatResponse(llmReply, sessionId);
            return ResponseEntity.status(500).body(errorResponse);
        }



        // 成功返回
        DirectChatResponse successResponse = new DirectChatResponse(llmReply, sessionId);
        return ResponseEntity.ok(successResponse);
    }
}