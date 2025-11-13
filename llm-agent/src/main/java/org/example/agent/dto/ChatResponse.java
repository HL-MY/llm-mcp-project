package org.example.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 【修改】
 * 1. 添加 DecisionProcessInfo 字段。
 * 2. 更新构造函数以接收新字段。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {
    private final String reply;
    private final UiState uiState;
    private final ToolCallInfo toolCall;
    private final DecisionProcessInfo decisionProcess; // <-- 【新增】

    // 【修改】主构造函数
    public ChatResponse(String reply, UiState uiState, ToolCallInfo toolCall, DecisionProcessInfo decisionProcess) {
        this.reply = reply;
        this.uiState = uiState;
        this.toolCall = toolCall;
        this.decisionProcess = decisionProcess;
    }

    // 兼容的构造函数
    public ChatResponse(String reply, UiState uiState) {
        this(reply, uiState, null, null);
    }

    public String getReply() {
        return reply;
    }

    public UiState getUiState() {
        return uiState;
    }

    public ToolCallInfo getToolCall() {
        return toolCall;
    }

    public DecisionProcessInfo getDecisionProcess() { // <-- 【新增】
        return decisionProcess;
    }
}