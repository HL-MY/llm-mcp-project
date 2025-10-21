package org.example.agent.controller;

import org.example.agent.dto.ChatRequest;
import org.example.agent.dto.ChatResponse;
import org.example.agent.dto.ConfigurationRequest;
import org.example.agent.dto.UiState;
import org.example.agent.service.ChatService;
import org.example.agent.service.ChatService.ChatCompletion; // Import the inner record
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class WebController {

    private static final Logger log = LoggerFactory.getLogger(WebController.class);
    private final ChatService chatService;

    public WebController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/")
    public String index(Model model) {
        // 初始加载，显示默认预览
        UiState initialState = chatService.getCurrentUiState(); // <-- 调用无参数版本
        model.addAttribute("initialState", initialState);
        return "index";
    }

    @PostMapping("/api/chat")
    @ResponseBody
    public ResponseEntity<?> handleChat(@RequestBody ChatRequest chatRequest) {
        try {
            // processUserMessage 内部会处理动态人设，并返回实际使用的 persona
            ChatCompletion completion = chatService.processUserMessage(chatRequest.getMessage());

            // --- 修正：使用 completion 返回的 personaUsed 来获取正确的 UiState ---
            UiState updatedState = chatService.getCurrentUiState(completion.personaUsed()); // <-- 调用带参数版本


            ChatResponse response = new ChatResponse(completion.reply(), updatedState, completion.toolCallInfo());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("处理聊天请求时出错", e);
            // 在错误时也返回当前状态（默认预览）
            UiState errorState = chatService.getCurrentUiState(); // <-- 调用无参数版本
            ChatResponse errorResponse = new ChatResponse("处理您的请求时出错: " + e.getMessage(), errorState, null);
            return ResponseEntity.status(500).body(errorResponse);
        }
    }


    @PostMapping("/api/reset")
    @ResponseBody
    public ResponseEntity<UiState> resetState() {
        chatService.resetProcessesAndSaveHistory();
        // 重置后，显示默认预览
        return ResponseEntity.ok(chatService.getCurrentUiState()); // <-- 调用无参数版本
    }

    @PostMapping("/api/configure")
    @ResponseBody
    public ResponseEntity<UiState> configureWorkflow(@RequestBody ConfigurationRequest config) {
        chatService.updateWorkflow(config);
        // 配置更新后，显示默认预览
        return ResponseEntity.ok(chatService.getCurrentUiState()); // <-- 调用无参数版本
    }

    @PostMapping("/api/save-on-exit")
    public ResponseEntity<Void> saveOnExit() {
        chatService.saveHistoryOnExit();
        return ResponseEntity.ok().build();
    }
}