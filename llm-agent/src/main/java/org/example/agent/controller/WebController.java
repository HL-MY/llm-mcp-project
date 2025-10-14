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
        UiState initialState = chatService.getCurrentUiState();
        model.addAttribute("initialState", initialState);
        return "index";
    }

    @PostMapping("/api/chat")
    @ResponseBody
    public ResponseEntity<?> handleChat(@RequestBody ChatRequest chatRequest) {
        try {
            ChatCompletion completion = chatService.processUserMessage(chatRequest.getMessage());
            UiState updatedState = chatService.getCurrentUiState();
            // Create the final response, now including the tool call info
            ChatResponse response = new ChatResponse(completion.reply(), updatedState, completion.toolCallInfo());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("处理聊天请求时出错", e);
            return ResponseEntity.status(500).body("处理您的请求时出错: " + e.getMessage());
        }
    }

    @PostMapping("/api/reset")
    @ResponseBody
    public ResponseEntity<UiState> resetState() {
        chatService.resetProcessesAndSaveHistory();
        return ResponseEntity.ok(chatService.getCurrentUiState());
    }

    @PostMapping("/api/configure")
    @ResponseBody
    public ResponseEntity<UiState> configureWorkflow(@RequestBody ConfigurationRequest config) {
        chatService.updateWorkflow(config);
        return ResponseEntity.ok(chatService.getCurrentUiState());
    }

    @PostMapping("/api/save-on-exit")
    public ResponseEntity<Void> saveOnExit() {
        chatService.saveHistoryOnExit();
        return ResponseEntity.ok().build();
    }
}