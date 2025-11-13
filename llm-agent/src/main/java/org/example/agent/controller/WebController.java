package org.example.agent.controller;

import org.example.agent.dto.ChatRequest;
import org.example.agent.dto.ChatResponse;
import org.example.agent.dto.UiState;
import org.example.agent.service.ChatService;
import org.example.agent.service.ChatService.ChatCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

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

    public WebController(ChatService chatService) {
        this.chatService = chatService;
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
}